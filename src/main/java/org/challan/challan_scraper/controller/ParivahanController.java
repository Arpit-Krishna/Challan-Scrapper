package org.challan.challan_scraper.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.challan.challan_scraper.DTO.P1Data;
import org.challan.challan_scraper.DTO.VehicleDetails;
import org.challan.challan_scraper.services.ChallanServices;
import org.challan.challan_scraper.services.P1;
import org.challan.challan_scraper.services.P1Client;
import org.challan.challan_scraper.services.ParivahanServicies;
import org.challan.challan_scraper.utills.MapperUtils;
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

    @Autowired
    private final P1Client p1Client;
    @Autowired
    private final P1 p1;

    @Autowired
    public ParivahanController(P1Client p1Client, P1 p1) {
        this.p1Client = p1Client;
        this.p1 = p1;
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
                String response = p1.getData(vehicleNum, stateCode);
                responses.add("[" + stateCode + "] " + response);
            } catch (Exception e) {
                responses.add("[" + stateCode + "] ERROR: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/rel/{vehicleNum}")
    public ResponseEntity<String> fetchRelBulk(@PathVariable String vehicleNum) throws Exception {
        P1Data mergedData = new P1Data();
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> stateCodes = List.of("MH", "KL", "OR");

        for (String stateCode : stateCodes) {
            try {
                String rawResponse = p1Client.getData(vehicleNum, stateCode);
                JsonNode rootNode = objectMapper.readTree(rawResponse);
                JsonNode dataNode = rootNode.path("data");

                P1Data stateData = objectMapper.treeToValue(dataNode, P1Data.class);
                P1Client.mergeP1Data(mergedData, stateData);

            } catch (Exception e) {
                System.err.println("Error fetching data from " + stateCode + ": " + e.getMessage());
            }
        }

        String finalResponse = MapperUtils.convertObjectToString(mergedData);

        return ResponseEntity.ok(finalResponse);

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
