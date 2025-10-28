package org.challan.challan_scraper.services;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_photo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

@Service
public class OcrService2 {

    static {
        try {
            Loader.load(opencv_core.class);
            System.setProperty("jna.library.path", "/opt/homebrew/opt/tesseract/lib");
            System.setProperty("TESSDATA_PREFIX", "/opt/homebrew/share/tessdata/");
            System.out.println("OpenCV loaded successfully");
        } catch (Exception e) {
            System.err.println("Failed to load OpenCV");
            e.printStackTrace();
        }
    }

    private final String tessDataPath = "/opt/homebrew/share/tessdata";
    private static final int SCALE_FACTOR = 10;

    public Map<String, String> processArithmeticImage(String imagePath) {
        Map<String, String> result = new HashMap<>();

        try {
            String outputPath = imagePath.replace(".png", "_processed.png");

            Mat processed = preprocessImage(imagePath, outputPath);
            if (processed == null) {                result.put("success", "false");
                result.put("error", "Preprocessing failed");
                return result;
            }

            DigitPair digits = findTwoDigits(outputPath);
            processed.release();

            if (digits.digit1 == null || digits.digit2 == null) {
                result.put("success", "false");
                result.put("error", "Could not find 2 digits");
                return result;
            }

            String operator = detectOperator(outputPath);
            if (Integer.parseInt(digits.digit1) < Integer.parseInt(digits.digit2)){
                operator = "+";
            }
            String expression = digits.digit1 + " " + operator + " " + digits.digit2 + " = ?";

            int answer = operator.equals("+") ?
                    Integer.parseInt(digits.digit1) + Integer.parseInt(digits.digit2) :
                    Integer.parseInt(digits.digit1) - Integer.parseInt(digits.digit2);

            result.put("expression", expression);
            result.put("answer", String.valueOf(answer));
            result.put("success", "true");

            System.out.println("Result: " + expression + " → " + answer);

        } catch (Exception e) {
            result.put("success", "false");
            result.put("error", e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private Mat preprocessImage(String inputPath, String outputPath) {
        try {
            Mat img = opencv_imgcodecs.imread(inputPath, opencv_imgcodecs.IMREAD_GRAYSCALE);
            if (img == null || img.empty()) {
                return null;
            }

            // Invert
            Mat inverted = new Mat();
            bitwise_not(img, inverted);
            img.release();

            // Upscale
            Mat resized = new Mat();
            Size newSize = new Size(inverted.cols() * SCALE_FACTOR, inverted.rows() * SCALE_FACTOR);
            resize(inverted, resized, newSize, 0, 0, INTER_CUBIC);
            inverted.release();

            // Denoise
            Mat denoised = new Mat();
            opencv_photo.fastNlMeansDenoising(resized, denoised, 20, 7, 21);
            resized.release();

            // Binary threshold
            Mat thresholded = new Mat();
            threshold(denoised, thresholded, 0, 255, THRESH_BINARY | THRESH_OTSU);
            denoised.release();

            // Remove small noise with morphological opening
            Mat kernel = getStructuringElement(MORPH_RECT, new Size(4, 4));
            Mat invForMorph = new Mat();
            bitwise_not(thresholded, invForMorph);

            Mat opened = new Mat();
            morphologyEx(invForMorph, opened, MORPH_OPEN, kernel, new Point(-1, -1), 2, BORDER_CONSTANT, morphologyDefaultBorderValue());
            kernel.release();
            invForMorph.release();

            Mat result = new Mat();
            bitwise_not(opened, result);
            opened.release();
            thresholded.release();

            // Add padding
            int padding = 100;
            Mat padded = new Mat();
            copyMakeBorder(result, padded, padding, padding, padding, padding,
                    BORDER_CONSTANT, new Scalar(255, 255, 255, 255));
            result.release();

            opencv_imgcodecs.imwrite(outputPath, padded);
            return padded;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class DigitPair {
        String digit1;
        String digit2;

        DigitPair(String d1, String d2) {
            this.digit1 = d1;
            this.digit2 = d2;
        }
    }

    private DigitPair findTwoDigits(String imagePath) {
        Mat img = opencv_imgcodecs.imread(imagePath, opencv_imgcodecs.IMREAD_GRAYSCALE);
        if (img == null || img.empty()) {
            return new DigitPair(null, null);
        }

        // Threshold
        Mat thresh = new Mat();
        threshold(img, thresh, 200, 255, THRESH_BINARY_INV);

        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(thresh, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        thresh.release();
        hierarchy.release();

        // Get bounding boxes and filter by size
        List<BoundingBox> candidates = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Rect rect = boundingRect(contours.get(i));
            int area = rect.width() * rect.height();

            if (rect.width() > 25 && rect.height() > 40 && area > 2000) {
                candidates.add(new BoundingBox(rect.x(), rect.y(), rect.width(),
                        rect.height(), area));
            }
        }

        // Sort by area (largest first) and take top 3
        candidates.sort((a, b) -> Integer.compare(b.area, a.area));
        List<BoundingBox> topCandidates = candidates.subList(0, Math.min(3, candidates.size()));

        // Sort by x position (left to right)
        topCandidates.sort((a, b) -> Integer.compare(a.x, b.x));

        // Recognize digits
        List<String> digits = new ArrayList<>();
        for (BoundingBox box : topCandidates) {
            int pad = 15;
            int x1 = Math.max(0, box.x - pad);
            int y1 = Math.max(0, box.y - pad);
            int x2 = Math.min(img.cols(), box.x + box.w + pad);
            int y2 = Math.min(img.rows(), box.y + box.h + pad);

            Rect roi = new Rect(x1, y1, x2 - x1, y2 - y1);
            Mat charImg = new Mat(img, roi);

            String digit = recognizeDigit(charImg);
            charImg.release();

            if (digit != null) {
                digits.add(digit);
            }
        }

        img.release();

        if (digits.size() >= 2) {
            return new DigitPair(digits.get(0), digits.get(1));
        }
        return new DigitPair(null, null);
    }

    private static class BoundingBox {
        int x, y, w, h, area;

        BoundingBox(int x, int y, int w, int h, int area) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.area = area;
        }
    }

    private String recognizeDigit(Mat charImg) {
        int[] psmModes = {10, 10, 8, 7};
        int[] oemModes = {3, 1, 3, 3};

        List<String> results = new ArrayList<>();

        for (int i = 0; i < psmModes.length; i++) {
            String digit = tryOcrDigit(charImg, psmModes[i], oemModes[i]);
            if (digit != null) {
                results.add(digit);
            }
        }

        if (results.isEmpty()) {
            return null;
        }

        // Return most common result
        Map<String, Integer> counts = new HashMap<>();
        for (String d : results) {
            counts.put(d, counts.getOrDefault(d, 0) + 1);
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String tryOcrDigit(Mat charImg, int psm, int oem) {
        try {
            File tmp = File.createTempFile("digit_", ".png");
            opencv_imgcodecs.imwrite(tmp.getAbsolutePath(), charImg);

            ITesseract tess = new Tesseract();
            tess.setDatapath(tessDataPath);
            tess.setLanguage("eng");
            tess.setPageSegMode(psm);
            tess.setOcrEngineMode(oem);
            tess.setTessVariable("tessedit_char_whitelist", "0123456789");

            String result = tess.doOCR(tmp).trim();
            tmp.delete();

            result = fixDigitErrors(result);

            if (result.length() == 1 && result.matches("[0-9]")) {
                return result;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String fixDigitErrors(String text) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("O", "0");
        replacements.put("o", "0");
        replacements.put("D", "0");
        replacements.put("Q", "0");
        replacements.put("l", "1");
        replacements.put("I", "1");
        replacements.put("|", "1");
        replacements.put("i", "1");
        replacements.put("Z", "2");
        replacements.put("z", "2");
        replacements.put("S", "5");
        replacements.put("s", "5");
        replacements.put("b", "6");
        replacements.put("G", "6");
        replacements.put("T", "7");
        replacements.put("t", "7");
        replacements.put("B", "8");
        replacements.put("g", "9");
        replacements.put("q", "9");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        // Keep only first digit
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }

        return digits.length() > 0 ? String.valueOf(digits.charAt(0)) : "";
    }

    private String detectOperator(String imagePath) {
        Mat img = opencv_imgcodecs.imread(imagePath, opencv_imgcodecs.IMREAD_GRAYSCALE);
        if (img == null || img.empty()) {
            return "-";
        }

        int[] psmModes = {11, 12, 6};

        for (int psm : psmModes) {
            if (tryDetectPlus(img, psm)) {
                img.release();
                return "+";
            }
        }

        img.release();
        return "-";
    }

    private boolean tryDetectPlus(Mat img, int psm) {
        try {
            File tmp = File.createTempFile("operator_", ".png");
            opencv_imgcodecs.imwrite(tmp.getAbsolutePath(), img);

            ITesseract tess = new Tesseract();
            tess.setDatapath(tessDataPath);
            tess.setLanguage("eng");
            tess.setPageSegMode(psm);
            tess.setOcrEngineMode(3);
            tess.setTessVariable("tessedit_char_whitelist", "+");

            String result = tess.doOCR(tmp).trim();
            tmp.delete();

            return result.contains("+");
        } catch (Exception e) {
            return false;
        }
    }
}