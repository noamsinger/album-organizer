package com.albumorganizer.service.enhancement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

public class StabilityAIProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(StabilityAIProvider.class);
    private static final String ENDPOINT = "https://api.stability.ai/v2beta/stable-image/upscale/creative";

    private final String apiKey;

    public StabilityAIProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "Stability AI (Cloud)";
    }

    @Override
    public String getShortId() { return "stabilityai"; }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supportsPrompt() {
        return true;
    }

    @Override
    public String estimatedCostPerImage() { return "~$0.25–1.00 / image (Stable Image Creative Upscale)"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = ImageUtils.loadAsJpeg(request.inputPath());

        String boundary = UUID.randomUUID().toString().replace("-", "");
        String mimeType = "image/jpeg";
        String outputFormat = "jpeg";

        byte[] body = buildMultipartBody(boundary, imageBytes, mimeType, request.prompt(), outputFormat);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "image/*")
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .timeout(Duration.ofMinutes(5))
            .build();

        String cleanBody = String.format(
            "Content-Type: multipart/form-data; boundary=%s\n\n" +
            "--%s\n" +
            "Content-Disposition: form-data; name=\"image\"; filename=\"input.jpg\"\n" +
            "Content-Type: image/jpeg\n\n" +
            "<IMAGE DATA TRUNCATED>\n" +
            "%s" +
            "--%s\n" +
            "Content-Disposition: form-data; name=\"output_format\"\n\n" +
            "%s\n" +
            "--%s--",
            boundary, boundary,
            (request.prompt() != null && !request.prompt().isBlank()) ? 
                String.format("--%s\nContent-Disposition: form-data; name=\"prompt\"\n\n%s\n", boundary, request.prompt()) : "",
            boundary, outputFormat, boundary
        );

        try {
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                String responseBody = new String(response.body());
                String msg = "Stability AI returned HTTP " + response.statusCode() + ": " + responseBody;
                logger.error(msg);
                String technicalDetail = "Request Sent:\n" + cleanBody + "\n\nResponse Received (HTTP " + response.statusCode() + "):\n" + responseBody;
                return EnhancementResult.failure("Stability AI API error " + response.statusCode(), technicalDetail);
            }
            Path outputPath = writeOutput(request, response.body(), outputFormat);
            return EnhancementResult.ok(outputPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] imageBytes, String mimeType, String prompt, String outputFormat) throws IOException {
        String nl = "\r\n";
        StringBuilder header = new StringBuilder();

        // image part
        header.append("--").append(boundary).append(nl);
        header.append("Content-Disposition: form-data; name=\"image\"; filename=\"input").append(mimeType.contains("png") ? ".png" : ".jpg").append("\"").append(nl);
        header.append("Content-Type: ").append(mimeType).append(nl).append(nl);

        byte[] headerBytes = header.toString().getBytes();

        StringBuilder middle = new StringBuilder();
        middle.append(nl);

        // prompt part
        if (prompt != null && !prompt.isBlank()) {
            middle.append("--").append(boundary).append(nl);
            middle.append("Content-Disposition: form-data; name=\"prompt\"").append(nl).append(nl);
            middle.append(prompt).append(nl);
        }

        // output_format part
        middle.append("--").append(boundary).append(nl);
        middle.append("Content-Disposition: form-data; name=\"output_format\"").append(nl).append(nl);
        middle.append(outputFormat).append(nl);

        middle.append("--").append(boundary).append("--").append(nl);
        byte[] middleBytes = middle.toString().getBytes();

        byte[] result = new byte[headerBytes.length + imageBytes.length + middleBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(imageBytes, 0, result, headerBytes.length, imageBytes.length);
        System.arraycopy(middleBytes, 0, result, headerBytes.length + imageBytes.length, middleBytes.length);
        return result;
    }

    private Path writeOutput(EnhancementRequest request, byte[] bytes, String outputFormat) throws IOException {
        Path outputPath = ImageUtils.saveAsJpeg(bytes, request.inputPath(), getShortId(), request.outputDir());
        logger.info("Saved enhanced image to {}", outputPath);
        return outputPath;
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "jpg";
    }
}
