package org.challan.challan_scraper.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import org.bytedeco.opencv.presets.opencv_core;
import org.challan.challan_scraper.DTO.S26Context;
import org.springframework.web.bind.annotation.*;

import org.challan.challan_scraper.services.OcrService;
import org.challan.challan_scraper.services.OcrService2;
import org.challan.challan_scraper.services.OcrService3;
import org.challan.challan_scraper.services.OcrService4;
import org.challan.challan_scraper.utills.CsvUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;



import java.io.File;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/ocr")
public class OcrController {

    @Autowired
    private OcrService ocrService;
    @Autowired
    private OcrService2 ocrService2;
    @Autowired
    private OcrService3 ocrService3;
    @Autowired
    private OcrService4 ocrService4;

    /**
     * Read text from a single image using the Python-equivalent OCR pipeline
     */

    @GetMapping("/tsch/bulk")
    public ResponseEntity<?> tsch_bulk(@org.springframework.web.bind.annotation.RequestBody List<String> vehicleNumbers) {
        try {
            List<String> responses = new ArrayList<>();
            for (String vehicleNumber : vehicleNumbers) {
                String response = ocrService4.getData(vehicleNumber);
                responses.add(response);
            }
            return new ResponseEntity<>(responses, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tsch")
    public ResponseEntity<?> tsch(@RequestParam String vehicleNum) {
        try {
            return ResponseEntity.ok(ocrService4.getData(vehicleNum));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/read")
    public ResponseEntity<?> readImage(@RequestParam String imageName) {
        try {
            String baseDir = System.getProperty("user.dir");
            String imagePath = baseDir + File.separator + "synthetic_arith" + File.separator + imageName;

            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Image not found: " + imagePath));
            }

//            String text = ocrService.extractText(imagePath);
//
//            return ResponseEntity.ok(Map.of(
//                    "image", imageName,
//                    "text", text,
//                    "path", imagePath
//            ));
            Map<String, String> result = ocrService3.processArithmeticImage(imagePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

//    /**
//     * Read and solve arithmetic expression from image
//     */
//    @GetMapping("/solve")
//    public ResponseEntity<?> solveImage(@RequestParam String imageName) {
//        try {
//            String baseDir = System.getProperty("user.dir");
//            String imagePath = baseDir + File.separator + "synthetic_arith" + File.separator + imageName;
//
//            File imageFile = new File(imagePath);
//            if (!imageFile.exists()) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(Map.of("error", "Image not found: " + imagePath));
//            }
//
//            Map<String, String> result = ocrService.ocrAndSolve(imagePath);
//
//            return ResponseEntity.ok(result);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }

    /**
     * Test OCR accuracy against a CSV file with ground truth labels
     * CSV format: filename,text,label
     */
    @GetMapping("/test")
    public ResponseEntity<?> testCsv(@RequestParam("csv") String csvPath) {
        try {
            File csvFile = new File("synthetic_arith/" + csvPath);
            if (!csvFile.exists()) {
                System.out.println(csvFile.getAbsolutePath());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "CSV file not found: " + csvPath));
            }

            List<Map<String, String>> rows = CsvUtils.readCsv("synthetic_arith/" + csvPath);
            List<Map<String, String>> results = new ArrayList<>();

            int correct = 0;
            int failed = 0;
            int total = rows.size();

            String baseDir = System.getProperty("user.dir");

            for (Map<String, String> row : rows) {
                // CSV expected headers: filename,text,label
                String filename = row.get("filename");
                String expectedExpr = row.get("text");
                String expectedLabel = row.get("label");

                // Build full path
                String imagePath = baseDir + File.separator + "synthetic_arith" + File.separator + filename;

                // Run OCR and solve
                Map<String, String> out = ocrService3.processArithmeticImage(imagePath);

                String predictedExpr = out.getOrDefault("expression", "");
                String predictedAnswer = out.getOrDefault("answer", "");
                String success = out.getOrDefault("success", "false");

                // Check if prediction matches expected label
                boolean match = predictedAnswer != null
                        && !predictedAnswer.isEmpty()
                        && predictedAnswer.equals(expectedLabel);

                if (match) {
                    correct++;
                }

                if (predictedAnswer == null || predictedAnswer.isEmpty()) {
                    failed++;
                }

                // Build result record
                Map<String, String> rec = new LinkedHashMap<>();
                rec.put("filename", filename);
                rec.put("expected_text", expectedExpr != null ? expectedExpr : "");
                rec.put("expected_label", expectedLabel != null ? expectedLabel : "");
                rec.put("predicted_text", predictedExpr);
                rec.put("predicted_label", predictedAnswer);
                rec.put("match", String.valueOf(match));
                rec.put("success", success);
                results.add(rec);
            }

            // Calculate metrics
            double accuracy = total == 0 ? 0.0 : (correct * 100.0) / total;
            double failureRate = total == 0 ? 0.0 : (failed * 100.0) / total;

            // Write results to CSV
            String outCsv = "ocr_results.csv";
            CsvUtils.writeCsv(outCsv, results);

            // Prepare response
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("total", total);
            resp.put("correct", correct);
            resp.put("incorrect", total - correct - failed);
            resp.put("failed_to_extract", failed);
            resp.put("accuracy", String.format("%.2f%%", accuracy));
            resp.put("failure_rate", String.format("%.2f%%", failureRate));
            resp.put("results_file", new File(outCsv).getAbsolutePath());
            resp.put("csv_path", csvPath);

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "stack_trace", e.toString()));
        }
    }

    /**
     * Batch process multiple images from a directory
     */
    @GetMapping("/batch")
    public ResponseEntity<?> batchProcess(@RequestParam(defaultValue = "synthetic_arith") String directory) {
        try {
            String baseDir = System.getProperty("user.dir");
            String dirPath = baseDir + File.separator + directory;

            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Directory not found: " + dirPath));
            }

            File[] imageFiles = dir.listFiles((d, name) ->
                    name.toLowerCase().endsWith(".png") ||
                            name.toLowerCase().endsWith(".jpg") ||
                            name.toLowerCase().endsWith(".jpeg")
            );

            if (imageFiles == null || imageFiles.length == 0) {
                return ResponseEntity.ok(Map.of(
                        "message", "No images found in directory",
                        "directory", dirPath
                ));
            }

            List<Map<String, String>> results = new ArrayList<>();
            int processed = 0;
            int successful = 0;

            for (File imageFile : imageFiles) {
                try {
                    Map<String, String> result = ocrService.ocrAndSolve(imageFile.getAbsolutePath());
                    result.put("filename", imageFile.getName());
                    results.add(result);
                    processed++;

                    if ("true".equals(result.get("success"))) {
                        successful++;
                    }
                } catch (Exception e) {
                    Map<String, String> errorResult = new HashMap<>();
                    errorResult.put("filename", imageFile.getName());
                    errorResult.put("error", e.getMessage());
                    errorResult.put("success", "false");
                    results.add(errorResult);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("directory", dirPath);
            response.put("total_images", imageFiles.length);
            response.put("processed", processed);
            response.put("successful", successful);
            response.put("success_rate", String.format("%.2f%%",
                    processed > 0 ? (successful * 100.0) / processed : 0.0));
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download the generated results CSV file
     */
//    @GetMapping("/download")
//    public ResponseEntity<Resource> downloadResult(@RequestParam("path") String path) {
//        try {
//            Path p = Paths.get(path);
//            if (!Files.exists(p)) {
//                return ResponseEntity.notFound().build();
//            }
//
//            Resource r = new UrlResource(p.toUri());
//            String fn = p.getFileName().toString();
//
//            return ResponseEntity.ok()
//                    .contentType(MediaType.parseMediaType("text/csv"))
//                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + "\"")
//                    .body(r);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "OCR Service",
                "method", "Python-equivalent preprocessing"
        ));
    }

    @GetMapping("/test-fastapi")
    public ResponseEntity<?> testFastApi(@RequestParam("csv") String csvFileName) {

        try {
            String baseDir = System.getProperty("user.dir");
            String csvPath = baseDir + File.separator + "synthetic_arith" + File.separator + csvFileName;

            File csvFile = new File(csvPath);
            if (!csvFile.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "CSV not found: " + csvPath));
            }

            List<Map<String, String>> rows = CsvUtils.readCsv(csvPath);
            List<Map<String, Object>> results = new ArrayList<>();

            int correct = 0;
            int failed = 0;
            int total = rows.size();

            OkHttpClient client = new OkHttpClient();

            for (Map<String, String> row : rows) {

                String filename = row.get("filename");
                String expectedText = row.get("text");
                String expectedAnswer = row.get("label");

                String imagePath = baseDir + File.separator + "synthetic_arith" + File.separator + filename;
                File imageFile = new File(imagePath);

                if (!imageFile.exists()) {
                    failed++;
                    results.add(Map.of(
                            "filename", filename,
                            "error", "Image not found"
                    ));
                    continue;
                }

                // ------------ CALL FASTAPI OCR SERVICE ------------
                RequestBody requestBody = RequestBody.create(
                        imageFile,
                        MediaType.parse("application/octet-stream")
                );

                Request request = new Request.Builder()
                        .url("http://localhost:8000/ocr/bytes")
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();

                assert response.body() != null;
                String fastApiJson = response.body().string();
                Map ocrOut = new ObjectMapper().readValue(fastApiJson, Map.class);

                String predicted = (String) ocrOut.getOrDefault("answer", "");
                boolean success = (boolean) ocrOut.get("success");
                boolean match = predicted.equals(expectedAnswer);

                if (match) correct++;
                if (!success || predicted.isEmpty()) failed++;

                // ------------ BUILD RESULT RECORD ------------
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("filename", filename);
                rec.put("expected_text", expectedText);
                rec.put("expected_label", expectedAnswer);
                rec.put("predicted_text", ocrOut.get("expression"));
                rec.put("predicted_label", predicted);
                rec.put("match", match);
                rec.put("success", success);

                results.add(rec);
            }

            // ------------ SUMMARY ------------
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total", total);
            summary.put("correct", correct);
            summary.put("failed", failed);
            summary.put("accuracy", (correct * 100.0) / total + "%");
            summary.put("results", results);

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}