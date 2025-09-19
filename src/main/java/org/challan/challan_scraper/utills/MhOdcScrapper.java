package org.challan.challan_scraper.utills;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MhOdcScrapper {

    // ======= CONSTANTS =======
    private static final String BASE_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml";
    private static final String MAIN_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionOnlineOdc.xhtml";
    private static final List<String> FIELD_LIST = Arrays.asList(
            "Vehicle No.", "Vehicle Type", "Chassis No.", "Owner Name",
            "Vehicle Class", "GVW (In Kg.)", "Unladen Weight(In Kg.)",
            "Load Carrying Capacity of Vehicle(In Kg.)", "Road Tax Validity",
            "Insurance Validity", "Fitness Validity", "PUCC Validity",
            "Registration Date", "Address"
    );

    private final HttpClient client;
    private final CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    public MhOdcScrapper() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .cookieHandler(this.cm)
                .build();
    }

    // ======= PUBLIC API =======
    public Map<String, String> fetchVehicleDetails(String vehicleNum) throws Exception {
        Map<String, String> result = new HashMap<>();
        try {
            // ---- 1: Initial page ----
            String baseHtml = sendGet(BASE_URL);
            String viewState1 = extractViewState(baseHtml, false);

            Document initialDoc = Jsoup.parse(baseHtml);
            String formName = extractFormName(initialDoc);
            String stateField = extractStateFieldName(initialDoc);
            String opField = extractOperationFieldName(initialDoc);
            String goButton = findGoButtonId(initialDoc);

            // ---- 2: Select state ----
            String payload2 = buildStateSelectionPayload(formName, stateField, opField, viewState1);
            String resp2 = sendPost(BASE_URL, payload2, true);
            String viewState2 = extractViewState(resp2, true);

            // ---- 3: Select operation ----
            String payload3 = buildOperationSelectionPayload(formName, stateField, opField, viewState2);
            String resp3 = sendPost(BASE_URL, payload3, true);
            String viewState3 = extractViewState(resp3, true);

            // ---- 4: Submit form ----
            String payload4 = buildFormSubmissionPayload(formName, stateField, goButton, opField, viewState3);
            sendPost(BASE_URL, payload4, false);

            // ---- 5: ODC page ----
            String odcHtml = sendGet(MAIN_URL);
            String viewStateOdc = extractViewState(odcHtml, false);

            // ---- 6: Vehicle search ----
            String payload6 = buildVehicleSearchPayload(vehicleNum, viewStateOdc, odcHtml);
            String resp6 = sendPost(MAIN_URL, payload6, true);

            result = parseVehicleDetailsFromResponse(resp6, vehicleNum);

        } catch (Exception e) {
            throw new Exception("Error fetching vehicle details: " + e.getMessage(), e);
        }
        return result;
    }

    // ======= HTTP HELPERS =======
    private String sendGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String sendPost(String url, String payload, boolean ajax) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", "Mozilla/5.0")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        if (ajax) {
            builder.header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest");
        } else {
            builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    // ======= VIEWSTATE EXTRACTION =======
    private String extractViewState(String content, boolean isXml) throws Exception {
        if (!isXml) {
            Document doc = Jsoup.parse(content);
            Element el = doc.selectFirst("input[name='javax.faces.ViewState'], input[id*='ViewState']");
            return el != null ? el.attr("value") : null;
        }
        // XML parsing
        Document doc = Jsoup.parse(content, "", Parser.xmlParser());
        Element update = doc.selectFirst("update[id*='ViewState']");
        if (update != null) return update.text().trim();

        Matcher matcher = Pattern.compile(
                "<update\\s+id=\"[^\"]*ViewState[^\"]*\">\\s*<!\\[CDATA\\[(.*?)]]>\\s*</update>",
                Pattern.DOTALL
        ).matcher(content);
        if (matcher.find()) return matcher.group(1).trim();

        // Fallback DOM
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

    // ======= FORM HELPERS =======
    private String findGoButtonId(Document doc) {
        for (Element button : doc.select("button"))
            if ("Go".equalsIgnoreCase(button.text().trim())) return button.id();
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

    private String buildVehicleSearchPayload(String vehicleNum, String viewState, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        String formName = extractFormName(doc);

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

    // ======= RESPONSE PARSER =======
    private Map<String, String> parseVehicleDetailsFromResponse(String xmlResponse, String vehicleNum) {
        Map<String, String> result = new HashMap<>();
        try {
            Document xmlDoc = Jsoup.parse(xmlResponse, Parser.xmlParser());
            for (Element update : xmlDoc.select("update")) {
                String id = update.attr("id");
                if (id.contains("taxcollodc") || id.contains("popup")) {
                    Document content = Jsoup.parse(update.text());
                    for (String label : FIELD_LIST) {
                        Element labelEl = content.selectFirst("span.ui-outputlabel-label:contains(" + label + ")");
                        if (labelEl != null) {
                            Element parent = labelEl.closest("label.field-label");
                            if (parent != null) {
                                Element inputEl = parent.nextElementSibling();
                                if (inputEl != null) {
                                    String value = "";
                                    if (inputEl.tagName().equals("input")) {
                                        value = inputEl.attr("value");
                                    } else if (inputEl.hasClass("ui-selectonemenu")) {
                                        Element selected = inputEl.selectFirst("select option[selected]");
                                        if (selected != null) value = selected.text();
                                    } else if (inputEl.hasClass("ui-calendar")) {
                                        Element calInput = inputEl.selectFirst("input");
                                        if (calInput != null) value = calInput.attr("value");
                                    }
                                    result.put(label, value);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            result.put("Vehicle No.", vehicleNum);
            result.put("Error", "Failed to parse response: " + e.getMessage());
        }
        if (result.isEmpty()) {
            result.put("Vehicle No.", vehicleNum);
            result.put("Status", "No details found or vehicle not in database");
        }
        return result;
    }
}
