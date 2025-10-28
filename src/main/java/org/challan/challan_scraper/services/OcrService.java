package org.challan.challan_scraper.services;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_photo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.opencv.core.Core.BORDER_CONSTANT;
import static org.opencv.core.CvType.CV_32FC2;
import static org.opencv.core.CvType.CV_32S;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.MORPH_OPEN;

@Service
public class OcrService {

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

    // Pattern for single digit arithmetic: digit + operator + digit
    private static final Pattern ARITHMETIC_PATTERN = Pattern.compile("^\\s*([0-9])\\s*([+\\-])\\s*([0-9])\\s*$");

    public String extractText(String imgPath) {
        Mat bgr = opencv_imgcodecs.imread(imgPath);

        if (bgr == null || bgr.empty()) {
            System.err.println("Failed to load image: " + imgPath);
            return "";
        }

        System.out.println("Image loaded: " + bgr.cols() + "x" + bgr.rows());

        try {
            PreprocessResult preprocessed = preprocessForOcr(bgr);
            System.out.println("Preprocessed image: " + preprocessed.thresholded.cols() + "x" + preprocessed.thresholded.rows());

            String result = readImg(preprocessed.thresholded);
            System.out.println("OCR Result: " + result);

            preprocessed.gray.release();
            preprocessed.thresholded.release();

            // If result is empty, try simple preprocessing
            if (result.isEmpty()) {
                System.out.println("Trying simple preprocessing...");
                PreprocessResult simple = simplePreprocess(bgr);
                result = readImg(simple.thresholded);
                System.out.println("Simple OCR Result: " + result);
                simple.gray.release();
                simple.thresholded.release();
            }

            bgr.release();
            return result;
        } catch (Exception e) {
            System.err.println("Preprocessing failed: " + e.getMessage());
            e.printStackTrace();

            // Fallback to simple preprocessing
            try {
                System.out.println("Falling back to simple preprocessing...");
                PreprocessResult simple = simplePreprocess(bgr);
                String result = readImg(simple.thresholded);
                simple.gray.release();
                simple.thresholded.release();
                bgr.release();
                return result;
            } catch (Exception e2) {
                System.err.println("Simple preprocessing also failed: " + e2.getMessage());
                bgr.release();
                return "";
            }
        }
    }

    private PreprocessResult simplePreprocess(Mat bgr) {
        Mat gray = new Mat();
        cvtColor(bgr, gray, COLOR_BGR2GRAY);

        // Upscale by 4x
        Mat resized = new Mat();
        Size newSize = new Size(gray.cols() * 4, gray.rows() * 4);
        resize(gray, resized, newSize, 0, 0, INTER_CUBIC);
        gray.release();

        // Simple threshold
        Mat th = new Mat();
        threshold(resized, th, 0, 255, THRESH_BINARY_INV | THRESH_OTSU);

        return new PreprocessResult(resized, th);
    }

    private static class PreprocessResult {
        Mat gray;
        Mat thresholded;

        PreprocessResult(Mat gray, Mat thresholded) {
            this.gray = gray;
            this.thresholded = thresholded;
        }
    }

    private PreprocessResult preprocessForOcr(Mat bgr) {
        // Convert to grayscale
        Mat gray = new Mat();
        cvtColor(bgr, gray, COLOR_BGR2GRAY);

        // Upscale by 5x
        double scale = 5.0;
        Mat resized = new Mat();
        Size newSize = new Size((int)(gray.cols() * scale), (int)(gray.rows() * scale));
        resize(gray, resized, newSize, 0, 0, INTER_CUBIC);
        gray.release();

        // Denoise
        Mat denoised = new Mat();
        opencv_photo.fastNlMeansDenoising(resized, denoised, 10, 7, 21);
        resized.release();

        // Threshold with OTSU
        Mat th = new Mat();
        threshold(denoised, th, 0, 255, THRESH_BINARY_INV | THRESH_OTSU);

        // Connected components filtering
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numLabels = connectedComponentsWithStats(th, labels, stats, centroids, 8, CV_32S);

        Mat thClean = Mat.zeros(th.size(), th.type()).asMat();

        long minArea = 20;
        long maxArea = (long)(th.rows() * th.cols() * 0.5);

        int componentsKept = 0;
        for (int i = 1; i < numLabels; i++) {
            int area = stats.ptr(i, CC_STAT_AREA).getInt();
            if (area > minArea && area < maxArea) {
                componentsKept++;
                // Copy pixels where label == i
                byte[] labelData = new byte[labels.rows() * labels.cols() * 4];
                labels.data().get(labelData);
                byte[] thCleanData = new byte[thClean.rows() * thClean.cols()];
                thClean.data().get(thCleanData);

                for (int y = 0; y < labels.rows(); y++) {
                    for (int x = 0; x < labels.cols(); x++) {
                        int idx = (y * labels.cols() + x) * 4;
                        int labelValue = java.nio.ByteBuffer.wrap(labelData, idx, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        if (labelValue == i) {
                            thCleanData[y * thClean.cols() + x] = (byte)255;
                        }
                    }
                }
                thClean.data().put(thCleanData);
            }
        }

        System.out.println("Connected components: " + numLabels + ", kept: " + componentsKept);

        th.release();
        labels.release();
        stats.release();
        centroids.release();

        // If no components were kept, fall back to simple threshold
        if (componentsKept == 0) {
            System.out.println("WARNING: No components kept, using simple threshold");
            threshold(denoised, thClean, 0, 255, THRESH_BINARY_INV | THRESH_OTSU);
        }

        // Rotation detection and correction
        RotationResult rotated = detectAndRotate(denoised, thClean, scale);
        denoised.release();
        thClean.release();

        return new PreprocessResult(rotated.gray, rotated.thresholded);
    }

    private static class RotationResult {
        Mat gray;
        Mat thresholded;

        RotationResult(Mat gray, Mat thresholded) {
            this.gray = gray;
            this.thresholded = thresholded;
        }
    }

    private RotationResult detectAndRotate(Mat gray, Mat th, double scale) {
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(th, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        if (contours.size() == 0) {
            return new RotationResult(gray, th);
        }

        // Combine all contour points
        List<Point> allPoints = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            for (int j = 0; j < contour.rows(); j++) {
                allPoints.add(new Point(
                        contour.ptr(j, 0).getInt(0),
                        contour.ptr(j, 0).getInt(1)
                ));
            }
        }

        // Convert to Mat for minAreaRect
        Mat pointsMat = new Mat(allPoints.size(), 1, CV_32FC2);
        for (int i = 0; i < allPoints.size(); i++) {
            pointsMat.ptr(i, 0).putFloat(0, (float)allPoints.get(i).x());
            pointsMat.ptr(i, 0).putFloat(1, (float)allPoints.get(i).y());
        }

        RotatedRect rect = minAreaRect(pointsMat);
        pointsMat.release();

        double width = rect.size().width();
        double height = rect.size().height();

        if (height > width) {
            double temp = width;
            width = height;
            height = temp;
        }

        double aspectRatio = height > 0 ? width / height : 1.0;
        double rotationAngle = 0;

        if (aspectRatio < 0.5) {
            rotationAngle = 90;
        } else if (aspectRatio > 2) {
            rotationAngle = 0;
        } else {
            double angle = rect.angle();
            if (angle < -45) {
                rotationAngle = -(90 + angle);
            } else {
                rotationAngle = -angle;
            }

            if (Math.abs(rotationAngle) >= 15) {
                rotationAngle = 0;
            }
        }

        if (Math.abs(rotationAngle) < 1) {
            return new RotationResult(gray, th);
        }

        // Perform rotation
        Point2f center = new Point2f(gray.cols() / 2.0f, gray.rows() / 2.0f);
        Mat M = getRotationMatrix2D(center, rotationAngle, 1.0);

        double cos = Math.abs(M.ptr(0, 0).getDouble());
        double sin = Math.abs(M.ptr(0, 1).getDouble());
        int newW = (int)((gray.rows() * sin) + (gray.cols() * cos));
        int newH = (int)((gray.rows() * cos) + (gray.cols() * sin));

        M.ptr(0, 2).putDouble(M.ptr(0, 2).getDouble() + (newW / 2.0) - center.x());
        M.ptr(1, 2).putDouble(M.ptr(1, 2).getDouble() + (newH / 2.0) - center.y());

        Mat grayRotated = new Mat();
        Mat thRotated = new Mat();

        warpAffine(gray, grayRotated, M, new Size(newW, newH),
                INTER_CUBIC, BORDER_CONSTANT, new Scalar(255, 255, 255, 255));
        warpAffine(th, thRotated, M, new Size(newW, newH),
                INTER_NEAREST, BORDER_CONSTANT, new Scalar(0, 0, 0, 0));

        M.release();

        return new RotationResult(grayRotated, thRotated);
    }

    private String readImg(Mat th) {
        // Invert the image
        Mat grayInv = new Mat();
        bitwise_not(th, grayInv);

        // Additional noise removal using morphological operations
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(2, 2));
        Mat cleaned = new Mat();

        // Opening operation to remove small white noise
        morphologyEx(grayInv, cleaned, MORPH_OPEN, kernel);
        kernel.release();
        grayInv.release();

        // Remove very small connected components (noise)
        Mat labelsNoise = new Mat();
        Mat statsNoise = new Mat();
        Mat centroidsNoise = new Mat();
        int numLabelsNoise = connectedComponentsWithStats(cleaned, labelsNoise, statsNoise, centroidsNoise, 4, CV_32S);

        Mat cleanedFinal = new Mat();
        cleaned.copyTo(cleanedFinal);

        // Remove components smaller than threshold (likely noise)
        int minComponentSize = 50; // Adjust based on your image size
        for (int i = 1; i < numLabelsNoise; i++) {
            int area = statsNoise.ptr(i, CC_STAT_AREA).getInt();
            if (area < minComponentSize) {
                // Remove this component
                byte[] labelData = new byte[labelsNoise.rows() * labelsNoise.cols() * 4];
                labelsNoise.data().get(labelData);
                byte[] cleanedData = new byte[cleanedFinal.rows() * cleanedFinal.cols()];
                cleanedFinal.data().get(cleanedData);

                for (int y = 0; y < labelsNoise.rows(); y++) {
                    for (int x = 0; x < labelsNoise.cols(); x++) {
                        int idx = (y * labelsNoise.cols() + x) * 4;
                        int labelValue = java.nio.ByteBuffer.wrap(labelData, idx, 4)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        if (labelValue == i) {
                            cleanedData[y * cleanedFinal.cols() + x] = 0; // Set to black
                        }
                    }
                }
                cleanedFinal.data().put(cleanedData);
            }
        }

        labelsNoise.release();
        statsNoise.release();
        centroidsNoise.release();
        cleaned.release();

        // Add padding
        int pad = 50;
        Mat grayPadded = new Mat();
        copyMakeBorder(cleanedFinal, grayPadded, pad, pad, pad, pad,
                BORDER_CONSTANT, new Scalar(255, 255, 255, 255));
        cleanedFinal.release();

        // Debug: Save preprocessed image
        try {
            String debugPath = System.getProperty("user.dir") + File.separator + "debug_preprocessed.png";
            opencv_imgcodecs.imwrite(debugPath, grayPadded);
            System.out.println("Debug image saved to: " + debugPath);
        } catch (Exception e) {
            System.err.println("Failed to save debug image: " + e.getMessage());
        }

        // PSM configurations to try - focusing on simpler modes for clean text
        int[] psmModes = {7, 8, 6, 13, 11}; // PSM 7 (single line) first, then others
        int[] oemModes = {3, 3, 3, 3, 3};   // Use LSTM engine

        List<OcrResult> results = new ArrayList<>();

        for (int i = 0; i < psmModes.length; i++) {
            int psm = psmModes[i];
            int oem = oemModes[i];

            System.out.println("Trying PSM=" + psm + ", OEM=" + oem);

            // Try with whitelist (restricted to 0-9, +, -)
            String textWithWl = tryOcr(grayPadded, psm, oem, true);
            System.out.println("  With whitelist: '" + textWithWl + "'");

            if (textWithWl != null && !textWithWl.isEmpty()) {
                double score = scoreResult(textWithWl, psm, oem, true, grayPadded);
                System.out.println("  Score: " + score);
                if (score > 0) {
                    results.add(new OcrResult(textWithWl, score, psm, oem, true));
                }
            }

            // Try without whitelist
            String textNoWl = tryOcr(grayPadded, psm, oem, false);
            System.out.println("  Without whitelist: '" + textNoWl + "'");

            if (textNoWl != null && !textNoWl.isEmpty() && !textNoWl.equals(textWithWl)) {
                double score = scoreResult(textNoWl, psm, oem, false, grayPadded);
                System.out.println("  Score: " + score);
                if (score > 0) {
                    results.add(new OcrResult(textNoWl, score, psm, oem, false));
                }
            }
        }

        grayPadded.release();

        if (results.isEmpty()) {
            System.out.println("No valid OCR results found");
            return "";
        }

        // Sort by score (descending)
        results.sort((a, b) -> Double.compare(b.confidence, a.confidence));

        System.out.println("Best result: '" + results.get(0).text + "' (score: " + results.get(0).confidence +
                ", PSM=" + results.get(0).psm + ", OEM=" + results.get(0).oem +
                ", whitelist=" + results.get(0).useWhitelist + ")");

        return results.get(0).text;
    }

    /**
     * Validates and scores an OCR result based on:
     * 1. Pattern matching (digit + operator + digit)
     * 2. Character validation (only 0-9, +, -, spaces)
     * 3. Result value in valid range (0-18)
     * 4. OCR confidence
     */
    private double scoreResult(String text, int psm, int oem, boolean useWhitelist, Mat image) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        String cleaned = text.trim();

        // Check length (should be exactly 3 characters without spaces)
        String noSpaces = cleaned.replaceAll("\\s+", "");
        if (noSpaces.length() != 3) {
            System.out.println("    Invalid length: " + noSpaces.length());
            return 0.0;
        }

        // Validate characters - only 0-9, +, -, and spaces allowed
        if (!cleaned.matches("^[0-9+\\-\\s]+$")) {
            System.out.println("    Contains invalid characters");
            return 0.0;
        }

        // Match the pattern: digit + operator + digit
        Matcher matcher = ARITHMETIC_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            System.out.println("    Does not match pattern");
            return 0.0;
        }

        // Extract components
        int digit1 = Integer.parseInt(matcher.group(1));
        String operator = matcher.group(2);
        int digit2 = Integer.parseInt(matcher.group(3));

        // Calculate result
        int result;
        if (operator.equals("+")) {
            result = digit1 + digit2;
        } else { // operator is "-"
            result = digit1 - digit2;
        }

        // Validate result is in valid range (0-18)
        if (result < 0 || result > 18) {
            System.out.println("    Result out of range: " + result);
            return 0.0;
        }

        // Get OCR confidence
        double ocrConfidence = getAverageConfidence(image, psm, oem, useWhitelist);

        // Calculate final score
        // Base score for matching pattern: 50
        // OCR confidence: up to 50
        double score = 50.0 + (ocrConfidence / 2.0);

        System.out.println("    Valid expression: " + digit1 + operator + digit2 + "=" + result +
                ", OCR confidence: " + ocrConfidence + ", final score: " + score);

        return score;
    }

    private static class OcrResult {
        String text;
        double confidence;
        int psm;
        int oem;
        boolean useWhitelist;

        OcrResult(String text, double confidence, int psm, int oem, boolean useWhitelist) {
            this.text = text;
            this.confidence = confidence;
            this.psm = psm;
            this.oem = oem;
            this.useWhitelist = useWhitelist;
        }
    }

    private String tryOcr(Mat image, int psm, int oem, boolean useWhitelist) {
        try {
            File tmp = File.createTempFile("ocr_temp_", ".png");
            opencv_imgcodecs.imwrite(tmp.getAbsolutePath(), image);

            ITesseract tess = new Tesseract();
            tess.setDatapath(tessDataPath);
            tess.setLanguage("eng");
            tess.setPageSegMode(psm);
            tess.setOcrEngineMode(oem);

            // ALWAYS restrict to 0-9, +, - characters (removed space from whitelist)
            if (useWhitelist) {
                tess.setTessVariable("tessedit_char_whitelist", "0123456789+-");
            } else {
                tess.setTessVariable("tessedit_char_whitelist", "0123456789+-");
            }

            // Additional Tesseract configurations for better accuracy
            tess.setTessVariable("tessedit_do_invert", "0"); // Don't auto-invert (we already inverted)
            tess.setTessVariable("classify_bln_numeric_mode", "1"); // Numeric mode

            String result = tess.doOCR(tmp).trim();
            tmp.delete();

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private double getAverageConfidence(Mat image, int psm, int oem, boolean useWhitelist) {
        try {
            File tmp = File.createTempFile("ocr_conf_", ".png");
            opencv_imgcodecs.imwrite(tmp.getAbsolutePath(), image);

            Tesseract tess = new Tesseract();
            tess.setDatapath(tessDataPath);
            tess.setLanguage("eng");
            tess.setPageSegMode(psm);
            tess.setOcrEngineMode(oem);

            tess.setTessVariable("tessedit_char_whitelist", "0123456789+-");
            tess.setTessVariable("tessedit_do_invert", "0");
            tess.setTessVariable("classify_bln_numeric_mode", "1");

            // Use getWords with BufferedImage
            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(tmp);
            List<Word> words = tess.getWords(bufferedImage, 0);
            tmp.delete();

            if (words == null || words.isEmpty()) {
                return 0.0;
            }

            double sum = 0;
            int count = 0;
            for (Word word : words) {
                if (word.getConfidence() > 0) {
                    sum += word.getConfidence();
                    count++;
                }
            }

            return count > 0 ? sum / count : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    public Map<String, String> ocrAndSolve(String imagePath) {
        Map<String, String> out = new HashMap<>();
        try {
            String expression = extractText(imagePath);

            if (expression == null || expression.isEmpty()) {
                out.put("expression", "");
                out.put("answer", "");
                out.put("success", "false");
                return out;
            }

            out.put("expression", expression);
            String answer = evaluateExpression(expression);

            if (answer == null) {
                out.put("answer", "");
                out.put("success", "false");
            } else {
                out.put("answer", answer);
                out.put("success", "true");
            }
            return out;
        } catch (Exception e) {
            out.put("error", e.getMessage());
            out.put("success", "false");
            return out;
        }
    }

    private String evaluateExpression(String expr) {
        if (expr == null) return null;

        Matcher matcher = ARITHMETIC_PATTERN.matcher(expr);
        if (!matcher.matches()) return null;

        try {
            int a = Integer.parseInt(matcher.group(1));
            String op = matcher.group(2);
            int b = Integer.parseInt(matcher.group(3));
            int ans;

            switch (op) {
                case "+": ans = a + b; break;
                case "-": ans = a - b; break;
                default: return null;
            }

            return String.valueOf(ans);
        } catch (Exception e) {
            return null;
        }
    }
}