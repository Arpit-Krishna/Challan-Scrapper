package org.challan.challan_scraper.services;

import org.challan.challan_scraper.utills.SSLUtills;
import org.jsoup.Jsoup;

import java.util.HashMap;
import java.util.Map;

public class ChallanServices {
    public String fetchChallanHtml(String vehicleNumber) {
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
}
