package com.albumorganizer.service.enhancement;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

class ImageUtils {

    // Anthropic's limit is 5 MB base64-decoded; keep well under that.
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DIM   = 1568; // Anthropic recommends ≤1568 on long edge

    /**
     * Reads any supported image format and returns JPEG bytes, resized if needed.
     * Always outputs JPEG regardless of the source format.
     */
    static byte[] loadAsJpeg(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        boolean isHeic = name.endsWith(".heic") || name.endsWith(".heif");

        BufferedImage img;
        if (isHeic) {
            img = readHeic(path);
        } else {
            img = ImageIO.read(path.toFile());
        }
        if (img == null) throw new IOException("Cannot read image: " + path.getFileName());

        img = scaleIfNeeded(img);

        // Ensure RGB (no alpha) before JPEG encoding
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img = rgb;
        }

        byte[] bytes = encode(img, false, 0.90f);
        if (bytes.length > MAX_BYTES) bytes = encode(img, false, 0.75f);
        if (bytes.length > MAX_BYTES) {
            img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
            bytes = encode(img, false, 0.80f);
        }
        return bytes;
    }

    /**
     * Reads an image file, scales it down if it exceeds MAX_DIM or MAX_BYTES,
     * and returns the result as JPEG bytes (or PNG for PNG sources).
     */
    static byte[] loadAndResize(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        boolean isHeic = name.endsWith(".heic") || name.endsWith(".heif");

        BufferedImage img;
        if (isHeic) {
            img = readHeic(path);
        } else {
            img = ImageIO.read(path.toFile());
        }
        if (img == null) throw new IOException("Cannot read image: " + path.getFileName());

        img = scaleIfNeeded(img);

        String name2 = path.getFileName().toString().toLowerCase();
        boolean isPng = name2.endsWith(".png");

        // First attempt at target quality
        byte[] bytes = encode(img, isPng, 0.90f);

        // If still too large, re-encode at lower quality
        if (bytes.length > MAX_BYTES && !isPng) {
            bytes = encode(img, false, 0.75f);
        }
        // Last resort: scale down further
        if (bytes.length > MAX_BYTES) {
            img = scale(img, img.getWidth() / 2, img.getHeight() / 2);
            bytes = encode(img, false, 0.80f);
        }
        return bytes;
    }

    private static BufferedImage scaleIfNeeded(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= MAX_DIM) return img;
        double ratio = (double) MAX_DIM / longEdge;
        return scale(img, (int) (w * ratio), (int) (h * ratio));
    }

    private static BufferedImage scale(BufferedImage src, int newW, int newH) {
        BufferedImage dst = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return dst;
    }

    private static byte[] encode(BufferedImage img, boolean png, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (png) {
            ImageIO.write(img, "png", baos);
        } else {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
            }
        }
        return baos.toByteArray();
    }

    private static BufferedImage readHeic(Path path) throws IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("heic_enhance_", ".jpg");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "sips", "-s", "format", "jpeg", "-s", "formatOptions", "95",
                path.toAbsolutePath().toString(),
                "--out", tmp.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exit = proc.waitFor();
            if (exit != 0 || java.nio.file.Files.size(tmp) == 0) return null;
            return ImageIO.read(tmp.toFile());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    /**
     * Converts raw image bytes to JPEG and writes to the resolved output path.
     * Output filename pattern: {base}_AI-{providerSlug}-{epoch}.jpg
     */
    static Path saveAsJpeg(byte[] imageBytes, Path inputPath, String providerName) throws IOException {
        return saveAsJpeg(imageBytes, inputPath, providerName, null);
    }

    /**
     * Converts raw image bytes to JPEG. When outputDir is non-null the file is placed there;
     * otherwise the file is placed next to the input.
     */
    static Path saveAsJpeg(byte[] imageBytes, Path inputPath, String providerName, Path outputDir) throws IOException {
        Path outputPath;
        if (outputDir != null) {
            java.nio.file.Files.createDirectories(outputDir);
            String filename = inputPath.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String base = dot >= 0 ? filename.substring(0, dot) : filename;
            String slug = providerName.replaceAll("[^a-zA-Z0-9_-]", "_");
            long epoch = System.currentTimeMillis() / 1000;
            outputPath = outputDir.resolve(String.format("%s_AI-%s-%d.jpg", base, slug, epoch));
        } else {
            outputPath = ImageEnhancementService.resolveOutputPath(inputPath, providerName);
        }
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (img == null) {
            // Fallback: write raw bytes (already JPEG from some providers)
            java.nio.file.Files.write(outputPath, imageBytes);
            return outputPath;
        }
        // Ensure RGB (no alpha) for JPEG
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img = rgb;
        }
        byte[] jpegBytes = encode(img, false, 0.92f);
        java.nio.file.Files.write(outputPath, jpegBytes);
        return outputPath;
    }

    static String mediaType(Path path, boolean isPng) {
        return isPng ? "image/png" : "image/jpeg";
    }
}
