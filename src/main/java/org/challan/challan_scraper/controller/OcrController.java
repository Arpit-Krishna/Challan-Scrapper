package org.challan.challan_scraper.controller;

import org.challan.challan_scraper.services.OcrService;
import org.challan.challan_scraper.services.OcrService2;
import org.challan.challan_scraper.services.OcrService3;
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

    /**
     * Read text from a single image using the Python-equivalent OCR pipeline
     */
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
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadResult(@RequestParam("path") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                return ResponseEntity.notFound().build();
            }

            Resource r = new UrlResource(p.toUri());
            String fn = p.getFileName().toString();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + "\"")
                    .body(r);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

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
}