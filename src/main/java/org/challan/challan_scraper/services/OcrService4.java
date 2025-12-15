package org.challan.challan_scraper.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.*;
import org.challan.challan_scraper.DTO.ChallanInfo;
import org.challan.challan_scraper.DTO.S26Context;
import org.challan.challan_scraper.utills.MapperUtils;
import org.challan.challan_scraper.utills.OkHttpUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Service
public class OcrService4 {

    static  {
        try {
            Loader.load(opencv_core.class);
            System.setProperty("jna.library.path", "/opt/homebrew/opt/tesseract/lib");
            System.setProperty("TESSDATA_PREFIX", "/opt/homebrew/share/tessdata/");
            System.out.println("OpenCV loaded successfully");
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV");
            e.printStackTrace();
        }
    }

    private static final String S26_URL = "https://echallan.tspolice.gov.in/publicview/PendingChallans.do";
    private static  String S26_CAPTCHA = "https://echallan.tspolice.gov.in/publicview/GetCaptcha?ctrl=new&_csrfToeknVehNo=%s&t=%s";
    private static String OCR_URL = "http://internal-pvt-carinfo-prd-alb-602286310.ap-south-1.elb.amazonaws.com/ts-captcha";

    public String getData(String vehicleNum) throws Exception {
        S26Context s26Context = new S26Context();
        s26Context.setVehicleNum(vehicleNum);
        extractCookiesAndCrsf(s26Context);
        extractCaptcha(s26Context);
        String html = callGetChallanApi(s26Context);
        String challans = parseChallans(html);
        s26Context.setChallanInfos(challans);
        System.out.println(html);
        return MapperUtils.convertObjectToString(s26Context);

    }

    public String parseChallans(String html) {
        List<ChallanInfo> challans = new ArrayList<>();

        if (html == null || html.trim().isEmpty()) {
            return "empty";
        }

        if (html.contains("NoPendingChallans")) {
            return "No Pending Challans";
        }

        Document doc = Jsoup.parse(html);

        Element table = doc.getElementById("rtable");
        if (table == null) {
            return "empty";
        }

        Elements rows = table.select("tr");

        for (Element row : rows) {
            Elements cols = row.select("td");

            if (cols.size() < 12) continue;

            try {
                ChallanInfo ci = new ChallanInfo();

                ci.setUnit(cols.get(2).text().trim());
                ci.setEchallanNo(cols.get(3).text().trim());
                ci.setDate(cols.get(4).text().trim());
                ci.setTime(cols.get(5).text().trim());
                ci.setPlace(cols.get(6).text().trim());
                ci.setPsLimits(cols.get(7).text().trim());

                Element violTable = cols.get(8).selectFirst("table");
                if (violTable != null) {
                    Element violRow = violTable.selectFirst("tr");
                    if (violRow != null) {
                        Elements violTds = violRow.select("td");
                        if (violTds.size() >= 2) {
                            ci.setViolation(violTds.get(0).text().trim());
                            ci.setFineAmount(violTds.get(1).text().trim());
                        }
                    }
                }

                ci.setUserCharges(cols.get(10).text().trim());
                ci.setTotalFine(cols.get(11).text().trim());

                // Image column last → find image URL
                Element imgButton = cols.get(12).selectFirst("input[type=button]");
                if (imgButton != null) {
                    String onClick = imgButton.attr("onclick");
                    // Extract URL inside getDetailsForm('URL','1')
                    String url = onClick.replace("getDetailsForm('", "")
                            .replace("')", "")
                            .split("'")[0];
                    ci.setImageUrl(url);
                }

                challans.add(ci);

            } catch (Exception ex) {
                System.out.println("Failed to parse row: " + ex.getMessage());
            }
        }
        System.out.println(challans);

        return MapperUtils.convertObjectToString(challans);
    }

    private String callGetChallanApi(S26Context s26Context) {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");
        RequestBody body = RequestBody.create(mediaType, "ctrl=tab1&obj="+s26Context.getVehicleNum()+"&obj1=&put="+s26Context.getCaptcha()+"&_csrfToeknVehNo="+s26Context.getCrsfToken());
        Request request = new Request.Builder()
                .url(S26_URL)
                .method("POST", body)
                .addHeader("accept", "*/*")
                .addHeader("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                .addHeader("origin", "https://echallan.tspolice.gov.in")
                .addHeader("referer", "https://echallan.tspolice.gov.in/publicview/")
                .addHeader("Cookie", s26Context.getCookies())
                .build();
        try (Response response = OkHttpUtils.getOkHttpClient(700).newCall(request).execute()) {
            if (response.isSuccessful()){
                return response.body().string();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    private void extractCaptcha(S26Context s26Context) {
        String url = String.format(S26_CAPTCHA,s26Context.getCrsfToken(),System.currentTimeMillis());
        Request request = new Request.Builder().url(url)
                .method("GET", null)
                .addHeader("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .addHeader("referer", "https://echallan.tspolice.gov.in/publicview/")
                .addHeader("Cookie", s26Context.getCookies())
                .build();
        try (Response response = OkHttpUtils.getOkHttpClient(700).newCall(request).execute()) {
            if (response.isSuccessful()){
                getCaptcha(s26Context,response.body().bytes());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void getCaptcha(S26Context s26Context, byte[] captchaBytes) throws IOException {
        RequestBody requestBody = RequestBody.create(
                captchaBytes,
                MediaType.parse("application/octet-stream")
        );

        Request request = new Request.Builder()
                .url(OCR_URL)
                .post(requestBody)
                .build();
        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(request).execute();

        assert response.body() != null;
        String fastApiJson = response.body().string();
        Map ocrOut = new ObjectMapper().readValue(fastApiJson, Map.class);
        System.out.println(ocrOut);
        Map extractedCaptcha = (Map) ocrOut.get("extracted_text");
        s26Context.setCaptchaText(extractedCaptcha.get("expression").toString());
        md5Hash((String) extractedCaptcha.get("answer"), s26Context);
    }


    private static void extractCookiesAndCrsf(S26Context s26Context) throws IOException {
        String url = S26_URL+"?ctrl=sess&req=https://echallan.tspolice.gov.in/publicview/";
        Request request = new Request.Builder().url(url)
                .method("GET", null)
                .build();
        try (Response response = OkHttpUtils.getOkHttpClient(700).newCall(request).execute()) {
            long startTime = System.currentTimeMillis();
            List<String> cookieHeaders = Arrays.stream(response.headers().values("Set-Cookie").toArray(new String[0])).toList();
            String jsessionId = cookieHeaders.stream()
                    .filter(c -> c.startsWith("JSESSIONID"))
                    .map(c -> c.split(";", 2)[0])
                    .findFirst()
                    .orElse(null);
            if(response.code() == 200){
                s26Context.setCrsfToken(response.body().string());
            }

            System.out.println("JSESSIONID: " + jsessionId);

            s26Context.setCookies(jsessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    public static void md5Hash(String input,S26Context s26Context) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            s26Context.setCaptcha(sb.toString());

        } catch (Exception e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

}