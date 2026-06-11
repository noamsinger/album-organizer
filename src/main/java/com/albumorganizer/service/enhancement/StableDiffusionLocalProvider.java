package com.albumorganizer.service.enhancement;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

public class StableDiffusionLocalProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(StableDiffusionLocalProvider.class);

    private final String baseUrl;
    private final Gson gson = new Gson();

    public StableDiffusionLocalProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:7860";
    }

    @Override
    public String getName() {
        return "Stable Diffusion (Local)";
    }

    @Override
    public String getShortId() { return "sd"; }

    @Override
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public boolean supportsPrompt() {
        return true;
    }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = ImageUtils.loadAsJpeg(request.inputPath());
        String b64Image = Base64.getEncoder().encodeToString(imageBytes);

        JsonObject payload = new JsonObject();
        JsonArray initImages = new JsonArray();
        initImages.add(b64Image);
        payload.add("init_images", initImages);
        payload.addProperty("prompt", request.prompt() != null ? request.prompt() : "enhance, high quality, detailed");
        payload.addProperty("denoising_strength", 0.4);

        if (request.targetSize() != null) {
            payload.addProperty("width", request.targetSize().width);
            payload.addProperty("height", request.targetSize().height);
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/sdapi/v1/img2img"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .timeout(Duration.ofMinutes(10))
            .build();

        JsonObject cleanPayload = payload.deepCopy();
        JsonArray cleanInitImages = new JsonArray();
        cleanInitImages.add("<BASE64_IMAGE_DATA_TRUNCATED>");
        cleanPayload.add("init_images", cleanInitImages);
        String cleanBody = gson.toJson(cleanPayload);

        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String respBody = response.body();
                String msg = "SD WebUI returned HTTP " + response.statusCode() + ": " + respBody;
                logger.error(msg);
                String technicalDetail = "Request Sent:\n" + cleanBody + "\n\nResponse Received (HTTP " + response.statusCode() + "):\n" + respBody;
                return EnhancementResult.failure("Stable Diffusion Local API error " + response.statusCode(), technicalDetail, cleanBody, respBody);
            }
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String b64Result = json.getAsJsonArray("images").get(0).getAsString();
            byte[] resultBytes = Base64.getDecoder().decode(b64Result);
            Path outputPath = ImageUtils.saveAsJpeg(resultBytes, request.inputPath(), getShortId(), request.outputDir());
            logger.info("Saved SD-enhanced image to {}", outputPath);
            return EnhancementResult.ok(outputPath, cleanBody, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
    }

    public boolean testConnection() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sdapi/v1/sd-models"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
