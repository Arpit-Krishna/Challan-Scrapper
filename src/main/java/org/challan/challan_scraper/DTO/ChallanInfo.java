package org.challan.challan_scraper.DTO;

import lombok.Data;

@Data
public class ChallanInfo {
    private String unit;
    private String echallanNo;
    private String date;
    private String time;
    private String place;
    private String psLimits;
    private String violation;
    private String fineAmount;
    private String userCharges;
    private String totalFine;
    private String imageUrl;
}
