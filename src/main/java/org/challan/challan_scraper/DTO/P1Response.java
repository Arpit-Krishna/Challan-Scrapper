package org.challan.challan_scraper.DTO;
import lombok.Data;

@Data
public class P1Response {
    private int status;
    private String message;
    private P1Data data;
    private String source;
}