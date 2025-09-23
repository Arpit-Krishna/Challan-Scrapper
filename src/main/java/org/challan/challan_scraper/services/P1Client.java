package org.challan.challan_scraper.services;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.challan.challan_scraper.DTO.P1Data;
import org.challan.challan_scraper.DTO.P1Response;
import org.challan.challan_scraper.DTO.WebSourceContext;
import org.challan.challan_scraper.constants.P1Constants;
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

import static org.challan.challan_scraper.constants.P1Constants.*;

@Slf4j
@Service
public class P1Client {

    public String getData(String vehicleNum, String stateCode) throws Exception {
        WebSourceContext ctx = new WebSourceContext(vehicleNum);

        ctx.setStateCode(stateCode);
        ctx.setOpCode(opCode.get(stateCode));
        if(stateCode.equals("MH") || stateCode.equals("CG") || stateCode.equals("GJ")) {
            ctx.setUpdateTag("taxcollodc");
            ctx.setMAIN_URL(P1_TAX_ODC_URL);
        }
        else {
            ctx.setUpdateTag(stateCode.toLowerCase() + "taxcollection");
            ctx.setMAIN_URL(P1_TAX_COL_URL);
        }

        ctx.setCookie(fetchInitialCookie());
        P1Response resp = scrapeVehicleDetails(ctx);

        return MapperUtils.convertObjectToString(resp);
    }

    private P1Response scrapeVehicleDetails(WebSourceContext ctx) throws Exception {
        // STEP-1: Initial GET
        String html = doGet(P1Constants.P1_HOMEPAGE_URL, ctx, null);
        String MAIN_URL = ctx.getMAIN_URL();
        enrichContextFromHtml(ctx, html);

        // STEP-2: 3 POST calls
        doPost(P1Constants.P1_HOMEPAGE_URL, ctx, buildBody("STEP_1", ctx, null, html));
        doPost(P1Constants.P1_HOMEPAGE_URL, ctx, buildBody("STEP_2", ctx, null, html));

        // STEP-3: GET main form page
        String formHtml = doGet(MAIN_URL, ctx, P1Constants.P1_HOMEPAGE_URL);
        enrichContextFromHtml(ctx, formHtml);

        // STEP-4: Final POST with vehicle number
        String finalHtml = doPost(MAIN_URL, ctx, buildBody("STEP_3", ctx, ctx.getVehicleNum(), formHtml));

        // Parse details
        return parseVehicleDetails(finalHtml, ctx);
    }

    // ----------------------------------------------------------
    //  HTTP helpers
    // ----------------------------------------------------------

    private String fetchInitialCookie() throws Exception {
        Request req = new Request.Builder().url(P1Constants.P1_HOMEPAGE_URL).get().build();
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

    private String doPost(String url, WebSourceContext ctx, String body) throws Exception {
        MediaType mt = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
        Request.Builder req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(mt, body))
                .addHeader("Cookie", ctx.getCookie());

        req.addHeader("Faces-Request", "partial/ajax").addHeader("X-Requested-With", "XMLHttpRequest");

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
        String goButton = ctx.getGoButton();
        String viewState = ctx.getViewState();

        return switch (step) {
            case "STEP_1" -> buildStateSelectionPayload(ctx.getStateCode(), viewState);
            case "STEP_2" -> buildOperationSelectionPayload(ctx.getStateCode(), ctx.getOpCode(), goButton, viewState);
            case "STEP_3"  -> buildVehicleSearchPayload(vehicleNo, viewState, html, ctx.getUpdateTag());
            default -> throw new IllegalArgumentException("Unknown step " + step);
        };
    }

    private P1Response parseVehicleDetails(String xmlResponse, WebSourceContext ctx) {
        P1Data data = new P1Data();
        data.setVehicleNum(ctx.getVehicleNum());

        try {
            Document xmlDoc = Jsoup.parse(xmlResponse, Parser.xmlParser());

            Element update = xmlDoc.select("update[id*=" + ctx.getUpdateTag() + "], update[id*=popup]").first();
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
                        case "Vehicle Type", "Vehicle Permit Type" -> data.setVehicleType(value);
                        case "Chassis No." -> data.setChassisNo(value);
                        case "Owner Name", "Owner/Firm Name" -> data.setOwnerName(value);
                        case "Vehicle Class" -> data.setVehicleClass(value);
                        case "GVW (In Kg.)", "Gross Vehicle Weight(In Kg.)", "Gross Vehicle Wt.(In Kg.)" -> data.setGvwKg(value);
                        case "Unladen Weight(In Kg.)" -> data.setUnladenWeightKg(value);
                        case "Load Carrying Capacity of Vehicle(In Kg.)" -> data.setLoadCapacityKg(value);
                        case "Road Tax Validity" -> data.setRoadTaxValidity(value);
                        case "Insurance Validity" -> data.setInsuranceValidity(value);
                        case "Fitness Validity" -> data.setFitnessValidity(value);
                        case "PUCC Validity" -> data.setPuccValidity(value);
                        case "Registration Date" -> data.setRegistrationDate(value);
                        case "Address" -> data.setAddress(value);
                        case "Mobile No." -> data.setMobile(value);
                        case "From State" -> data.setState(value);
                        case "Seating Cap(Ex. Driver)", "Seating Capacity (Excluding Driver)", "Seating cap " -> data.setSeatingCapacity(value);
                        case "Sale Amount" -> data.setSaleAmount(value);
                        case "Fuel"  -> data.setFuel(value);
                        case "Cubic Cap(CC)" -> data.setCC(value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse vehicle details for {}", ctx.getVehicleNum(), e);
        }

        return new P1Response(200, "Success", data, ctx.getStateCode());


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
    private void enrichContextFromHtml(WebSourceContext ctx, String html) throws Exception {
        String vs = extractViewState(html, false);
        if (StringUtils.isNotBlank(vs)) ctx.setViewState(vs);
        Document doc = Jsoup.parse(html);
        if (StringUtils.isBlank(ctx.getGoButton())) ctx.setGoButton(findGoButtonId(doc));
    }

    // ======= FORM HELPERS =======
    private String findGoButtonId(Document doc) {
        for (Element button : doc.select("button")) {
            if ("Go".equalsIgnoreCase(button.text().trim())) return button.id();
        }
        return null;
    }



    // ======= PAYLOAD BUILDERS =======
    private String buildStateSelectionPayload(String stateCode, String viewState) {
        return "javax.faces.partial.ajax=true&javax.faces.source=ib_state&javax.faces.partial.execute=ib_state&javax.faces.partial.render=operation_code&javax.faces.behavior.event=change" +
                "&javax.faces.partial.event=change&master_Layout_form=master_Layout_form&ib_state_input=" + stateCode + "&operation_code_focus=&operation_code_input=-1&javax.faces.ViewState=" + enc(viewState);
    }

    private String buildOperationSelectionPayload(String stateCode, String opCode,String goButtonId, String viewState) {
        return "javax.faces.partial.ajax=true&javax.faces.source= " + goButtonId + "&javax.faces.partial.execute=%40all" + "&" + goButtonId + "=" + goButtonId + "&PAYMENT_TYPE=ONLINE" +
                "&master_Layout_form=master_Layout_form&ib_state_focus=&ib_state_input=" +  stateCode + "&operation_code_focus=&operation_code_input=" + opCode +  "&javax.faces.ViewState=" + enc(viewState);
    }


    private String buildVehicleSearchPayload(String vehicleNum, String viewState, String html, String updateTag) throws Exception {
        Document doc = Jsoup.parse(html);

        Element vehicleInput = doc.selectFirst("input[maxlength='10']");
        String vehicleField = vehicleInput.attr("name") ;

        Element detailsBtn = doc.selectFirst("button[title*='get owner and vehicle details']");
        Element detailsBtn1 = doc.selectFirst("button.ui-button:has(span.ui-button-text:contains(Get Details))");
        if (detailsBtn == null && detailsBtn1 != null){
            detailsBtn = detailsBtn1;
        }
        String btnName = detailsBtn.attr("name");

        StringBuilder payload = new StringBuilder();
        payload.append("javax.faces.partial.ajax=true")
                .append("&javax.faces.source=").append(btnName)
                .append("&javax.faces.partial.execute=@all")
                .append("&javax.faces.partial.render=" + updateTag + " popup")
                .append("&").append(btnName).append("=").append(btnName)
                .append("&master_Layout_form=master_Layout_form")
                .append("&").append(vehicleField).append("=").append(vehicleNum);


        payload.append("&javax.faces.ViewState=").append(enc(viewState));
        return payload.toString();
    }

    private String enc(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
