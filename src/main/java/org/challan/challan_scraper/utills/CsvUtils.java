package org.challan.challan_scraper.utills;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CsvUtils {

    public static List<Map<String, String>> readCsv(String csvPath) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(csvPath));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord rec : parser) {
                Map<String,String> m = new HashMap<>();
                for (String h : parser.getHeaderNames()) {
                    m.put(h, rec.get(h));
                }
                rows.add(m);
            }
        }
        return rows;
    }

    public static File writeCsv(String outputPath, List<Map<String, String>> rows) throws IOException {
        if (rows == null || rows.isEmpty()) return null;
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        Path p = Paths.get(outputPath);
        try (BufferedWriter writer = Files.newBufferedWriter(p);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))) {
            for (Map<String,String> row : rows) {
                List<String> rec = new ArrayList<>();
                for (String h : headers) rec.add(row.getOrDefault(h, ""));
                printer.printRecord(rec);
            }
        }
        return p.toFile();
    }
}
