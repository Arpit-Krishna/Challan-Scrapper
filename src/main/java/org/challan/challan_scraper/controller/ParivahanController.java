package org.challan.challan_scraper.controller;

import org.challan.challan_scraper.DTO.VehicleDetails;
import org.challan.challan_scraper.services.ChallanServices;
import org.challan.challan_scraper.services.P1Client;
import org.challan.challan_scraper.services.ParivahanServicies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.challan.challan_scraper.constants.P1Constants.stateCodes;

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
    @PostMapping("/tax/{stateCode}/{vehicleNum}")
    public ResponseEntity<String> fetchData(@PathVariable String stateCode, @PathVariable String vehicleNum) throws Exception {
        try {
            if(!stateCodes.contains(stateCode.toUpperCase())){
                throw new Exception("Invalid state code");
            }
            String response = p1Client.getData(vehicleNum, stateCode.toUpperCase());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("error" + e.getMessage());
        }
    }
    @PostMapping("/bulk/{vehicleNum}")
    public ResponseEntity<List<String>> fetchDataBulk(@PathVariable String vehicleNum) throws Exception {
        List<String> responses = new ArrayList<>();
        for (String stateCode : stateCodes) {
            try {
                String response = p1Client.getData(vehicleNum, stateCode);
                responses.add("[" + stateCode + "] " + response);
            } catch (Exception e) {
                responses.add("[" + stateCode + "] ERROR: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(responses);
    }

//    @PostMapping("/mh/odc/{vehicleNum}")
//    public ResponseEntity<Map<String, String>> fetchVehicle(@PathVariable String vehicleNum) throws Exception {
//        try {
//            Map<String, String> details = services.getVehicleDetails(vehicleNum);
//            return ResponseEntity.ok(details);
//        } catch (Exception e) {
//            return ResponseEntity.status(500)
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
}
