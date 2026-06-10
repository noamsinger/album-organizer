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
import java.util.List;

public class GrokProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(GrokProvider.class);
    private static final String EDITS_ENDPOINT = "https://api.x.ai/v1/images/edits";
    private static final String BALANCE_ENDPOINT = "https://api.x.ai/v1/api-key";
    private static final String MODEL = "grok-imagine-image-quality";

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Gson gson = new Gson();

    public GrokProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override public String getName()         { return "Grok xAI (Cloud)"; }
    @Override public String getShortId()      { return "grok"; }
    @Override public boolean isConfigured()   { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean supportsPrompt() { return true; }
    @Override public String estimatedCostPerImage() { return "~$0.05 / image (grok-imagine-image-quality)"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = ImageUtils.loadAsJpeg(request.inputPath());
        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);

        String prompt = (request.prompt() != null && !request.prompt().isBlank())
            ? request.prompt()
            : "Enhance this image: improve sharpness, color, and overall quality.";

        JsonObject imageObj = new JsonObject();
        imageObj.addProperty("url", dataUrl);
        imageObj.addProperty("type", "image_url");

        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("prompt", prompt);
        payload.add("image", imageObj);
        payload.addProperty("num_images", 1);
        payload.addProperty("response_format", "b64_json");

        JsonObject cleanImageObj = new JsonObject();
        cleanImageObj.addProperty("url", "data:image/jpeg;base64,<BASE64_IMAGE_DATA_TRUNCATED>");
        cleanImageObj.addProperty("type", "image_url");

        JsonObject cleanPayload = new JsonObject();
        cleanPayload.addProperty("model", MODEL);
        cleanPayload.addProperty("prompt", prompt);
        cleanPayload.add("image", cleanImageObj);
        cleanPayload.addProperty("num_images", 1);
        cleanPayload.addProperty("response_format", "b64_json");

        String cleanBody = gson.toJson(cleanPayload);

        logger.info("Calling Grok edits endpoint with model {}", MODEL);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(EDITS_ENDPOINT))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .timeout(Duration.ofMinutes(3))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }

        if (resp.statusCode() != 200) {
            logger.error("Grok API error {}: {}", resp.statusCode(), resp.body());
            String errMsg = resp.body();
            try {
                JsonObject errJson = gson.fromJson(resp.body(), JsonObject.class);
                if (errJson.has("error")) errMsg = errJson.get("error").getAsString();
                else if (errJson.has("message")) errMsg = errJson.get("message").getAsString();
            } catch (Exception ignored) {}
            String technicalDetail = "Request Sent:\n" + cleanBody + "\n\nResponse Received (HTTP " + resp.statusCode() + "):\n" + resp.body();
            return EnhancementResult.failure("Grok API error " + resp.statusCode() + ": " + errMsg, technicalDetail);
        }

        JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
        String b64 = json.getAsJsonArray("data").get(0).getAsJsonObject().get("b64_json").getAsString();
        byte[] resultBytes = Base64.getDecoder().decode(b64);

        Path outputPath = ImageUtils.saveAsJpeg(resultBytes, request.inputPath(), getShortId(), request.outputDir());
        logger.info("Grok enhanced image saved to {}", outputPath);
        return EnhancementResult.ok(outputPath);
    }

    private String detectMime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif"))  return "image/gif";
        return "image/jpeg";
    }

    /**
     * Fetches the remaining credit balance for this API key from xAI.
     * Returns a human-readable string like "$12.34" or null on failure.
     */
    public String fetchBalance() {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BALANCE_ENDPOINT))
            .header("Authorization", "Bearer " + apiKey)
            .GET()
            .timeout(Duration.ofSeconds(15))
            .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                logger.debug("Balance endpoint returned {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
            // Response has a "balance_cents" field (integer, US cents)
            if (json.has("balance_cents")) {
                double dollars = json.get("balance_cents").getAsDouble() / 100.0;
                return String.format("$%.2f", dollars);
            }
            // Fallback: look for any field with "balance" or "credit"
            for (String key : List.of("balance", "credits", "credit_balance", "remaining_credits")) {
                if (json.has(key)) {
                    return json.get(key).getAsString();
                }
            }
            logger.debug("Unexpected balance response: {}", resp.body());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.debug("Failed to fetch Grok balance: {}", e.getMessage());
            return null;
        }
    }
}

