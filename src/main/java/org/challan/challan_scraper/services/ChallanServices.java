package org.challan.challan_scraper.services;

import okhttp3.*;
import org.challan.challan_scraper.utills.SSLUtills;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallanServices {
    private final OkHttpClient client = SSLUtills.getUnsafeOkHttpClient();
    public String fetchChallanHtml(String vehicleNumber) {
        String url = "https://rajkotcitypolice.co.in";

        try {
            Request getRequest = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Response getResponse = client.newCall(getRequest).execute();
            if (!getResponse.isSuccessful()) {
                throw new IOException("GET failed: " + getResponse);
            }

            // Extract cookies
            Headers headers = getResponse.headers();
            List<String> cookies = headers.values("Set-Cookie");
            String cookieHeader = String.join("; ", cookies);

            String html = getResponse.body().string();
            Document doc = Jsoup.parse(html);

            String viewState = doc.select("input[name=__VIEWSTATE]").attr("value");
            String eventValidation = doc.select("input[name=__EVENTVALIDATION]").attr("value");
            String viewStateGen = doc.select("input[name=__VIEWSTATEGENERATOR]").attr("value");

            getResponse.close();

            FormBody formBody = new FormBody.Builder()
                    .add("__VIEWSTATE", viewState)
                    .add("__VIEWSTATEGENERATOR", viewStateGen)
                    .add("__EVENTVALIDATION", eventValidation)
                    .add("ctl00$ContentPlaceHolder1$txtVehicleNo", vehicleNumber)
                    .add("ctl00$ContentPlaceHolder1$btnSubmit", " Go !")
                    .build();

            Request postRequest = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Cookie", cookieHeader)
                    .build();

            try (Response postResponse = client.newCall(postRequest).execute()) {
                if (!postResponse.isSuccessful()) {
                    throw new IOException("POST failed: " + postResponse);
                }
                return postResponse.body().string();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch challans for vehicle " + vehicleNumber, e);
        }
    }

    public String fetchChallanHtmlJsoup(String vehicleNumber) {
        String url = "https://rajkotcitypolice.co.in/";

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Cookie", "ASP.NET_SessionId=0zpg214jpqmc4ybmtpmhtwcu; cookiesession1=678A8C325F6FB904D4A5D69AFBFAC662");

        // Form data
        Map<String, String> data = new HashMap<>();
        data.put("__VIEWSTATE", "/wEPDwULLTExMjY0MTQ4NzYPZBYCZg9kFgICAw9kFgICAQ9kFgQCAw8WAh4FY2xhc3MFHGNvbC1tZC00IGhvbWVwYWdlLWJsb2NrLWxlZnRkAgUPZBYCAgEPFgIeB1Zpc2libGVnFgoCAQ8PFgIeBFRleHQFCkdKMjdEVTc1NjZkZAIDDxYCHgtfIUl0ZW1Db3VudAL/////D2QCBQ8WAh4IZGlzYWJsZWQFCGRpc2FibGVkFggCAQ8PFgIfAmVkZAIHDw8WAh8CZWRkAgsPDxYCHwIFATBkZAIND2QWAgIDDxQrAAVkKClYU3lzdGVtLkd1aWQsIG1zY29ybGliLCBWZXJzaW9uPTQuMC4wLjAsIEN1bHR1cmU9bmV1dHJhbCwgUHVibGljS2V5VG9rZW49Yjc3YTVjNTYxOTM0ZTA4OSRiMjYwZjJkYS0yZmYyLTQ1YTAtOGU5Mi02NWUzNjhlMjRhOTQCARQrAAE8KwAEAQBmZGQCBw8WAh8BZ2QCCQ9kFgICAQ8WAh8DAv////8PZGRzWvc8A7gJJ9YmooXqRBoR4DFgeUYq+nUcAqDTVpIRVw==");
        data.put("__VIEWSTATEGENERATOR", "90059987");
        data.put("__EVENTVALIDATION", "/wEdAAMVhaPGgSO7tZn90y8RCaLeG5I12yP+Ggz6F+R8DW1Jx80fuEgtH2WBdB9YI/l4FYtVpWBNGFFqoxHX0OI/FuX0BXpW0mVd2KHHkhCUdhOT1g==");
        data.put("ctl00$ContentPlaceHolder1$txtVehicleNo", vehicleNumber);
        data.put("ctl00$ContentPlaceHolder1$btnSubmit", " Go !");

        try {
            // Jsoup POST request
            SSLUtills.disableSSLVerification();

            return Jsoup.connect(url)
                    .timeout(15000)
                    .headers(headers)
                    .data(data)
                    .method(org.jsoup.Connection.Method.POST)
                    .ignoreContentType(true)
                    .execute()
                    .body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch challans for vehicle " + vehicleNumber, e);
        }
    }

    public String fetchAhmedabadChallanHtml(String vehicleNumber) {
        String BASE_URL = "https://www.payahmedabadechallan.org/";

        try {
            Request getRequest = new Request.Builder()
                    .url(BASE_URL)
                    .get()
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            Response getResponse = client.newCall(getRequest).execute();
            if (!getResponse.isSuccessful()) {
                throw new IOException("GET failed: " + getResponse);
            }

            Headers headers = getResponse.headers();
            List<String> cookies = headers.values("Set-Cookie");
            String cookieHeader = String.join("; ", cookies);

            String html = getResponse.body().string();
            Document doc = Jsoup.parse(html);

            String viewState = doc.select("input[id=__VIEWSTATE]").attr("value");
            String viewStateGen = doc.select("input[id=__VIEWSTATEGENERATOR]").attr("value");
            String eventValidation = doc.select("input[id=__EVENTVALIDATION]").attr("value");
            String captchaAns = doc.select("input[id=ContentPlaceHolder1_hdnCaptchaAns]").attr("value");

            getResponse.close();

            FormBody formBody = new FormBody.Builder()
                    .add("__LASTFOCUS", "")
                    .add("__EVENTTARGET", "")
                    .add("__EVENTARGUMENT", "")
                    .add("__VIEWSTATE", viewState)
                    .add("__VIEWSTATEGENERATOR", viewStateGen)
                    .add("__EVENTVALIDATION", eventValidation)
                    .add("ctl00$ContentPlaceHolder1$txtVehicleNo", vehicleNumber)
                    .add("ctl00$ContentPlaceHolder1$hdfTxn", "0")
                    .add("ctl00$ContentPlaceHolder1$txtCaptcha", captchaAns)
                    .add("ctl00$ContentPlaceHolder1$hdnCaptchaAns", captchaAns)
                    .add("ctl00$ContentPlaceHolder1$btnSubmit", " GO !")
                    .build();

            Request postRequest = new Request.Builder()
                    .url(BASE_URL)
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cookie", cookieHeader)
                    .build();

            try (Response postResponse = client.newCall(postRequest).execute()) {
                if (!postResponse.isSuccessful()) {
                    throw new IOException("POST failed: " + postResponse);
                }
                return postResponse.body().string();
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch Ahmedabad challans for vehicle " + vehicleNumber, e);
        }
    }

    public static void main(String[] args) {
        ChallanServices service = new ChallanServices();
        String html = service.fetchAhmedabadChallanHtml("GJ03JN3842");
        System.out.println(html);
    }
}
