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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MhOdcScrapper {

    private static final String BASE_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml";
    private static final String MAIN_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionOnlineOdc.xhtml";

    private static final List<String> FIELD_LIST = Arrays.asList(
            "Vehicle No.", "Vehicle Type",
            "Chassis No.", "Owner Name",
            "Vehicle Class", "GVW (In Kg.)",
            "Unladen Weight(In Kg.)",
            "Load Carrying Capacity of Vehicle(In Kg.)",
            "Road Tax Validity", "Insurance Validity",
            "Fitness Validity", "PUCC Validity",
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

    private String extractViewState(String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Element el = doc.selectFirst("input[name='javax.faces.ViewState']");
        if (el == null) el = doc.selectFirst("input[id*='ViewState']");
        return el != null ? el.attr("value") : null;
    }

    private String extractViewStateFromXml(String xml) {
        try {
            Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
            Element update = doc.selectFirst("update[id*='ViewState']");
            if (update != null) return update.text().trim();

            Matcher matcher = Pattern.compile(
                    "<update\\s+id=\"[^\"]*ViewState[^\"]*\">\\s*<!\\[CDATA\\[(.*?)]]>\\s*</update>",
                    Pattern.DOTALL
            ).matcher(xml);
            if (matcher.find()) return matcher.group(1).trim();

            var dbf = DocumentBuilderFactory.newInstance();
            var db = dbf.newDocumentBuilder();
            var xmlDoc = db.parse(new InputSource(new StringReader(xml)));
            NodeList updates = xmlDoc.getElementsByTagName("update");
            for (int i = 0; i < updates.getLength(); i++) {
                var node = updates.item(i);
                var id = node.getAttributes().getNamedItem("id").getNodeValue();
                if (id.contains("javax.faces.ViewState")) return node.getTextContent().trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Map<String, String> fetchVehicleDetails(String vehicleNum) throws Exception {
        Map<String, String> result;
        try {
            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
            String baseHtml = response1.body();
            String viewstate1 = extractViewState(baseHtml);
            if (viewstate1 == null) throw new Exception("Could not extract initial ViewState");

            Document initialDoc = Jsoup.parse(baseHtml);
            String formName = extractFormName(initialDoc);
            String stateFieldName = extractStateFieldName(initialDoc);
            String operationFieldName = extractOperationFieldName(initialDoc);
            String goButton = findGoButtonId(initialDoc);

            String statePayload = buildStateSelectionPayload(formName, stateFieldName, operationFieldName, viewstate1);
            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .POST(HttpRequest.BodyPublishers.ofString(statePayload))
                    .build();
            HttpResponse<String> response2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
            String viewstate2 = extractViewStateFromXml(response2.body());
            if (viewstate2 == null) throw new Exception("Could not extract ViewState after state selection");

            String opPayload = buildOperationSelectionPayload(formName, stateFieldName, operationFieldName, viewstate2);
            HttpRequest req3 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .POST(HttpRequest.BodyPublishers.ofString(opPayload))
                    .build();
            HttpResponse<String> response3 = client.send(req3, HttpResponse.BodyHandlers.ofString());
            String viewstate3 = extractViewStateFromXml(response3.body());
            if (viewstate3 == null) throw new Exception("Could not extract ViewState after operation selection");

            String submitPayload = buildFormSubmissionPayload(formName, stateFieldName, goButton, operationFieldName, viewstate3);
            HttpRequest req4 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .POST(HttpRequest.BodyPublishers.ofString(submitPayload))
                    .build();
            client.send(req4, HttpResponse.BodyHandlers.ofString());

            HttpRequest req5 = HttpRequest.newBuilder()
                    .uri(URI.create(MAIN_URL))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response5 = client.send(req5, HttpResponse.BodyHandlers.ofString());
            String odcHtml = response5.body();
            String viewstateOdc = extractViewState(odcHtml);
            if (viewstateOdc == null) throw new Exception("Could not extract ODC form ViewState");

            String vehiclePayload = buildVehicleSearchPayload(vehicleNum, viewstateOdc, odcHtml);
            HttpRequest req6 = HttpRequest.newBuilder()
                    .uri(URI.create(MAIN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .POST(HttpRequest.BodyPublishers.ofString(vehiclePayload))
                    .build();
            HttpResponse<String> response6 = client.send(req6, HttpResponse.BodyHandlers.ofString());
            result = parseVehicleDetailsFromResponse(response6.body(), vehicleNum);

        } catch (Exception e) {
            throw new Exception("Error fetching vehicle details: " + e.getMessage(), e);
        }
        return result;
    }

    private String buildFormSubmissionPayload(String formName, String stateFieldName, String goButton,
                                              String operationFieldName, String viewstate) {
        return formName + "=" + formName +
                "&" + stateFieldName + "=MH" +
                "&" + operationFieldName + "=5007" +
                "&" + goButton + "=" + goButton +
                "&javax.faces.ViewState=" + java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String findGoButtonId(Document doc) {
        for (Element button : doc.select("button"))
            if (button.text().trim().equalsIgnoreCase("Go")) return button.id();
        return null;
    }

    private String extractFormName(Document doc) {
        Element form = doc.selectFirst("form[method='post']");
        return form != null ? form.attr("id") : "master_Layout_form";
    }

    private String extractStateFieldName(Document doc) {
        Element stateSelect = doc.selectFirst("select option[value='MH']");
        if (stateSelect != null) return stateSelect.parent().attr("name");
        return "ib_state_input";
    }

    private String extractOperationFieldName(Document doc) {
        Element operationSelect = doc.selectFirst("select option[value='5007']");
        if (operationSelect != null) return operationSelect.parent().attr("name");
        return "operation_code_input";
    }

    private String buildStateSelectionPayload(String formName, String stateFieldName,
                                              String operationFieldName, String viewstate) {
        return "javax.faces.partial.ajax=true" +
                "&javax.faces.source=" + stateFieldName.replace("_input", "") +
                "&javax.faces.partial.execute=" + stateFieldName.replace("_input", "") +
                "&javax.faces.partial.render=" + operationFieldName.replace("_input", "") +
                "&javax.faces.behavior.event=change" +
                "&javax.faces.partial.event=change" +
                "&" + formName + "=" + formName +
                "&" + stateFieldName + "=MH" +
                "&" + operationFieldName + "=-1" +
                "&javax.faces.ViewState=" + java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildOperationSelectionPayload(String formName, String stateFieldName,
                                                  String operationFieldName, String viewstate) {
        return "javax.faces.partial.ajax=true" +
                "&javax.faces.source=" + operationFieldName.replace("_input", "") +
                "&javax.faces.partial.execute=" + operationFieldName.replace("_input", "") +
                "&javax.faces.partial.render=" + operationFieldName.replace("_input", "") +
                "&javax.faces.behavior.event=change" +
                "&javax.faces.partial.event=change" +
                "&" + formName + "=" + formName +
                "&" + stateFieldName + "=MH" +
                "&" + operationFieldName + "=5007" +
                "&javax.faces.ViewState=" + java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildVehicleSearchPayload(String vehicleNum, String viewstate, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        String formName = extractFormName(doc);

        Element vehicleInput = doc.selectFirst("input[maxlength='10']");
        String vehicleFieldName = vehicleInput != null ? vehicleInput.attr("name") : "j_idt43:j_idt48";

        Element getDetailsButton = doc.selectFirst("button[title*='get owner and vehicle details']");
        String buttonName = getDetailsButton != null ? getDetailsButton.attr("name") : "j_idt43:getdetails";

        StringBuilder payload = new StringBuilder();
        payload.append("javax.faces.partial.ajax=true")
                .append("&javax.faces.source=").append(buttonName)
                .append("&javax.faces.partial.execute=@all")
                .append("&javax.faces.partial.render=taxcollodc popup")
                .append("&").append(buttonName).append("=").append(buttonName)
                .append("&").append(formName).append("=").append(formName)
                .append("&").append(vehicleFieldName).append("=").append(vehicleNum);

        addAllFormFields(doc, payload);
        payload.append("&javax.faces.ViewState=").append(java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8));
        return payload.toString();
    }

    private void addAllFormFields(Document doc, StringBuilder payload) {
        for (Element select : doc.select("select")) {
            String name = select.attr("name");
            if (!name.isEmpty()) {
                Element selectedOption = select.selectFirst("option[selected]");
                String value = selectedOption != null ? selectedOption.attr("value") : "-1";
                payload.append("&").append(name).append("=")
                        .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        for (Element input : doc.select("input[type='text'], input[type='hidden']")) {
            String name = input.attr("name");
            String value = input.attr("value");
            if (!name.isEmpty() && !name.contains("ViewState")) {
                payload.append("&").append(name).append("=")
                        .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
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

    private Map<String, String> parseVehicleDetailsFromResponse(String xmlResponse, String vehicleNum) {
        Map<String, String> result = new HashMap<>();
        try {
            Document xmlDoc = Jsoup.parse(xmlResponse, Parser.xmlParser());
            for (Element update : xmlDoc.select("update")) {
                String updateId = update.attr("id");
                if (updateId.contains("taxcollodc") || updateId.contains("popup")) {
                    Document contentDoc = Jsoup.parse(update.text());
                    for (String label : FIELD_LIST) {
                        Element labelElement = contentDoc.selectFirst("span.ui-outputlabel-label:contains(" + label + ")");
                        if (labelElement != null) {
                            Element parentLabel = labelElement.closest("label.field-label");
                            if (parentLabel != null) {
                                Element inputElement = parentLabel.nextElementSibling();
                                if (inputElement != null) {
                                    String value = "";
                                    if (inputElement.tagName().equals("input")) {
                                        value = inputElement.attr("value");
                                    } else if (inputElement.hasClass("ui-selectonemenu")) {
                                        Element selectedOption = inputElement.selectFirst("select option[selected]");
                                        if (selectedOption != null) value = selectedOption.text();
                                    } else if (inputElement.hasClass("ui-calendar")) {
                                        Element calendarInput = inputElement.selectFirst("input");
                                        if (calendarInput != null) value = calendarInput.attr("value");
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
