package org.challan.challan_scraper.services;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.challan.challan_scraper.DTO.P1Data;
import org.challan.challan_scraper.DTO.P1Response;
import org.challan.challan_scraper.DTO.WebSourceContext;
import org.challan.challan_scraper.utills.MapperUtils;
import org.challan.challan_scraper.utills.OkHttpUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class P1Client {

    private static final String BASE_URL =
            "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml";
    private static final String MAIN_URL =
            "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionOnlineOdc.xhtml";
    private static final List<String> FIELD_LIST = Arrays.asList(
            "Vehicle No.", "Vehicle Type", "Chassis No.", "Owner Name",
            "Vehicle Class", "GVW (In Kg.)", "Unladen Weight(In Kg.)",
            "Load Carrying Capacity of Vehicle(In Kg.)", "Road Tax Validity",
            "Insurance Validity", "Fitness Validity", "PUCC Validity",
            "Registration Date", "Address"
    );

    public String getData(String vehicleNum) throws Exception {
        WebSourceContext ctx = new WebSourceContext(vehicleNum);

        // fetch initial cookies
        ctx.setCookie(fetchInitialCookie());

        // perform full scraping flow
        P1Response resp = scrapeVehicleDetails(ctx);

        return MapperUtils.convertObjectToString(resp);
    }

    private P1Response scrapeVehicleDetails(WebSourceContext ctx) throws Exception {
        // STEP-1: Initial GET
        String html = doGet(BASE_URL, ctx, null);
        enrichContextFromHtml(ctx, html, false);

        // STEP-2: 3 POST calls
        doPost(BASE_URL, ctx, buildBody("STEP_1", ctx, null, html), true);
        doPost(BASE_URL, ctx, buildBody("STEP_2", ctx, null, html), true);
        doPost(BASE_URL, ctx, buildBody("STEP_3", ctx, null, html), false);

        // STEP-3: GET main form page
        String formHtml = doGet(MAIN_URL, ctx, BASE_URL);
        enrichContextFromHtml(ctx, formHtml, false);

        // STEP-4: Final POST with vehicle number
        String finalHtml = doPost(MAIN_URL, ctx, buildBody("FINAL", ctx, ctx.getVehicleNum(), formHtml), true);

        // Parse details
        return parseVehicleDetails(finalHtml, ctx);
    }

    // ----------------------------------------------------------
    //  HTTP helpers
    // ----------------------------------------------------------

    private String fetchInitialCookie() throws Exception {
        Request req = new Request.Builder().url(BASE_URL).get().build();
        try (Response res = OkHttpUtils.getOkHttpClient(20000).newCall(req).execute()) {
            if (res.code() != 200) throw new Exception("Failed to get homepage cookie");
            List<String> cookies = Arrays.asList(res.headers("Set-Cookie").toArray(new String[0]));
            String jsession = cookies.get(0).split(";")[0];
            String serverId = cookies.size() > 1 ? cookies.get(1).split(";")[0] : "";
            return serverId + ";" + jsession;
        }
    }

    private String doGet(String url, WebSourceContext ctx, String referer) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (StringUtils.isNotBlank(referer)) builder.addHeader("Referer", referer);
        builder.addHeader("Cookie", ctx.getCookie());
        try (Response res = OkHttpUtils.getOkHttpClient(2000).newCall(builder.build()).execute()) {
            return res.body().string();
        }
    }

    private String doPost(String url, WebSourceContext ctx, String body, boolean ajax) throws Exception {
        MediaType mt = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
        Request.Builder req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(mt, body))
                .addHeader("Cookie", ctx.getCookie());

        if (ajax) {
            req.addHeader("Faces-Request", "partial/ajax")
                    .addHeader("X-Requested-With", "XMLHttpRequest");
        } else {
            req.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        }
        try (Response res = OkHttpUtils.getOkHttpClient(3500).newCall(req.build()).execute()) {
            String responseBody = res.body().string();
            // Update viewState if it's an XML ajax response
            String vs = extractViewState(responseBody, false);
            if (StringUtils.isNotBlank(vs)) ctx.setViewState(vs);
            return responseBody;
        }
    }

    // ----------------------------------------------------------
    //  Payload builders & parsers
    // ----------------------------------------------------------

    private String buildBody(String step, WebSourceContext ctx, String vehicleNo, String html) throws Exception {
        String form = ctx.getFormName();
        String stateField = ctx.getStateField();
        String opField = ctx.getOperationField();
        String goButton = ctx.getGoButton();
        String viewState = ctx.getViewState();

        return switch (step) {
            case "STEP_1" -> buildStateSelectionPayload(form, stateField, opField, viewState);
            case "STEP_2" -> buildOperationSelectionPayload(form, stateField, opField, viewState);
            case "STEP_3" -> buildFormSubmissionPayload(form, stateField, goButton, opField, viewState);
            case "FINAL"  -> buildVehicleSearchPayload(form, vehicleNo, viewState, html);
            default -> throw new IllegalArgumentException("Unknown step " + step);
        };
    }

    private P1Response parseVehicleDetails(String xmlResponse, WebSourceContext ctx) {
        P1Data data = new P1Data();
        data.setVehicleNum(ctx.getVehicleNum());

        try {
            Document xmlDoc = Jsoup.parse(xmlResponse, Parser.xmlParser());

            Element update = xmlDoc.select("update[id*=taxcollodc], update[id*=popup]").first();
            if (update != null) {
                Document content = Jsoup.parse(update.text());

                for (String label : FIELD_LIST) {
                    Element labelEl = content.selectFirst("span.ui-outputlabel-label:contains(" + label + ")");
                    if (labelEl == null) continue;

                    Element parent = labelEl.closest("label.field-label");
                    if (parent == null) continue;

                    Element inputEl = parent.nextElementSibling();
                    if (inputEl == null) continue;

                    String value = extractValue(inputEl);

                    switch (label) {
                        case "Vehicle Type" -> data.setVehicleType(value);
                        case "Chassis No." -> data.setChassisNo(value);
                        case "Owner Name" -> data.setOwnerName(value);
                        case "Vehicle Class" -> data.setVehicleClass(value);
                        case "GVW (In Kg.)" -> data.setGvwKg(value);
                        case "Unladen Weight(In Kg.)" -> data.setUnladenWeightKg(value);
                        case "Load Carrying Capacity of Vehicle(In Kg.)" -> data.setLoadCapacityKg(value);
                        case "Road Tax Validity" -> data.setRoadTaxValidity(value);
                        case "Insurance Validity" -> data.setInsuranceValidity(value);
                        case "Fitness Validity" -> data.setFitnessValidity(value);
                        case "PUCC Validity" -> data.setPuccValidity(value);
                        case "Registration Date" -> data.setRegistrationDate(value);
                        case "Address" -> data.setAddress(value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse vehicle details for {}", ctx.getVehicleNum(), e);
        }

        return new P1Response(200, "Success", data, "MH");


    }

    private String extractValue(Element inputEl) {
        if ("input".equals(inputEl.tagName())) {
            return inputEl.attr("value");
        }
        if (inputEl.hasClass("ui-selectonemenu")) {
            Element selected = inputEl.selectFirst("select option[selected]");
            return selected != null ? selected.text() : "";
        }
        if (inputEl.hasClass("ui-calendar")) {
            Element calInput = inputEl.selectFirst("input");
            return calInput != null ? calInput.attr("value") : "";
        }
        return inputEl.text();
    }



    // ======= VIEWSTATE EXTRACTION =======
    private String extractViewState(String content, boolean isXml) throws Exception {
        if (!isXml) {
            Document doc = Jsoup.parse(content);
            Element el = doc.selectFirst("input[name='javax.faces.ViewState'], input[id*='ViewState']");
            return el != null ? el.attr("value") : null;
        }
        Document doc = Jsoup.parse(content, "", Parser.xmlParser());
        Element update = doc.selectFirst("update[id*='ViewState']");
        if (update != null) return update.text().trim();

        Matcher matcher = Pattern.compile(
                "<update\\s+id=\"[^\"]*ViewState[^\"]*\">\\s*<!\\[CDATA\\[(.*?)]]>\\s*</update>",
                Pattern.DOTALL
        ).matcher(content);
        if (matcher.find()) return matcher.group(1).trim();

        var dbf = DocumentBuilderFactory.newInstance();
        var db = dbf.newDocumentBuilder();
        var xmlDoc = db.parse(new InputSource(new StringReader(content)));
        NodeList updates = xmlDoc.getElementsByTagName("update");
        for (int i = 0; i < updates.getLength(); i++) {
            var node = updates.item(i);
            var id = node.getAttributes().getNamedItem("id").getNodeValue();
            if (id.contains("javax.faces.ViewState")) return node.getTextContent().trim();
        }
        return null;
    }

    // ======= CONTEXT ENRICHMENT =======
    private void enrichContextFromHtml(WebSourceContext ctx, String html, boolean xml) throws Exception {
        String vs = extractViewState(html, xml);
        if (StringUtils.isNotBlank(vs)) ctx.setViewState(vs);

        Document doc = Jsoup.parse(html);
        if (StringUtils.isBlank(ctx.getFormName())) ctx.setFormName(extractFormName(doc));
        if (StringUtils.isBlank(ctx.getStateField())) ctx.setStateField(extractStateFieldName(doc));
        if (StringUtils.isBlank(ctx.getOperationField())) ctx.setOperationField(extractOperationFieldName(doc));
        if (StringUtils.isBlank(ctx.getGoButton())) ctx.setGoButton(findGoButtonId(doc));
    }

    // ======= FORM HELPERS =======
    private String findGoButtonId(Document doc) {
        for (Element button : doc.select("button")) {
            if ("Go".equalsIgnoreCase(button.text().trim())) return button.id();
        }
        return null;
    }

    private String extractFormName(Document doc) {
        Element form = doc.selectFirst("form[method='post']");
        return form != null ? form.attr("id") : "master_Layout_form";
    }

    private String extractStateFieldName(Document doc) {
        Element stateSelect = doc.selectFirst("select option[value='MH']");
        return stateSelect != null ? stateSelect.parent().attr("name") : "ib_state_input";
    }

    private String extractOperationFieldName(Document doc) {
        Element opSelect = doc.selectFirst("select option[value='5007']");
        return opSelect != null ? opSelect.parent().attr("name") : "operation_code_input";
    }

    // ======= PAYLOAD BUILDERS =======
    private String buildStateSelectionPayload(String form, String stateField,
                                              String opField, String viewState) {
        return "javax.faces.partial.ajax=true" +
                "&javax.faces.source=" + stateField.replace("_input", "") +
                "&javax.faces.partial.execute=" + stateField.replace("_input", "") +
                "&javax.faces.partial.render=" + opField.replace("_input", "") +
                "&javax.faces.behavior.event=change" +
                "&javax.faces.partial.event=change" +
                "&" + form + "=" + form +
                "&" + stateField + "=MH" +
                "&" + opField + "=-1" +
                "&javax.faces.ViewState=" + enc(viewState);
    }

    private String buildOperationSelectionPayload(String form, String stateField,
                                                  String opField, String viewState) {
        return "javax.faces.partial.ajax=true" +
                "&javax.faces.source=" + opField.replace("_input", "") +
                "&javax.faces.partial.execute=" + opField.replace("_input", "") +
                "&javax.faces.partial.render=" + opField.replace("_input", "") +
                "&javax.faces.behavior.event=change" +
                "&javax.faces.partial.event=change" +
                "&" + form + "=" + form +
                "&" + stateField + "=MH" +
                "&" + opField + "=5007" +
                "&javax.faces.ViewState=" + enc(viewState);
    }

    private String buildFormSubmissionPayload(String form, String stateField,
                                              String goButton, String opField, String viewState) {
        return form + "=" + form +
                "&" + stateField + "=MH" +
                "&" + opField + "=5007" +
                "&" + goButton + "=" + goButton +
                "&javax.faces.ViewState=" + enc(viewState);
    }

    private String buildVehicleSearchPayload(String formName, String vehicleNum,
                                             String viewState, String html) throws Exception {
        Document doc = Jsoup.parse(html);

        Element vehicleInput = doc.selectFirst("input[maxlength='10']");
        String vehicleField = vehicleInput != null ? vehicleInput.attr("name") : "j_idt43:j_idt48";

        Element detailsBtn = doc.selectFirst("button[title*='get owner and vehicle details']");
        String btnName = detailsBtn != null ? detailsBtn.attr("name") : "j_idt43:getdetails";

        StringBuilder payload = new StringBuilder();
        payload.append("javax.faces.partial.ajax=true")
                .append("&javax.faces.source=").append(btnName)
                .append("&javax.faces.partial.execute=@all")
                .append("&javax.faces.partial.render=taxcollodc popup")
                .append("&").append(btnName).append("=").append(btnName)
                .append("&").append(formName).append("=").append(formName)
                .append("&").append(vehicleField).append("=").append(vehicleNum);

        appendFormFields(doc, payload);
        payload.append("&javax.faces.ViewState=").append(enc(viewState));
        return payload.toString();
    }

    private void appendFormFields(Document doc, StringBuilder payload) {
        for (Element select : doc.select("select")) {
            String name = select.attr("name");
            if (!name.isEmpty()) {
                Element selected = select.selectFirst("option[selected]");
                String value = selected != null ? selected.attr("value") : "-1";
                payload.append("&").append(name).append("=").append(enc(value));
            }
        }
        for (Element input : doc.select("input[type='text'], input[type='hidden']")) {
            String name = input.attr("name");
            if (!name.isEmpty() && !name.contains("ViewState")) {
                payload.append("&").append(name).append("=").append(enc(input.attr("value")));
            }
        }
        for (Element checkbox : doc.select("input[type='checkbox']")) {
            String name = checkbox.attr("name");
            if (!name.isEmpty()) {
                String value = checkbox.hasAttr("checked") ? "on" : "off";
                payload.append("&").append(name).append("=").append(value);
            }
        }
    }

    private String enc(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
