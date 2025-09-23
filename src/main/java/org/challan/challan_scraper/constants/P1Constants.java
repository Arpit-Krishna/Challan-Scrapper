package org.challan.challan_scraper.constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class P1Constants {
    public static final String P1_HOMEPAGE_URL =
            "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollection.xhtml";
    public static final String P1_TAX_ODC_URL =
            "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionOnlineOdc.xhtml";
    public static final String P1_TAX_COL_URL =
            "https://checkpost.parivahan.gov.in/checkpost/faces/public/payment/TaxCollectionMainOnline.xhtml";

    public static final List<String> stateCodes
            = Arrays.asList(
            "BR", "CG", "GA", "GJ", "HR", "HP", "JK", "JH", "KA", "KL", "MP",
            "MH", "OR", "PB", "RJ", "SK", "TN", "TR", "UP", "UK", "WB"
    );
    public static final HashMap<String, String> opCode = new HashMap<>() {{
        put("BR", "5003"); // BIHAR
        put("CG", "5007"); // CHHATTISGARH
        put("GA", "5020"); // GOA
        put("GJ", "5007"); // GUJARAT
        put("HR", "5003"); // HARYANA
        put("HP", "5003"); // HIMACHAL PRADESH
        put("JK", "5003"); // JAMMU & KASHMIR
        put("JH", "5003"); // JHARKHAND
        put("KA", "5003"); // KARNATAKA
        put("KL", "5003"); // KERALA
        put("MP", "5003"); // MADHYA PRADESH
        put("MH", "5007"); // MAHARASHTRA
        put("OR", "5003"); // ODISHA
        put("PB", "5003"); // PUNJAB
        put("RJ", "5003"); // RAJASTHAN
        put("SK", "5003"); // SIKKIM
        put("TN", "5003"); // TAMIL NADU
        put("TR", "5003"); // TRIPURA
        put("UP", "5003"); // UTTAR PRADESH
        put("UK", "5003"); // UTTRAKHAND
        put("WB", "5003"); // WEST BENGAL
    }};

    public static final List<String> FIELD_LIST = Arrays.asList(
            "Vehicle No.", "Vehicle Type", "Chassis No.", "Owner Name", "Owner/Firm Name", "Vehicle Permit Type",
            "Mobile No.", "From State", "Vehicle Class", "GVW (In Kg.)", "Gross Vehicle Weight(In Kg.)","Gross Vehicle Wt.(In Kg.)", "Unladen Weight(In Kg.)",
            "Load Carrying Capacity of Vehicle(In Kg.)", "Road Tax Validity",
            "Insurance Validity", "Fitness Validity", "PUCC Validity", "Seating Capacity (Excluding Driver)",
            "Registration Date", "Address", "Seating Cap(Ex. Driver)", "Seating cap ",
            "Sale Amount", "Fuel", "Cubic Cap(CC)"
    );

}