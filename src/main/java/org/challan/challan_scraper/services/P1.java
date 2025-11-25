package org.challan.challan_scraper.services;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.challan.challan_scraper.DTO.P1Data;
import org.challan.challan_scraper.DTO.P1Response;
import org.challan.challan_scraper.DTO.StateEnums;
import org.challan.challan_scraper.DTO.WebSourceContext;
import org.challan.challan_scraper.constants.P1Constants;
import org.challan.challan_scraper.utills.MapperUtils;
import org.challan.challan_scraper.utills.OkHttpUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.challan.challan_scraper.constants.P1Constants.*;


@Slf4j @Service
public class P1 {
    public String getData(String vehicleNum, String stateName) throws Exception {
        WebSourceContext context = new WebSourceContext(vehicleNum);
        String state = StringUtils.isBlank(stateName) ? StateEnums.randomStateCode() : stateName; //get distributed source
        buildContext(context, state);
        P1Response response = callGetTaxCollectionAPIs(context);
        System.out.println(response);
        return org.challan.challan_scraper.utills.MapperUtils.convertObjectToString(response);
    }

    private  String encodeState(String hex) {
        String hexAscii = stringToHex("7c606d287b6d6b7" + hex);
        byte[] asciiBytes = hexAscii.getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(asciiBytes);
    }

    private  String stringToHex(String input) {
        StringBuilder sb = new StringBuilder();

        for (char c : input.toCharArray()) {
            sb.append(String.format("%02x", (int) c));
        }

        return sb.toString();
    }
    private void buildContext(WebSourceContext context, String src) throws Exception {
        context.setCookie(callHomepage(context));
        context.setStateCode(src);
    }

    private String callHomepage(WebSourceContext context) throws Exception {
        Request request = new Request.Builder().url(P1_HOMEPAGE_URL)
                .method("GET", null)
                .addHeader("Referer", P1_REFERRER_URL)
                .build();
        try (Response response = OkHttpUtils.getOkHttpClient(1000).newCall(request).execute()) {
            long startTime = System.currentTimeMillis();
            if (response.code() == 200) {
                List<String> cookieHeaders = Arrays.stream(response.headers().values("Set-Cookie").toArray(new String[0])).toList();
                String[] jSessionId = cookieHeaders.get(0).split(";");
                String[] serverId = cookieHeaders.get(1).split(";");
                return serverId[0] + ";" + jSessionId[0];
            } else {
//                pushSourceEventToEs("P1_HOMEPAGE_CALL_FAILED", MapperUtils.convertObjectToString(response), System.currentTimeMillis() - startTime, null, response.code(), context);
                throw new Exception("Exception in fetching cookie");
            }
        }
    }

    private P1Response callGetTaxCollectionAPIs(WebSourceContext context) throws Exception {
        String html = callTaxGetApi(context, P1_HOMEPAGE_URL + "?statecd=" + encodeState(context.getStateCode()), P1_REFERRER_URL);

        context.setViewState(extractViewStateFromHtml(html, context));
        String goButtonId = extractGoButtonIdFromHtml(html, context);
        callTaxPostApi(context, P1_HOMEPAGE_URL, getBodyRequestForPostCall("STEP_1", context, goButtonId, null), P1_HOMEPAGE_URL );

        String html2 = callTaxGetApi(context, Objects.requireNonNull(StateEnums.fromStateCode(context.getStateCode())).getUrl(), P1_HOMEPAGE_URL);
        context.setViewState(extractViewStateFromHtml(html2, context));
        String sourceId = extractSourceButtonIdFromHtml(html2, context);
        String vehicleFieldId = extractVehicleFieldIdFromHtml(html2,context);

        String response = callTaxPostApi(context, Objects.requireNonNull(StateEnums.fromStateCode(context.getStateCode())).getUrl(), getBodyRequestForPostCall("STEP_2", context, vehicleFieldId, sourceId), Objects.requireNonNull(StateEnums.fromStateCode(context.getStateCode())).getUrl());
        return createResponse(response, context);
    }

    private String extractVehicleFieldIdFromHtml(String html2, WebSourceContext context) {
        try {
            Document doc = Jsoup.parse(html2);
            Element vehicleField = doc.selectFirst("input[maxlength='10']");
            return vehicleField.id();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_FETCH_VEHICLE_FIELD_FAILED", html2, 0, e, 400, context);
            throw e;
        }
    }

    private String callTaxGetApi(WebSourceContext context, String url, String referer) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (StringUtils.isNotBlank(referer)) {
            builder.addHeader("Referer",  referer + "?statecd=" + encodeState(context.getStateCode()));
        }
        if (StringUtils.isNotBlank(context.getCookie())) {
            builder.addHeader("Cookie", context.getCookie());
        }

        try (Response res = OkHttpUtils.getOkHttpClient(2000).newCall(builder.build()).execute()) {
            if (!res.isSuccessful()) {
                throw new Exception("GET request failed: " + res.code());
            }

            if (StringUtils.isBlank(context.getCookie())) {
                List<String> cookies = res.headers("Set-Cookie");
                if (!cookies.isEmpty()) {
                    String cookieHeader = cookies.stream()
                            .map(c -> c.split(";", 2)[0])
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("");
                    context.setCookie(cookieHeader);
                    log.debug("Captured cookies: {}", cookieHeader);
                }
            }
            return res.body().string();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_GET_API_FAILED", null, 0, e, 400, context);
            throw e;
        }
    }


    private String callTaxPostApi(WebSourceContext context, String url, String body, String referer) throws Exception {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(mediaType, body))
                .addHeader("Accept", "application/xml, text/xml, */*; q=0.01")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Connection", "keep-alive")
                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .addHeader("Faces-Request", "partial/ajax")
                .addHeader("Origin", "https://checkpost.parivahan.gov.in")
                .addHeader("Pragma", "no-cache")
                .addHeader("Cookie", context.getCookie())
                .addHeader("Referer", referer + "?statecd=" + encodeState(context.getStateCode()))
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "same-origin")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-platform", "\"macOS\"")
                .build();
        try (Response response = OkHttpUtils.getOkHttpClient(5500).newCall(request).execute()) {
            return response.body().string();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_POST_API_FAILED", null, 0, e, 400, context);
            throw e;
        }
    }

    private String getBodyRequestForPostCall(String step, WebSourceContext context, String goButtonId, String sourceButtonId) {
        switch (step) {
            case "STEP_1" -> {
                return "javax.faces.partial.ajax=true&javax.faces.source= " + goButtonId + "&javax.faces.partial.execute=%40all" + "&" + goButtonId + "=" + goButtonId + "&PAYMENT_TYPE=ONLINE" +
                        "&master_Layout_form=master_Layout_form&ib_state_filter=&operation_code_input=" + StateEnums.fromStateCode(context.getStateCode()).getOpCode() +  "&javax.faces.ViewState=" + context.getViewState();            }
            case "STEP_2" -> {
                return "javax.faces.partial.ajax=truejavax.faces.partial.ajax=true" + sourceButtonId + "&javax.faces.partial.execute=@all&javax.faces.partial.render=" + StateEnums.fromStateCode(context.getStateCode()).getRenderState() +
                        " popup&" + sourceButtonId + "=" + sourceButtonId + "&master_Layout_form=master_Layout_form&" + goButtonId + "=" + context.getVehicleNum() + "&javax.faces.ViewState=" + context.getViewState();
            }
            default -> {
                return null;
            }
        }
    }

    private P1Response createResponse(String html, WebSourceContext context) throws Exception {
        try {
            String htm = html.replace("<![CDATA[", "");
            Document doc = Jsoup.parse(htm);
            Element alertPopup = doc.select("li[role=alert]").first();
            P1Response response = new P1Response();
            response.setSource(context.getStateCode());
            if (alertPopup != null) {
                alertPopup.text();
                String normalized = alertPopup.text().trim().toLowerCase();
                if ("NO_DATA_MESSAGES".contains(normalized)) {
                    response.setStatus(1003);
                    response.setMessage("Record Not Found");
                    return response;
                }
            }
            return parseVehicleDetails(html,context);
        } catch (Exception e) {
//            pushSourceEventToEs("P1_CREATE_RESPONSE_FAILED", html, 0, e, 400, context);
            throw e;
        }
    }

    private String getElementFromTable(Element table, int index, WebSourceContext context) {
        try {
            Elements rows = table.select("tbody tr");
            return rows.get(index).select("td").get(2).text();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_FETCH_VIEW_STATE_FAILED", MapperUtils.convertObjectToString(table), 0, e, 400, context);
            throw e;
        }
    }

    private String extractViewStateFromHtml(String html, WebSourceContext context) {
        try {
            Document doc = Jsoup.parse(html);
            // Extract the value from the script tag
            Element scriptElement = doc.getElementById("j_id1:javax.faces.ViewState:0");
            return scriptElement.val();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_FETCH_VIEW_STATE_FAILED", html, 0, e, 400, context);
            throw e;
        }
    }

    private String extractGoButtonIdFromHtml(String html, WebSourceContext context) {
        try {
            Document doc = Jsoup.parse(html);
            Element goButton = doc.select("[type=submit]").last();
            return goButton.id();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_FETCH_GO_BUTTON_FAILED", html, 0, e, 400, context);
            throw e;
        }
    }

    private String extractSourceButtonIdFromHtml(String html, WebSourceContext context) {
        try {
            Document doc = Jsoup.parse(html);
            Element goButton = doc.selectFirst("[type=submit]");
            return goButton.id();
        } catch (Exception e) {
//            pushSourceEventToEs("P1_FETCH_SOURCE_BUTTON_FAILED", html, 0, e, 400, context);
            throw e;
        }
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


    private P1Response parseVehicleDetails(String xmlResponse, WebSourceContext ctx) {
        P1Data data = new P1Data();
        data.setVehicleNum(ctx.getVehicleNum());

        try {
            Document xmlDoc = Jsoup.parse(xmlResponse, Parser.xmlParser());

            Element update = xmlDoc.select("update[id*=" + StateEnums.fromStateCode(ctx.getStateCode()).getRenderState()  + "], update[id*=popup]").first();
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

                    if(StringUtils.equalsIgnoreCase(label, "Chassis No.") && value == null){

                    }

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
//            pushSourceEventToEs("P1_PARSE_RESPONSE_FAILED", xmlResponse, 0, e, 400, ctx);
        }

        return new P1Response(200, "Success", data, ctx.getStateCode());


    }


//    protected void pushSourceEventToEs(String key, String response, long timeTaken, Exception e, int responseCode, WebSourceContext context) {
//        String debugMsg = e != null ? ExceptionUtils.getMessage(e) : null;
//        String salt = e != null ? ExceptionUtils.getStackTrace(e) : response;
//        int status = StringUtils.endsWithIgnoreCase(key, "_Failed") ? 400 : 200;
//        ElasticSearchUtils.postEvent(new SearchEvent.Builder().clientId(debugMsg).vehicleNum(context.getVehicleNum())
//                .userId("Partner").ip(getMachinePublicIp()).success(e == null).src(context.getStateName()).meta(context.toString()).db(false)
//                .type(SearchEvent.EventType.SEARCH).msg(key).status(status).salt(salt).ts(context.getCookie()).param(responseCode + "")
//                .searchCount(0).maydayStatus("Live Data").waitTime(timeTaken).build(), Constants.SEARCH_INDEX);
//    }

}
