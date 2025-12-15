package org.challan.challan_scraper.services;

import okhttp3.*;
import org.challan.challan_scraper.DTO.WbChallanDTO;
import org.challan.challan_scraper.utills.MapperUtils;
import org.challan.challan_scraper.utills.SSLUtills;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

public class WbChallanService {

    private final OkHttpClient client = SSLUtills.getUnsafeOkHttpClient();

    private static final String URL =
            "https://sanjog.wb.gov.in/FetchProviderWisePendingChallanUser";

    /**
     * Fetch raw HTML for West Bengal challan page
     */
    public String fetchWbChallanHtml(String vehicleNumber) {

        String payload = "VehicleNumber=" + vehicleNumber;

        RequestBody body = RequestBody.create(
                payload,
                MediaType.parse("application/x-www-form-urlencoded")
        );

        Request req = new Request.Builder()
                .url(URL)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response response = client.newCall(req).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("WB Challan POST failed: " + response);
            }
            assert response.body() != null;
            return response.body().string();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to fetch WB challans for vehicle " + vehicleNumber, e
            );
        }
    }


    /**
     * Extract challan objects from HTML using Regex
     */
    public List<WbChallanDTO> parseWbChallans(String html) {

        List<WbChallanDTO> list = new ArrayList<>();

        // Same regex pattern as Python version
        String regex =
                "var caseNumber = \"([^\"]+)\";\\s*" +
                        "var fineAmount = \"([^\"]+)\";\\s*" +
                        "var status = \"([^\"]+)\";.*?" +
                        "var offence = \"([^\"]+)\";\\s*" +
                        "var place = \"([^\"]+)\";\\s*" +
                        "var caseType = \"([^\"]+)\";\\s*" +
                        "var caseDate = \"([^\"]+)\";\\s*" +
                        "var image = \"([^\"]*)\";";

        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(html);

        while (m.find()) {
            WbChallanDTO c = new WbChallanDTO();

            c.setCaseNumber(m.group(1));

            String fine = m.group(2);
            String status = m.group(3);

            if ("Court".equalsIgnoreCase(status)) {
                fine = fine + " (Pending in court)";
            }

            c.setFine(fine);
            c.setStatus(status);
            c.setOffense(m.group(4));
            c.setPlace(m.group(5));
            c.setCaseType(m.group(6));
            c.setCaseDate(m.group(7));
            c.setImage(m.group(8));

            list.add(c);
        }

        return list;
    }

    public List<WbChallanDTO> getData(String vehicleNumber) {
        WbChallanService wb = new WbChallanService();
        String html = wb.fetchWbChallanHtml(
                vehicleNumber
        );
        List<WbChallanDTO> challans = wb.parseWbChallans(html);
        System.out.println("Total Challans: " + challans.size());
        return challans;
//        return MapperUtils.convertObjectToString(challans);

    }


    public static void main(String[] args) {
        WbChallanService wb = new WbChallanService();

        String html = wb.fetchWbChallanHtml(
                "WB76A6559"
        );

        List<WbChallanDTO> challans = wb.parseWbChallans(html);

        System.out.println("Total Challans: " + challans.size());
        challans.forEach(System.out::println);
    }
}
