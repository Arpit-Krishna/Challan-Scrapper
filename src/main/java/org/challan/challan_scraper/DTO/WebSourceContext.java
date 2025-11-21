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
    private String MAIN_URL;
    private String updateTag;
    private String stateCd;

    public WebSourceContext(String vehicleNum) {
        this.vehicleNum = vehicleNum;
    }
}
