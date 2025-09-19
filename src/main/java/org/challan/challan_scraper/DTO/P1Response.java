package org.challan.challan_scraper.DTO;
import lombok.Data;

@Data
public class P1Response {
    private int status;
    private String message;
    private P1Data data;
    private String source;

    public P1Response() {
    }
    public P1Response(int status, String message, P1Data data, String source) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.source = source;
    }
}