package org.challan.challan_scraper.DTO;
import lombok.Data;

@Data   // Lombok or generate getters/setters
public class VehicleDetails {
    private String vehicleNo;
    private String vehicleType;
    private String chassisNo;
    private String ownerName;
    private String vehicleClass;
    private String gvw;
    private String unladenWeight;
    private String loadCapacity;
    private String roadTaxValidity;
    private String insuranceValidity;
    private String fitnessValidity;
    private String puccValidity;
    private String registrationDate;
    private String address;
}