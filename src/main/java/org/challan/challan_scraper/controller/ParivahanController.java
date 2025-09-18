package org.challan.challan_scraper.controller;

import org.challan.challan_scraper.DTO.VehicleDetails;
import org.challan.challan_scraper.services.ChallanServices;
import org.challan.challan_scraper.services.ParivahanServicies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/privahan")
public class ParivahanController {

    private final ParivahanServicies services;

    @Autowired
    public ParivahanController(ParivahanServicies services) {
        this.services = services;
    }

    @PostMapping("/mh/odc/{vehicleNum}")
    public ResponseEntity<Map<String, String>> fetchVehicle(@PathVariable String vehicleNum) throws Exception {
        try {
            Map<String, String> details = services.getVehicleDetails(vehicleNum);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
