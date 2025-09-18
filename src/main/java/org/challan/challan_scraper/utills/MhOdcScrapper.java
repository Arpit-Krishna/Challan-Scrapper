package org.challan.challan_scraper.utills;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MhOdcScrapper {

    private static final String BASE_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml";
    private static final String MAIN_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionOnlineOdc.xhtml";

    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            Map.entry("Vehicle No.", "j_idt43:j_idt48"),
            Map.entry("Vehicle Type", "j_idt43:j_idt54_input"),
            Map.entry("Chassis No.", "j_idt43:j_idt59"),
            Map.entry("Owner Name", "j_idt43:j_idt63"),
            Map.entry("Vehicle Class", "j_idt43:j_idt70_input"),
            Map.entry("GVW (In Kg.)", "j_idt43:txt_laden_wt"),
            Map.entry("Unladen Weight(In Kg.)", "j_idt43:txt_unladen_wt"),
            Map.entry("Load Carrying Capacity of Vehicle(In Kg.)", "j_idt43:txt_lccv"),
            Map.entry("Road Tax Validity", "j_idt43:tax_clear_to1_input"),
            Map.entry("Insurance Validity", "j_idt43:ins_validity_input"),
            Map.entry("Fitness Validity", "j_idt43:fitness_valid_input"),
            Map.entry("PUCC Validity", "j_idt43:pucc_validity_input"),
            Map.entry("Registration Date", "j_idt43:j_idt187_input"),
            Map.entry("Address", "j_idt43:j_idt227")
    );


    private final HttpClient client;
    CookieManager cm = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    public MhOdcScrapper() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .cookieHandler(this.cm) // Add cookie handling
                .build();
    }

    private String extractViewState(String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Element el = doc.selectFirst("input[name='javax.faces.ViewState']");
        if (el == null) {
            // Try alternative selector
            el = doc.selectFirst("input[id*='ViewState']");
        }
        return el != null ? el.attr("value") : null;
    }

    private String extractViewStateFromXml(String xml) throws Exception {
        try {
            // First try JSoup parsing for simpler cases
            Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
            Element update = doc.selectFirst("update[id*='ViewState']");
            if (update != null) {
                return update.text().trim();
            }

            // Fallback to regex if JSoup fails
            Pattern pattern = Pattern.compile(
                    "<update\\s+id=\"[^\"]*ViewState[^\"]*\">\\s*<!\\[CDATA\\[(.*?)]]>\\s*</update>",
                    Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(xml);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }

            // DOM parser as last resort
            var dbf = DocumentBuilderFactory.newInstance();
            var db = dbf.newDocumentBuilder();
            var xmlDoc = db.parse(new InputSource(new StringReader(xml)));
            NodeList updates = xmlDoc.getElementsByTagName("update");
            for (int i = 0; i < updates.getLength(); i++) {
                var node = updates.item(i);
                var id = node.getAttributes().getNamedItem("id").getNodeValue();
                if (id.contains("javax.faces.ViewState")) {
                    return node.getTextContent().trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting ViewState from XML: " + e.getMessage());
        }
        return null;
    }

    public Map<String, String> fetchVehicleDetails(String vehicleNum) throws Exception {
        Map<String, String> result = new HashMap<>();

        try {
            // Step 1: Load initial page to get session and ViewState
            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .GET()
                    .build();

            HttpResponse<String> response1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
            String baseHtml = response1.body();
            String viewstate1 = extractViewState(baseHtml);

            if (viewstate1 == null) {
                throw new Exception("Could not extract initial ViewState");
            }

            // Extract form field names from initial page
            Document initialDoc = Jsoup.parse(baseHtml);
            String formName = extractFormName(initialDoc);
            String stateFieldName = extractStateFieldName(initialDoc);
            String operationFieldName = extractOperationFieldName(initialDoc);
            String goButton = findGoButtonId(initialDoc);
            System.out.println("Cookies: " + cm.getCookieStore().getCookies());


            // Step 2: Select state (MH) - This is mandatory
            String statePayload = buildStateSelectionPayload(formName, stateFieldName, operationFieldName, viewstate1);

            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/xml, text/xml, */*; q=0.01")
                    .header("Referer", BASE_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(statePayload))
                    .build();

            HttpResponse<String> response2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
            String stateXml = response2.body();
            String viewstate2 = extractViewStateFromXml(stateXml);
            System.out.println("Cookies: " + cm.getCookieStore().getCookies());



            if (viewstate2 == null) {
                throw new Exception("Could not extract ViewState after state selection");
            }

            // Step 3: Select operation (5007 for ODC) - This is also mandatory
            String opPayload = buildOperationSelectionPayload(formName, stateFieldName, operationFieldName, viewstate2);

            HttpRequest req3 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/xml, text/xml, */*; q=0.01")
                    .header("Referer", BASE_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(opPayload))
                    .build();

            HttpResponse<String> response3 = client.send(req3, HttpResponse.BodyHandlers.ofString());
            String opXml = response3.body();
            String viewstate3 = extractViewStateFromXml(opXml);
            System.out.println("Cookies: " + cm.getCookieStore().getCookies());


            if (viewstate3 == null) {
                throw new Exception("Could not extract ViewState after operation selection");
            }

            // Step 4: Submit the form data to base URL (this will establish session state)
            String submitPayload = buildFormSubmissionPayload(formName, stateFieldName, goButton, operationFieldName, viewstate3);

            HttpRequest req4 = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", BASE_URL)
                    .header("Upgrade-Insecure-Requests", "1")
                    .POST(HttpRequest.BodyPublishers.ofString(submitPayload))
                    .build();

            HttpResponse<String> response4 = client.send(req4, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response4.statusCode());
            System.out.println("Headers: " + response4.headers().map());
//            System.out.println("Body: " + response4.body());

            // Step 5: Now make a clean GET request to the ODC form page
            HttpRequest req5 = HttpRequest.newBuilder()
                    .uri(URI.create(MAIN_URL))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-GB,en;q=0.8")
                    .header("Referer", BASE_URL)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-User", "?1")
                    .header("Sec-GPC", "1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                    .header("sec-ch-ua", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Brave\";v=\"140\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .GET()
                    .build();

            HttpResponse<String> response5 = client.send(req5, HttpResponse.BodyHandlers.ofString());
            String odcHtml = response5.body();
            String viewstateOdc = extractViewState(odcHtml);
            System.out.println("Cookies: " + cm.getCookieStore().getCookies());


            if (viewstateOdc == null) {
                throw new Exception("Could not extract ODC form ViewState");
            }

            // Step 6: Submit vehicle search request
            String vehiclePayload = buildVehicleSearchPayload(vehicleNum, viewstateOdc, odcHtml);

            HttpRequest req6 = HttpRequest.newBuilder()
                    .uri(URI.create(MAIN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Faces-Request", "partial/ajax")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/xml, text/xml, */*; q=0.01")
                    .header("Referer", MAIN_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(vehiclePayload))
                    .build();

            HttpResponse<String> response6 = client.send(req6, HttpResponse.BodyHandlers.ofString());
            String xmlResponse = response6.body();

            // Parse response and extract vehicle details
            result = parseVehicleDetailsFromResponse(xmlResponse, vehicleNum);

        } catch (Exception e) {
            System.err.println("Error fetching vehicle details: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        return result;
    }

    private String buildFormSubmissionPayload(String formName, String stateFieldName, String goButton, String operationFieldName, String viewstate) {
        StringBuilder payload = new StringBuilder();
        payload.append(formName).append("=").append(formName);
        payload.append("&").append(stateFieldName).append("=MH");
        payload.append("&").append(operationFieldName).append("=5007");
        payload.append("&").append(goButton).append("=").append(goButton);
        payload.append("&javax.faces.ViewState=").append(java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8));

        return payload.toString();
    }

    private String findGoButtonId(Document doc) {
        // Find all <button> elements
        for (Element button : doc.select("button")) {
            String text = button.text().trim();
            if (text.equalsIgnoreCase("Go")) {
                return button.id(); // return the id attribute
            }
        }
        return null; // if no "Go" button found
    }

    private String extractFormName(Document doc) {
        Element form = doc.selectFirst("form[method='post']");
        return form != null ? form.attr("id") : "master_Layout_form";
    }

    private String extractStateFieldName(Document doc) {
        // Look for state dropdown - it should have states like MH, GJ, etc.
        Element stateSelect = doc.selectFirst("select option[value='MH']");
        if (stateSelect != null) {
            Element parent = stateSelect.parent();
            return parent.attr("name");
        }
        // Fallback to common field name
        return "ib_state_input";
    }

    private String extractOperationFieldName(Document doc) {
        // Look for operation dropdown - it should have operation codes
        Element operationSelect = doc.selectFirst("select option[value='5007']");
        if (operationSelect != null) {
            Element parent = operationSelect.parent();
            return parent.attr("name");
        }
        // Fallback to common field name
        return "operation_code_input";
    }

    private String buildStateSelectionPayload(String formName, String stateFieldName, String operationFieldName, String viewstate) {
        StringBuilder payload = new StringBuilder();
        payload.append("javax.faces.partial.ajax=true");
        payload.append("&javax.faces.source=").append(stateFieldName.replace("_input", ""));
        payload.append("&javax.faces.partial.execute=").append(stateFieldName.replace("_input", ""));
        payload.append("&javax.faces.partial.render=").append(operationFieldName.replace("_input", ""));
        payload.append("&javax.faces.behavior.event=change");
        payload.append("&javax.faces.partial.event=change");
        payload.append("&").append(formName).append("=").append(formName);
        payload.append("&").append(stateFieldName).append("=MH");
        payload.append("&").append(operationFieldName).append("=-1");
        payload.append("&javax.faces.ViewState=").append(java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8));

        return payload.toString();
    }

    private String buildOperationSelectionPayload(String formName, String stateFieldName, String operationFieldName, String viewstate) {
        StringBuilder payload = new StringBuilder();
        payload.append("javax.faces.partial.ajax=true");
        payload.append("&javax.faces.source=").append(operationFieldName.replace("_input", ""));
        payload.append("&javax.faces.partial.execute=").append(operationFieldName.replace("_input", ""));
        payload.append("&javax.faces.partial.render=").append(operationFieldName.replace("_input", ""));
        payload.append("&javax.faces.behavior.event=change");
        payload.append("&javax.faces.partial.event=change");
        payload.append("&").append(formName).append("=").append(formName);
        payload.append("&").append(stateFieldName).append("=MH");
        payload.append("&").append(operationFieldName).append("=5007");
        payload.append("&javax.faces.ViewState=").append(java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8));

        return payload.toString();
    }

    private String buildVehicleSearchPayload(String vehicleNum, String viewstate, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        String formName = extractFormName(doc);

        // Extract field names from ODC form
        Element vehicleInput = doc.selectFirst("input[maxlength='10']");
        String vehicleFieldName = vehicleInput != null ? vehicleInput.attr("name") : "j_idt43:j_idt48";

        Element getDetailsButton = doc.selectFirst("button[title*='get owner and vehicle details']");
        String buttonName = getDetailsButton != null ? getDetailsButton.attr("name") : "j_idt43:getdetails";

        StringBuilder payload = new StringBuilder();
        payload.append("javax.faces.partial.ajax=true");
        payload.append("&javax.faces.source=").append(buttonName);
        payload.append("&javax.faces.partial.execute=@all");
        payload.append("&javax.faces.partial.render=taxcollodc popup");
        payload.append("&").append(buttonName).append("=").append(buttonName);
        payload.append("&").append(formName).append("=").append(formName);
        payload.append("&").append(vehicleFieldName).append("=").append(vehicleNum);

        // Add all form fields with their current values
        addAllFormFields(doc, payload);

        payload.append("&javax.faces.ViewState=").append(java.net.URLEncoder.encode(viewstate, java.nio.charset.StandardCharsets.UTF_8));

        return payload.toString();
    }

    private void addAllFormFields(Document doc, StringBuilder payload) {
        // Add select fields
        for (Element select : doc.select("select")) {
            String name = select.attr("name");
            if (!name.isEmpty()) {
                Element selectedOption = select.selectFirst("option[selected]");
                String value = selectedOption != null ? selectedOption.attr("value") : "-1";
                payload.append("&").append(name).append("=")
                        .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        // Add text input fields with empty values (except vehicle number which is set separately)
        for (Element input : doc.select("input[type='text'], input[type='hidden']")) {
            String name = input.attr("name");
            String value = input.attr("value");
            if (!name.isEmpty() && !name.contains("ViewState")) {
                if (name.contains("mobileno") || name.contains("email") || name.contains("distance")) {
                    // Set some default values for required fields
                    value = "";
                }
                payload.append("&").append(name).append("=")
                        .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        // Add checkbox fields
        for (Element checkbox : doc.select("input[type='checkbox']")) {
            String name = checkbox.attr("name");
            if (!name.isEmpty()) {
                String value = checkbox.hasAttr("checked") ? "on" : "off";
                payload.append("&").append(name).append("=").append(value);
            }
        }
    }

//    private Map<String, String> parseVehicleDetailsFromResponse(String xmlResponse, String vehicleNum) {
//        Map<String, String> result = new HashMap<>();
//
//        try {
//            // Parse the XML response
//            Document xmlDoc = Jsoup.parse(xmlResponse, "", org.jsoup.parser.Parser.xmlParser());
//
//            // Look for updates containing vehicle data
//            for (Element update : xmlDoc.select("update")) {
//                String updateId = update.attr("id");
//                if (updateId.contains("taxcollodc") || updateId.contains("popup")) {
//                    // Parse the HTML content within the update
//                    Document contentDoc = Jsoup.parse(update.text());
//
//                    // Extract vehicle details from various input fields
//                    extractFieldValue(contentDoc, result, "Vehicle No.", "input[maxlength='10']");
//                    extractFieldValue(contentDoc, result, "Owner Name", "input[maxlength='50']");
//                    extractFieldValue(contentDoc, result, "Chassis No.", "input[maxlength='30']");
//                    extractFieldValue(contentDoc, result, "GVW", "input[maxlength='6']");
//
//                    // Extract from select elements
//                    extractSelectValue(contentDoc, result, "Vehicle Type", "select option[selected]");
//                    extractSelectValue(contentDoc, result, "Vehicle Class", "select option[selected]");
//
//                    break;
//                }
//            }
//
//            // If no details found, add the vehicle number at least
//            if (result.isEmpty()) {
//                result.put("Vehicle No.", vehicleNum);
//                result.put("Status", "No details found or vehicle not in database");
//            }
//
//        } catch (Exception e) {
//            System.err.println("Error parsing vehicle details: " + e.getMessage());
//            result.put("Vehicle No.", vehicleNum);
//            result.put("Error", "Failed to parse response: " + e.getMessage());
//        }
//
//        return result;
//    }

    private void extractFieldValue(Document doc, Map<String, String> result, String key, String selector) {
        Element element = doc.selectFirst(selector);
        if (element != null) {
            String value = element.attr("value");
            if (!value.isEmpty()) {
                result.put(key, value);
            }
        }
    }

    private void extractSelectValue(Document doc, Map<String, String> result, String key, String selector) {
        Element element = doc.selectFirst(selector);
        if (element != null) {
            String value = element.text();
            if (!value.isEmpty() && !value.contains("---Select")) {
                result.put(key, value);
            }
        }
    }

    private Map<String, String> parseVehicleDetailsFromResponse(String xmlResponse, String vehicleNum) {
        Map<String, String> result = new HashMap<>();

        try {
            // Parse the XML response
            Document xmlDoc = Jsoup.parse(xmlResponse, "", org.jsoup.parser.Parser.xmlParser());

            // Look for updates containing vehicle data
            for (Element update : xmlDoc.select("update")) {
                String updateId = update.attr("id");
                if (updateId.contains("taxcollodc") || updateId.contains("popup")) {
                    // Parse the HTML content within the update
                    Document contentDoc = Jsoup.parse(update.text());

                    // Iterate FIELD_MAP and extract values
                    for (Map.Entry<String, String> entry : FIELD_MAP.entrySet()) {
                        String label = entry.getKey();
                        String fieldId = entry.getValue();

                        if (fieldId.endsWith("_input")) {
                            // likely a <select> or calendar field
                            extractSelectOrInputValue(contentDoc, result, label, "#" + cssEscape(fieldId));
                        } else {
                            // normal input or textarea
                            extractInputOrTextareaValue(contentDoc, result, label, "#" + cssEscape(fieldId));
                        }
                    }

                    break;
                }
            }

            // If no details found, add vehicle number at least
            if (result.isEmpty()) {
                result.put("Vehicle No.", vehicleNum);
                result.put("Status", "No details found or vehicle not in database");
            }

        } catch (Exception e) {
            System.err.println("Error parsing vehicle details: " + e.getMessage());
            result.put("Vehicle No.", vehicleNum);
            result.put("Error", "Failed to parse response: " + e.getMessage());
        }

        return result;
    }

    private void extractInputOrTextareaValue(Document doc, Map<String, String> result, String key, String selector) {
        Element element = doc.selectFirst(selector);
        if (element != null) {
            String value = element.hasAttr("value") ? element.attr("value") : element.text();
            if (!value.isEmpty() && !value.contains("---Select")) {
                result.put(key, value);
            }
        }
    }

    private void extractSelectOrInputValue(Document doc, Map<String, String> result, String key, String selector) {
        Element element = doc.selectFirst(selector);
        if (element != null) {
            if (element.tagName().equals("select")) {
                Element selected = element.selectFirst("option[selected]");
                if (selected != null) {
                    String value = selected.text();
                    if (!value.isEmpty() && !value.contains("---Select")) {
                        result.put(key, value);
                    }
                }
            } else {
                String value = element.attr("value");
                if (!value.isEmpty() && !value.contains("---Select")) {
                    result.put(key, value);
                }
            }
        }
    }

    // JSF field IDs like "j_idt43:j_idt48" contain colons, which must be escaped in CSS selectors
    private String cssEscape(String id) {
        return id.replace(":", "\\:");
    }
}
