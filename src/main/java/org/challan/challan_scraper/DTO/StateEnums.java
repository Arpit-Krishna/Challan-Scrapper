package org.challan.challan_scraper.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.challan.challan_scraper.constants.P1Constants;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Getter
@AllArgsConstructor
public enum StateEnums {
    BR("BR", "5003", P1Constants.P1_TAX_COL_URL, "brtaxcollection"), // BIHAR
    CG("CG", "5007", P1Constants.P1_TAX_ODC_URL, "taxcollodc"), // CHHATTISGARH
    GA("GA", "5020", P1Constants.P1_TAX_COL_URL, "gataxcollection"), // GOA
    GJ("GJ", "5007", P1Constants.P1_TAX_ODC_URL, "taxcollodc"), // GUJARAT
    HR("HR", "5003", P1Constants.P1_TAX_COL_URL, "hrtaxcollection"), // HARYANA
    HP("HP", "5003", P1Constants.P1_TAX_COL_URL, "hptaxcollection"), // HIMACHAL PRADESH
    JK("JK", "5003", P1Constants.P1_TAX_COL_URL, "jktaxcollection"), // JAMMU & KASHMIR
    JH("JH", "5003", P1Constants.P1_TAX_COL_URL, "jhtaxcollection"), // JHARKHAND
    KA("KA", "5003", P1Constants.P1_TAX_COL_URL, "kataxcollection"), // KARNATAKA
    KL("KL", "5003", P1Constants.P1_TAX_COL_URL, "kltaxcollection"), // KERALA
    MH("MH", "5007", P1Constants.P1_TAX_ODC_URL, "taxcollodc"), // MAHARASHTRA
    OR("OR", "5003", P1Constants.P1_TAX_COL_URL, "ortaxcollection"), // ODISHA
    PB("PB", "5003", P1Constants.P1_TAX_COL_URL, "pbtaxcollection"), // PUNJAB
    RJ("RJ", "5003", P1Constants.P1_TAX_COL_URL, "rjtaxcollection"), // RAJASTHAN
    SK("SK", "5003", P1Constants.P1_TAX_COL_URL, "sktaxcollection"), // SIKKIM
    TR("TR", "5003", P1Constants.P1_TAX_COL_URL, "trtaxcollection"), // TRIPURA
    UP("UP", "5003", P1Constants.P1_TAX_COL_URL, "uptaxcollection"), // UTTAR PRADESH
    UK("UK", "5003", P1Constants.P1_TAX_COL_URL, "uktaxcollection"), // UTTARAKHAND
    WB("WB", "5003", P1Constants.P1_TAX_COL_URL, "wbtaxcollection"); // WEST BENGAL


    private static final Random RANDOM = new Random();

    private final String stateCode;
    private final String opCode;
    private final String url;
    private final String renderState;

    public static StateEnums pickRandom() {
        StateEnums[] values = StateEnums.values();
        return values[RANDOM.nextInt(values.length)];
    }


    public static String randomStateCode() {
        StateEnums[] values = StateEnums.values();
        List<StateEnums> filtered = Arrays.stream(values)
                .filter(state -> !"KL".equals(state.getStateCode())) // exclude KL
                .toList();

        return filtered.get(RANDOM.nextInt(filtered.size())).getStateCode();
    }

    public static StateEnums fromStateCode(String code) {
        for (StateEnums config : values()) {
            if (StringUtils.equalsIgnoreCase(config.stateCode,code)) {
                return config;
            }
        }
        return null;
    }
}