package org.challan.challan_scraper.DTO;

import lombok.Data;

@Data
public class WebSourceContext {
    private String vehicleNum;
    private String cookie;
    private String viewState;
    private String stateCode;
    private String opCode;
    private String goButton;

    public WebSourceContext(String vehicleNum) {
        this.vehicleNum = vehicleNum;
    }
}
