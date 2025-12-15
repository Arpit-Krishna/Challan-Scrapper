package org.challan.challan_scraper.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WbChallanDTO {
    private String caseNumber;
    private String fine;
    private String status;
    private String offense;
    private String place;
    private String caseType;
    private String caseDate;
    private String image;

    @Override
    public String toString() {
        return caseNumber + " | " + fine + " | " + status +
                " | " + offense + " | " + place + " | " +
                caseType + " | " + caseDate + " | " + image;
    }
}
