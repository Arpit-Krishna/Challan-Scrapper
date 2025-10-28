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
public class OcrService3{

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
            String preprocessedPath = imagePath.replace(".png", "_preprocessed.png");
            String croppedPath = imagePath.replace(".png", "_cropped.png");

            Mat preprocessed = preprocessImage(imagePath, preprocessedPath);
            if (preprocessed == null) {
                result.put("success", "false");
                result.put("error", "Preprocessing failed");
                return result;
            }

            Mat cropped = smartCrop(preprocessed);
            opencv_imgcodecs.imwrite(croppedPath, cropped);
            preprocessed.release();

            ExpressionResult expr = extractExpressionOcr(cropped);
            cropped.release();

            if (expr.digit1 == null || expr.operator == null || expr.digit2 == null) {
                result.put("success", "false");
                result.put("error", "Could not extract expression");
                return result;
            }

            String expression = expr.digit1 + " " + expr.operator + " " + expr.digit2 + " = ?";

            int answer = expr.operator.equals("+") ?
                    Integer.parseInt(expr.digit1) + Integer.parseInt(expr.digit2) :
                    Integer.parseInt(expr.digit1) - Integer.parseInt(expr.digit2);

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

            Mat inverted = new Mat();
            bitwise_not(img, inverted);
            img.release();

            Mat resized = new Mat();
            Size newSize = new Size(inverted.cols() * SCALE_FACTOR, inverted.rows() * SCALE_FACTOR);
            resize(inverted, resized, newSize, 0, 0, INTER_CUBIC);
            inverted.release();

            Mat denoised = new Mat();
            opencv_photo.fastNlMeansDenoising(resized, denoised, 20, 7, 21);
            resized.release();

            Mat thresholded = new Mat();
            threshold(denoised, thresholded, 0, 255, THRESH_BINARY | THRESH_OTSU);
            denoised.release();

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

    private Mat smartCrop(Mat img) {
        int h = img.rows();
        int w = img.cols();

        int cropTop = (int) (h * 0.35);
        int cropBottom = (int) (h * 0.65);
        int cropLeft = (int) (w * 0.185);
        int cropRight = (int) (w * 0.54);

        int roiHeight = cropBottom - cropTop;
        int roiWidth = cropRight - cropLeft;

        Rect roi = new Rect(cropLeft, cropTop, roiWidth, roiHeight);
        return new Mat(img, roi).clone();
    }

    private static class ExpressionResult {
        String digit1;
        String operator;
        String digit2;

        ExpressionResult(String d1, String op, String d2) {
            this.digit1 = d1;
            this.operator = op;
            this.digit2 = d2;
        }
    }

    private ExpressionResult extractExpressionOcr(Mat img) {
        int[] psmModes = {6, 7, 13, 11, 3};
        List<ExpressionResult> allResults = new ArrayList<>();

        for (int psm : psmModes) {
            try {
                File tmp = File.createTempFile("expr_", ".png");
                opencv_imgcodecs.imwrite(tmp.getAbsolutePath(), img);

                ITesseract tess = new Tesseract();
                tess.setDatapath(tessDataPath);
                tess.setLanguage("eng");
                tess.setPageSegMode(psm);
                tess.setOcrEngineMode(3);
                tess.setTessVariable("tessedit_char_whitelist", "0123456789+-");

                String text = tess.doOCR(tmp).trim();
                tmp.delete();

                text = text.replace(" ", "").replace("\n", "");

                for (String op : new String[]{"+", "-"}) {
                    if (text.contains(op)) {
                        String[] parts = text.split("\\" + op);
                        List<String> validParts = new ArrayList<>();

                        for (String part : parts) {
                            if (!part.isEmpty()) {
                                validParts.add(part);
                            }
                        }

                        for (int i = 0; i < validParts.size() - 1; i++) {
                            String part1 = validParts.get(i);
                            String part2 = validParts.get(i + 1);

                            if (part1.matches("\\d+") && part2.matches("\\d+")) {
                                allResults.add(new ExpressionResult(part1, op, part2));
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }

        if (!allResults.isEmpty()) {
            return allResults.get(0);
        }

        return new ExpressionResult(null, null, null);
    }
}