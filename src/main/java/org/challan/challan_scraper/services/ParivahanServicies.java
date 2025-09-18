package org.challan.challan_scraper.services;

import org.challan.challan_scraper.DTO.VehicleDetails;
import org.challan.challan_scraper.utills.MhOdcScrapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ParivahanServicies {
    private final MhOdcScrapper scraper;

    public ParivahanServicies(MhOdcScrapper scraper) {
        this.scraper = scraper;
    }

    public Map<String, String> getVehicleDetails(String vehicleNum) throws Exception {
        return scraper.fetchVehicleDetails(vehicleNum);
    }
}
