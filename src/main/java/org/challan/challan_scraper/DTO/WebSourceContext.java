package org.challan.challan_scraper.DTO;

import lombok.Data;

@Data
public class WebSourceContext {
    private String vehicleNum;
    private String cookie;
    private String viewState;
    private String stateName;
    private String chassis;

    public WebSourceContext(String vehicleNum) {
        this.vehicleNum = vehicleNum;
    }

    public WebSourceContext(String vehicleNum, String chassis) {
        this.vehicleNum = vehicleNum;
        this.chassis = chassis;
    }
}