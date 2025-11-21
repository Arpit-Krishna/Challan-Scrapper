package org.challan.challan_scraper.DTO;

import lombok.Data;
import org.bytedeco.opencv.presets.opencv_core;

@Data
public class S26Context {

    private String vehicleNum;
    private String cookies;
    private String crsfToken;
    private String captcha;
    private String captchaText;
    private String ChallanInfos;

}
