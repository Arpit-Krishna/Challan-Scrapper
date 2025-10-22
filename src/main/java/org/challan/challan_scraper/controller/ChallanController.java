package org.challan.challan_scraper.controller;


import org.challan.challan_scraper.services.ChallanServices;
import org.challan.challan_scraper.utills.ChallanParser;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/state")
public class ChallanController {
    private final ChallanServices challanService = new ChallanServices();

    @GetMapping("/rajkot")
    public List<Map<String, String>> getRajkotChallans(@RequestParam("vehicleNumber") String vehicleNumber) {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("vehicleNumber is required");
        }

        return ChallanParser.parseChallans(challanService.fetchChallanHtml(vehicleNumber));
    }

    @GetMapping("/ahmedabad")
    public List<Map<String, String>> getAhmedabadChallans(@RequestParam("vehicleNumber") String vehicleNumber) {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("vehicleNumber is required");
        }
        try {
            return ChallanParser.parseChallans(challanService.fetchAhmedabadChallanHtml(vehicleNumber));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/rajkot/bulk")
    public Map<String, List<Map<String, String>>> getRajkotChallansBulk(@RequestBody List<String> vehicleNumbers) {
        if (vehicleNumbers == null || vehicleNumbers.isEmpty()) {
            throw new IllegalArgumentException("vehicleNumbers list is required");
        }
        int len = vehicleNumbers.size();
        int count = 0;
        Map<String, List<Map<String, String>>> response = new HashMap<>();
        for (String v : vehicleNumbers) {
            if (v != null && !v.isBlank()) {
                List<Map<String, String>> temp = ChallanParser.parseChallans(challanService.fetchChallanHtml(v));
                int len1 = temp.size();
                count += temp.get(len1-1).get("Data Found").equals("True") ? 1 : 0;
                response.put(v, temp);
            }
        }
        System.out.println("Total request: " + len);
        System.out.println("Data Found: " + count);
        return response;
    }

    @PostMapping("/ahmedabad/bulk")
    public Map<String, List<Map<String, String>>> getAhmedabadChallansBulk(@RequestBody List<String> vehicleNumbers) {
        if (vehicleNumbers == null || vehicleNumbers.isEmpty()) {
            throw new IllegalArgumentException("vehicleNumbers list is required");
        }

        Map<String, List<Map<String, String>>> response = new HashMap<>();
        for (String v : vehicleNumbers) {
            if (v != null && !v.isBlank()) {
                try {
                    response.put(v, ChallanParser.parseChallans(challanService.fetchAhmedabadChallanHtml(v)));
                } catch (Exception e) {
                    response.put(v, Collections.emptyList());
                }
            }
        }
        return response;
    }
}


