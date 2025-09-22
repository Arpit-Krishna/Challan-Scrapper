package org.challan.challan_scraper.controller;

import org.challan.challan_scraper.DTO.VehicleDetails;
import org.challan.challan_scraper.services.ChallanServices;
import org.challan.challan_scraper.services.P1Client;
import org.challan.challan_scraper.services.ParivahanServicies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/privahan")
public class ParivahanController {

    private final ParivahanServicies services;
    @Autowired
    private final P1Client p1Client;

    @Autowired
    public ParivahanController(ParivahanServicies services,  P1Client p1Client) {
        this.services = services;
        this.p1Client = p1Client;
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

    @PostMapping("/mh/p1odc/{vehicleNum}")
    public ResponseEntity<String> fetchP1Vehicle(@PathVariable String vehicleNum) throws Exception {
        try {
            String response = p1Client.getData(vehicleNum, "MH", "5007");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("error" + e.getMessage());
        }
    }

    @PostMapping("/od/tax/{vehicleNum}")
    public ResponseEntity<String> fetchOdData(@PathVariable String vehicleNum) throws Exception {
        try {
            String response = p1Client.getData(vehicleNum, "OR", "5003");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("error" + e.getMessage());
        }
    }
}
