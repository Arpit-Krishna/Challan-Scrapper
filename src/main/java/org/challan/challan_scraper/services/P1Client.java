package org.challan.challan_scraper.services;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.challan.challan_scraper.DTO.WebSourceContext;
import org.challan.challan_scraper.DTO.P1Response;
import org.challan.challan_scraper.DTO.P1Data;
import org.challan.challan_scraper.utills.MapperUtils;
import org.challan.challan_scraper.utills.OkHttpUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class P1Client {

    private static final String BASE_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml";
    private static final String MAIN_URL = "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionOnlineOdc.xhtml";

    public String getData(String vehicleNum) throws Exception {
        WebSourceContext context = new WebSourceContext(vehicleNum);
        buildContext(context);

        P1Response response = callMhOdcApis(context);

        return MapperUtils.convertObjectToString(response);
    }

    private void buildContext(WebSourceContext context) throws Exception {
        context.setCookie(callHomepage(context));
        // viewState will be updated inside subsequent calls
    }

    private String callHomepage(WebSourceContext context) throws Exception {
        Request request = new Request.Builder()
                .url(BASE_URL)
                .get()
                .build();

        try (Response response = OkHttpUtils.getOkHttpClient(500).newCall(request).execute()) {
            if (response.code() == 200) {
                List<String> cookieHeaders = Arrays.stream(response.headers().values("Set-Cookie").toArray(new String[0])).toList();
                String[] jSessionId = cookieHeaders.get(0).split(";");
                String[] serverId = cookieHeaders.get(1).split(";");
                return serverId[0] + ";" + jSessionId[0];
            } else {
                throw new Exception("Failed to fetch homepage cookie for MH ODC");
            }
        }
    }

    private P1Response callMhOdcApis(WebSourceContext context) throws Exception {
        // STEP 1: Initial GET
        String html = callGetApi(context, BASE_URL, null);
        context.setViewState(extractViewStateFromHtml(html));

        // STEP 2: Perform 3 POST calls (simulate form steps)
        callPostApi(context, "STEP_1", null);
        callPostApi(context, "STEP_2", null);
        callPostApi(context, "STEP_3", null);

        // STEP 3: One GET request to fetch form page
        String formHtml = callGetApi(context, MAIN_URL,
                BASE_URL);
        context.setViewState(extractViewStateFromHtml(formHtml));

        // STEP 4: Final POST with vehicle number
        String finalHtml = callPostApi(context, "FINAL", context.getVehicleNum());

        return createResponse(finalHtml, context);
    }

    private String callGetApi(WebSourceContext context, String url, String referer) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (StringUtils.isNotEmpty(referer)) {
            builder.addHeader("Referer", referer);
        }
        builder.addHeader("Cookie", context.getCookie());

        try (Response response = OkHttpUtils.getOkHttpClient(2000).newCall(builder.build()).execute()) {
            return response.body().string();
        }
    }

    private String callPostApi(WebSourceContext context, String step, String vehicleNum) throws Exception {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
        String body = getBodyRequestForStep(step, context, vehicleNum);

        Request request = new Request.Builder()
                .url((step.equals("FINAL")) ? MAIN_URL : BASE_URL)
                .post(RequestBody.create(mediaType, body))
                .addHeader("Cookie", context.getCookie())
                .addHeader("Faces-Request", "partial/ajax")
                .build();

        try (Response response = OkHttpUtils.getOkHttpClient(3500).newCall(request).execute()) {
            return response.body().string();
        }
    }

    private String getBodyRequestForStep(String step, WebSourceContext context, String vehicleNum) {
        return switch (step) {
            case "STEP_1" -> "javax.faces.partial.ajax=true&javax.faces.source=dropdown1&javax.faces.ViewState=" + context.getViewState();
            case "STEP_2" -> "javax.faces.partial.ajax=true&javax.faces.source=dropdown2&javax.faces.ViewState=" + context.getViewState();
            case "STEP_3" -> "javax.faces.partial.ajax=true&javax.faces.source=nextButton&javax.faces.ViewState=" + context.getViewState();
            case "FINAL" -> "javax.faces.partial.ajax=true&javax.faces.source=btnSearch&vehicleNo=" + vehicleNum +
                    "&javax.faces.ViewState=" + context.getViewState();
            default -> throw new IllegalArgumentException("Unknown step " + step);
        };
    }

    private String extractViewStateFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Element scriptElement = doc.getElementById("j_id1:javax.faces.ViewState:0");
        return scriptElement != null ? scriptElement.val() : null;
    }

    private P1Response createResponse(String html, WebSourceContext context) throws Exception {
        Document doc = Jsoup.parse(html);

        P1Response response = new P1Response();
        response.setSource("MH");
        response.setStatus(200);
        response.setMessage("Success");

        P1Data data = new P1Data();
        data.setVehicleNum(context.getVehicleNum());
        data.setOwnerName(doc.select("input#ownerName").val());
        data.setChassisNo(doc.select("input#chassisNo").val());
        data.setRegDate(doc.select("input#regDate").val());
        data.setFitUpto(doc.select("input#fitnessDate").val());
        data.setInsuranceUpto(doc.select("input#insuranceDate").val());
        data.setTaxUpto(doc.select("input#taxDate").val());

        response.setData(data);

        return response;
    }
}





//package org.challan.challan_scraper.services;
//
////import com.cuvora.fireprox.dto.analytics.SearchEvent;
////import com.cuvora.fireprox.dto.common.Constants;
////import com.cuvora.fireprox.dto.sources.web.p1.*;
////import com.cuvora.fireprox.utils.ElasticSearchUtils;
////import com.cuvora.fireprox.utils.MapperUtils;
////import com.cuvora.fireprox.utils.OkHttpUtils;
//import lombok.extern.slf4j.Slf4j;
//import okhttp3.MediaType;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.exception.ExceptionUtils;
//import org.challan.challan_scraper.DTO.P1Constants;
//import org.challan.challan_scraper.DTO.P1Constants.*;
//import org.challan.challan_scraper.DTO.P1Data;
//import org.challan.challan_scraper.DTO.P1Response;
//import org.challan.challan_scraper.DTO.WebSourceContext;
//import org.challan.challan_scraper.utills.MapperUtils;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import java.util.Arrays;
//import java.util.List;
//
//import static org.challan.challan_scraper.DTO.P1Constants.P1_TAX_1_URL;
//import static org.challan.challan_scraper.DTO.P1Constants.P1_TAX_2_URL;
//
////import static com.cuvora.fireprox.dto.sources.web.p1.P1Constants.*;
////import static com.cuvora.fireprox.utils.ServerUtils.getMachinePublicIp;
//@Slf4j @Service
//public class P1Client {
////    private final WebScrapeSourceDistribution webScrapeSourceDistribution;
////    @Autowired
////    public P1Client(WebScrapeSourceDistribution webScrapeSourceDistribution) {
////        this.webScrapeSourceDistribution = webScrapeSourceDistribution;
////    }
//    public String getData(String vehicleNum) throws Exception {
//        WebSourceContext context = new WebSourceContext(vehicleNum);
//        String state = "KA";//webScrapeSourceDistribution.getSourceName(); //get distributed source
//        if (StringUtils.isEmpty(state)) state = "KL";
//        buildContext(context, state);
//        P1Response response = callGetTaxCollectionAPIs(context);
//        return MapperUtils.convertObjectToString(response);
//    }
//    private void buildContext(WebSourceContext context, String src) throws Exception {
//        context.setCookie(callHomepage(context));
//        context.setStateName(src);
//    }
//    private String callHomepage(WebSourceContext context) throws Exception {
//        Request request = new Request.Builder().url("https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml")
//                .method("GET", null)
//                .build();
//        try (Response response = OkHttpUtils.getOkHttpClient(500).newCall(request).execute()) {
//            long startTime = System.currentTimeMillis();
//            if (response.code() == 200) {
//                List<String> cookieHeaders = Arrays.stream(response.headers().values("Set-Cookie").toArray(new String[0])).toList();
//                String[] jSessionId = cookieHeaders.get(0).split(";");
//                String[] serverId = cookieHeaders.get(1).split(";");
//                return serverId[0] + ";" + jSessionId[0];
//            } else {
////                pushSourceEventToEs("P1_HOMEPAGE_CALL_FAILED", MapperUtils.convertObjectToString(response), System.currentTimeMillis() - startTime, null, response.code(), context);
//                throw new Exception("Exception in fetching cookie");
//            }
//        }
//    }
//    private P1Response callGetTaxCollectionAPIs(WebSourceContext context) throws Exception {
//        String html = callTaxGetApi(context, P1_TAX_1_URL, P1Constants.P1_HOMEPAGE_URL);
//        context.setViewState(extractViewStateFromHtml(html, context));
//        String goButtonId = extractGoButtonIdFromHtml(html, context);
//        callTaxPostApi(context, P1_TAX_1_URL, getBodyRequestForPostCall("STEP_1", context, null, 0), P1_TAX_1_URL);
//        callTaxPostApi(context, P1_TAX_1_URL, getBodyRequestForPostCall("STEP_2", context, goButtonId, 0), P1_TAX_1_URL);
//        String html2 = callTaxGetApi(context, P1_TAX_2_URL, P1_TAX_1_URL);
//        context.setViewState(extractViewStateFromHtml(html2, context));
//        String sourceId = extractSourceButtonIdFromHtml(html2, context);
//        String sourceButtonId = sourceId.replace("j_idt", "");
//        int sourceButtonIdIntValue = Integer.parseInt(sourceButtonId);
//        String response = callTaxPostApi(context, P1_TAX_2_URL, getBodyRequestForPostCall("STEP_3", context, null, sourceButtonIdIntValue), P1_TAX_2_URL);
//        //Get Response
//        return createResponse(response, context);
//    }
//    private String callTaxGetApi(WebSourceContext context, String url, String referer) throws Exception {
//        Request request = new Request.Builder().url(url)
//                .method("GET", null)
//                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
//                .addHeader("Accept-Language", "en-US,en;q=0.9")
//                .addHeader("Cache-Control", "no-cache")
//                .addHeader("Connection", "keep-alive")
//                .addHeader("Pragma", "no-cache")
//                .addHeader("Referer", referer)
//                .addHeader("Sec-Fetch-Dest", "document")
//                .addHeader("Sec-Fetch-Mode", "navigate")
//                .addHeader("Sec-Fetch-Site", "same-origin")
//                .addHeader("Sec-Fetch-User", "?1")
//                .addHeader("Upgrade-Insecure-Requests", "1")
//                .addHeader("Cookie", context.getCookie())
//                .build();
//        try (Response response = OkHttpUtils.getOkHttpClient(500).newCall(request).execute()) {
//            return response.body().string();
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_GET_API_FAILED", null, 0, e, 400, context);
//            throw e;
//        }
//    }
//    private String callTaxPostApi(WebSourceContext context, String url, String body, String referer) throws Exception {
//        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
//        Request request = new Request.Builder().url(url)
//                .post(RequestBody.create(mediaType, body))
//                .addHeader("Accept", "application/xml, text/xml, */*; q=0.01")
//                .addHeader("Accept-Language", "en-US,en;q=0.9")
//                .addHeader("Cache-Control", "no-cache")
//                .addHeader("Connection", "keep-alive")
//                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
//                .addHeader("Faces-Request", "partial/ajax")
//                .addHeader("Origin", "https://checkpost.parivahan.gov.in")
//                .addHeader("Pragma", "no-cache")
//                .addHeader("Cookie", context.getCookie())
//                .addHeader("Referer", referer)
//                .addHeader("Sec-Fetch-Dest", "empty")
//                .addHeader("Sec-Fetch-Mode", "cors")
//                .addHeader("Sec-Fetch-Site", "same-origin")
//                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
//                .addHeader("X-Requested-With", "XMLHttpRequest")
//                .addHeader("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
//                .addHeader("sec-ch-ua-mobile", "?0")
//                .addHeader("sec-ch-ua-platform", "\"macOS\"")
//                .build();
//        try (Response response = OkHttpUtils.getOkHttpClient(3500).newCall(request).execute()) {
//            return response.body().string();
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_POST_API_FAILED", null, 0, e, 400, context);
//            throw e;
//        }
//    }
//    private String getBodyRequestForPostCall(String step, WebSourceContext context, String goButtonId, int sourceButtonId) {
//        switch (step) {
//            case "STEP_1" -> {
//                return "javax.faces.partial.ajax=true&javax.faces.source=ib_state&javax.faces.partial.execute=ib_state&javax.faces.partial.render=operation_code&javax.faces.behavior.event=change&javax.faces.partial.event=change&master_Layout_form=master_Layout_form&ib_state_focus=&ib_state_input="+ context.getStateName() + "&operation_code_focus=&operation_code_input=-1&javax.faces.ViewState=" + context.getViewState();
//            }
//            case "STEP_2" -> {
//                return "javax.faces.partial.ajax=true&javax.faces.source= " + goButtonId + "&javax.faces.partial.execute=%40all&" + goButtonId + "=" + goButtonId + "&PAYMENT_TYPE=ONLINE&master_Layout_form=master_Layout_form&ib_state_focus=&ib_state_input="+context.getStateName()+"&operation_code_focus=&operation_code_input=5003&javax.faces.ViewState=" + context.getViewState();
//            }
//            case "STEP_3" -> {
//                return "javax.faces.partial.ajax=true&javax.faces.source=j_idt" + sourceButtonId + "&javax.faces.partial.execute=%40all&javax.faces.partial.render=kataxcollection+ConfirmationDialog+popup+ConfirmationDialogCash&j_idt" + sourceButtonId + "=j_idt" + sourceButtonId + "&master_Layout_form=master_Layout_form&j_idt" + (sourceButtonId - 2) + "=" + context.getVehicleNum() + "&mobileno=&j_idt" + (sourceButtonId + 15) + "_input=-1&district_input=-1&regn_dt_input=&cmb_service_type_input=-1&cmb_permit_type_input=-1&j_idt"+(sourceButtonId + 51)+ "=&txt_seat_cap=&cmb_payment_mode_input=-1&j_idt" + (sourceButtonId + 86) + "_input=-1&purposeofjourney=&cal_tax_from_input=&javax.faces.ViewState=" + context.getViewState();
//            }
//            default -> {
//                return null;
//            }
//        }
//    }
//    private P1Response createResponse(String html, WebSourceContext context) throws Exception {
//        try {
//            String htm = html.replace("<![CDATA[", "");
//            Document doc = Jsoup.parse(htm);
//            System.out.println(doc);
//            Element alertPopup = doc.select("li[role=alert]").first();
//            P1Response response = new P1Response();
//            response.setSource(context.getStateName());
//            if (alertPopup != null && "No data found for this vehicle number. Please enter the details".equalsIgnoreCase(alertPopup.text())) {
//                response.setStatus(1003);
//                response.setMessage("Record Not Found");
//                return response;
//            }
//            Element table = doc.select("div#ConfirmationDialog_content table.datatable-panel-100").first();
//            P1Data data = new P1Data();
//            data.setVehicleNum(getElementFromTable(table, 0, context));
//            data.setOwnerName(getElementFromTable(table, 1, context));
//            data.setChassisNo(getElementFromTable(table, 2, context));
////            //Extract RTO
////            Element element = doc.select("label").first();
////            String startingIndex = element.select("label").get(1).id();
////            int id = Integer.parseInt(startingIndex.replace("j_idt", ""));
////            Element rtoElement = doc.select("select#j_idt" + (id + 38) + "_input").first();
////            data.setRto(rtoElement.select("option[selected]").text());
//            //Extract cc
//            data.setCc(doc.select("input#txt_cc").val());
//            //Extract sale amount
//            data.setSaleAmount(doc.select("input#txt_sale_amount").val());
////            //Extract Registration Type
////            Element regTypeElement = doc.select("select#cmb_regn_type_input").first();
////            data.setRegisType(regTypeElement.select("option[selected]").text());
//            //Extract fitUpto
//            data.setFitUpto(doc.select("input#cal_fitness_vali_input").val());
//            //Extract insuranceUpto
//            data.setInsuranceUpto(doc.select("input#cal_insurance_vali_input").val());
//            //Extract taxUpto
//            data.setTaxUpto(doc.select("input#cal_tax_vali_input").val());
//            //Extract regData
//            data.setRegDate(doc.select("input#regn_dt_input").val());
//            //Extract fuelType
////            Element fuelElement = doc.select("select#j_idt" + (id + 164) + "_input").first();
////            data.setFuel(fuelElement.select("option[selected]").text());
////            Element stateElement = doc.select("select#j_idt" + (id + 32) + "_input").first();
////            data.setState(stateElement.select("option[selected]").text());
//            response.setData(data);
//            response.setStatus(200);
//            response.setMessage("Success");
//            return response;
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_CREATE_RESPONSE_FAILED", html, 0, e, 400, context);
//            throw e;
//        }
//    }
//    private String getElementFromTable(Element table, int index, WebSourceContext context) {
//        try {
//            Elements rows = table.select("tbody tr");
//            return rows.get(index).select("td").get(2).text();
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_FETCH_VIEW_STATE_FAILED", MapperUtils.convertObjectToString(table), 0, e, 400, context);
//            throw e;
//        }
//    }
//    private String extractViewStateFromHtml(String html, WebSourceContext context) {
//        try {
//            Document doc = Jsoup.parse(html);
//            // Extract the value from the script tag
//            Element scriptElement = doc.getElementById("j_id1:javax.faces.ViewState:0");
//            return scriptElement.val();
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_FETCH_VIEW_STATE_FAILED", html, 0, e, 400, context);
//            throw e;
//        }
//    }
//    private String extractGoButtonIdFromHtml(String html, WebSourceContext context) {
//        try {
//            Document doc = Jsoup.parse(html);
//            Element goButton = doc.select("[type=submit]").last();
//            return goButton.id();
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_FETCH_GO_BUTTON_FAILED", html, 0, e, 400, context)
//            throw e;
//        }
//    }
//    private String extractSourceButtonIdFromHtml(String html, WebSourceContext context) {
//        try {
//            Document doc = Jsoup.parse(html);
//            Element goButton = doc.select("[type=submit]").first();
//            return goButton.id();
//        } catch (Exception e) {
////            pushSourceEventToEs("P1_FETCH_SOURCE_BUTTON_FAILED", html, 0, e, 400, context);
//            throw e;
//        }
//    }
////    protected void pushSourceEventToEs(String key, String response, long timeTaken, Exception e, int responseCode, WebSourceContext context) {
////        String debugMsg = e != null ? ExceptionUtils.getMessage(e) : null;
////        String salt = e != null ? ExceptionUtils.getStackTrace(e) : response;
////        int status = StringUtils.endsWithIgnoreCase(key, "_Failed") ? 400 : 200;
////      ElasticSearchUtils.postEvent(new SearchEvent.Builder().clientId(debugMsg).vehicleNum(context.getVehicleNum())
////                .userId("Partner").ip(getMachinePublicIp()).success(e == null).src(context.getStateName()).meta(context.toString()).db(false)
////                .type(SearchEvent.EventType.SEARCH).msg(key).status(status).salt(salt).ts(context.getCookie()).param(responseCode + "")
////                .searchCount(0).maydayStatus("Live Data").waitTime(timeTaken).build(), Constants.SEARCH_INDEX);
////    }
//}