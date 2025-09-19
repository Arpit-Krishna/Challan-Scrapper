package org.challan.challan_scraper.DTO;

import lombok.Data;

@Data
public class WebSourceContext {
    private String vehicleNum;
    private String cookie;
    private String viewState;
    private String stateName;

    private String formName;
    private String stateField;
    private String operationField;
    private String goButton;

    public WebSourceContext(String vehicleNum) {
        this.vehicleNum = vehicleNum;
    }
}
