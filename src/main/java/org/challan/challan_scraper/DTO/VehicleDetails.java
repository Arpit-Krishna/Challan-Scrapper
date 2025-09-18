package org.challan.challan_scraper.DTO;
import java.util.Map;


public class VehicleDetails {
    private Map<String, String> fields;

    public VehicleDetails(Map<String, String> fields) {
        this.fields = fields;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }
}
