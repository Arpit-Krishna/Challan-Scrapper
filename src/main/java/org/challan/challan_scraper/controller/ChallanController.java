package org.challan.challan_scraper.controller;


import org.challan.challan_scraper.services.ChallanServices;
import org.challan.challan_scraper.utills.ChallanParser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChallanController {
    private final ChallanServices challanService = new ChallanServices();

    @GetMapping("/rajkot")
    public List<Map<String, String>> getChallans(@RequestParam("vehicleNumber") String vehicleNumber) {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("vehicleNumber is required");
        }

        return ChallanParser.parseChallans(challanService.fetchChallanHtml(vehicleNumber));
    }
}


