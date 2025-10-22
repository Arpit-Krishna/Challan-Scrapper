package org.challan.challan_scraper.utills;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallanParser {
    public static List<Map<String, String>> parseChallans(String html) {
        List<Map<String, String>> challans = new ArrayList<>();

        Document doc = Jsoup.parse(html);

        Elements challanSections = doc.select("section.challan-list");

        for (Element section : challanSections) {
            Map<String, String> challan = new HashMap<>();

            challan.put("date", section.select("span[id*=lblNoticeDate]").text());
            challan.put("noticeNumber", section.select("span[id*=lblNoticeNo]").text());
            challan.put("amount", section.select("span[id*=lblAmount]").text());
            challan.put("violationType", section.select("span[id*=lblViolationType]").text());
            challan.put("place", section.select("span[id*=lblPlace]").text());

            challans.add(challan);
        }
        if(html.contains("No Records Found!")) {
            Map<String, String> datafound = new HashMap<>();
            datafound.put("Data Found", "False");
            challans.add(datafound);
        }
        else{
            Map<String, String> datafound = new HashMap<>();
            datafound.put("Data Found", "True");
            challans.add(datafound);
        }

        return challans;
    }
}
