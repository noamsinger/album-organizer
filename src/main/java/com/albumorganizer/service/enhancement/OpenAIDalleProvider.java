package com.albumorganizer.service.enhancement;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class OpenAIDalleProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIDalleProvider.class);
    private static final String ENDPOINT = "https://api.openai.com/v1/images/edits";

    private final String apiKey;
    private final Gson gson = new Gson();

    public OpenAIDalleProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "OpenAI DALL·E (Cloud)";
    }

    @Override
    public String getShortId() { return "DALLE"; }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean supportsPrompt() {
        return true;
    }

    @Override
    public String estimatedCostPerImage() { return "~$0.04 / image (DALL·E 2 edit)"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = Files.readAllBytes(request.inputPath());
        String boundary = UUID.randomUUID().toString().replace("-", "");

        // DALL·E edits require a PNG with alpha; convert if needed
        byte[] pngBytes = ensurePng(request.inputPath(), imageBytes);

        // Pick nearest supported size
        String size = resolveSize(request);

        byte[] body = buildMultipartBody(boundary, pngBytes, request.prompt(), size);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .timeout(Duration.ofMinutes(5))
            .build();

        String cleanBody = String.format(
            "Content-Type: multipart/form-data; boundary=%s\n\n" +
            "--%s\n" +
            "Content-Disposition: form-data; name=\"image\"; filename=\"input.png\"\n" +
            "Content-Type: image/png\n\n" +
            "<PNG IMAGE DATA TRUNCATED>\n" +
            "--%s\n" +
            "Content-Disposition: form-data; name=\"prompt\"\n\n" +
            "%s\n" +
            "--%s\n" +
            "Content-Disposition: form-data; name=\"size\"\n\n" +
            "%s\n" +
            "--%s\n" +
            "Content-Disposition: form-data; name=\"response_format\"\n\n" +
            "b64_json\n" +
            "--%s--",
            boundary, boundary, boundary, request.prompt() != null ? request.prompt() : "enhance this image", boundary, size, boundary, boundary
        );

        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String respBody = response.body();
                logger.error("OpenAI returned HTTP {}: {}", response.statusCode(), respBody);
                String errMsg = respBody;
                try {
                    JsonObject errJson = gson.fromJson(respBody, JsonObject.class);
                    if (errJson.has("error")) {
                        JsonObject err = errJson.getAsJsonObject("error");
                        errMsg = err.has("message") ? err.get("message").getAsString() : err.toString();
                    }
                } catch (Exception ignored) {}
                String technicalDetail = "Request Sent:\n" + cleanBody + "\n\nResponse Received (HTTP " + response.statusCode() + "):\n" + respBody;
                return EnhancementResult.failure("OpenAI error " + response.statusCode() + ": " + errMsg, technicalDetail, cleanBody, respBody);
            }
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String b64 = json.getAsJsonArray("data").get(0).getAsJsonObject().get("b64_json").getAsString();
            byte[] resultBytes = Base64.getDecoder().decode(b64);
            Path outputPath = request.customOutputPath() != null
                ? ImageUtils.saveAsJpeg(resultBytes, request.customOutputPath())
                : ImageUtils.saveAsJpeg(resultBytes, request.inputPath(), getShortId(), request.outputDir());
            logger.info("Saved DALL·E enhanced image to {}", outputPath);
            return EnhancementResult.ok(outputPath, cleanBody, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
    }

    private byte[] ensurePng(Path path, byte[] originalBytes) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return originalBytes;
        }
        // Convert to PNG in memory via ImageIO
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(path.toFile());
        if (img == null) {
            return originalBytes;
        }
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private String resolveSize(EnhancementRequest request) {
        if (request.targetSize() == null) {
            return "1024x1024";
        }
        int maxDim = Math.max(request.targetSize().width, request.targetSize().height);
        if (maxDim <= 256) return "256x256";
        if (maxDim <= 512) return "512x512";
        return "1024x1024";
    }

    private byte[] buildMultipartBody(String boundary, byte[] pngBytes, String prompt, String size) {
        String nl = "\r\n";
        StringBuilder sb = new StringBuilder();

        sb.append("--").append(boundary).append(nl);
        sb.append("Content-Disposition: form-data; name=\"image\"; filename=\"input.png\"").append(nl);
        sb.append("Content-Type: image/png").append(nl).append(nl);

        byte[] headerBytes = sb.toString().getBytes();

        StringBuilder sb2 = new StringBuilder();
        sb2.append(nl);
        sb2.append("--").append(boundary).append(nl);
        sb2.append("Content-Disposition: form-data; name=\"prompt\"").append(nl).append(nl);
        sb2.append(prompt != null ? prompt : "enhance this image").append(nl);

        sb2.append("--").append(boundary).append(nl);
        sb2.append("Content-Disposition: form-data; name=\"size\"").append(nl).append(nl);
        sb2.append(size).append(nl);

        sb2.append("--").append(boundary).append(nl);
        sb2.append("Content-Disposition: form-data; name=\"response_format\"").append(nl).append(nl);
        sb2.append("b64_json").append(nl);

        sb2.append("--").append(boundary).append("--").append(nl);
        byte[] footerBytes = sb2.toString().getBytes();

        byte[] result = new byte[headerBytes.length + pngBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(pngBytes, 0, result, headerBytes.length, pngBytes.length);
        System.arraycopy(footerBytes, 0, result, headerBytes.length + pngBytes.length, footerBytes.length);
        return result;
    }
}
