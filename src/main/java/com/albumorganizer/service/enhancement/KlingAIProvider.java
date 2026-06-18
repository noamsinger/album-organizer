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

/**
 * Kling AI cloud video enhancement provider.
 * Uses the Kling AI v1 video generation API for video-to-video enhancement.
 * API key obtained from klingai.com → Settings → API Keys.
 */
public class KlingAIProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(KlingAIProvider.class);
    private static final String BASE_URL = "https://api.klingai.com/v1";
    private static final int POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLLS = 360; // 30 minutes

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Gson gson = new Gson();

    public KlingAIProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override public String getName()           { return "Kling AI (Cloud)"; }
    @Override public String getShortId()        { return "Kling"; }
    @Override public boolean isConfigured()     { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean supportsPrompt()   { return true; }
    @Override public boolean supportsVideo()    { return true; }
    @Override public String estimatedCostPerImage() { return "Credits-based (see klingai.com)"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        Path input = request.inputPath();
        String filename = input.getFileName().toString();
        String prompt = request.prompt() != null && !request.prompt().isBlank()
            ? request.prompt()
            : "Enhance this video: improve sharpness, color accuracy and overall quality.";

        logger.info("Submitting {} to Kling AI", filename);

        // Encode video as base64 for the API
        byte[] videoBytes = Files.readAllBytes(input);
        String videoBase64 = Base64.getEncoder().encodeToString(videoBytes);

        JsonObject videoObj = new JsonObject();
        videoObj.addProperty("video", videoBase64);
        videoObj.addProperty("prompt", prompt);
        videoObj.addProperty("model_name", request.param("model", "kling-v1"));
        videoObj.addProperty("mode", request.param("mode", "std"));
        videoObj.addProperty("duration", request.param("duration", "5"));

        String cleanBody = "{\"prompt\":\"" + prompt + "\",\"video\":\"<BASE64_VIDEO_TRUNCATED>\",\"model_name\":\"" +
            request.param("model", "kling-v1") + "\"}";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/videos/video2video"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(videoObj)))
            .timeout(Duration.ofMinutes(5))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }

        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            logger.error("Kling AI submit error {}: {}", resp.statusCode(), resp.body());
            return EnhancementResult.failure("Kling AI error " + resp.statusCode(), null, cleanBody, resp.body());
        }

        JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
        String taskId = extractTaskId(json);
        if (taskId == null) {
            return EnhancementResult.failure("Kling AI: no task ID in response", null, cleanBody, resp.body());
        }

        logger.info("Kling AI task {} submitted, polling…", taskId);
        String downloadUrl = pollForResult(taskId);
        if (downloadUrl == null) {
            return EnhancementResult.failure("Kling AI: task did not complete in time (taskId=" + taskId + ")");
        }

        byte[] resultBytes = downloadResult(downloadUrl);
        if (resultBytes == null) {
            return EnhancementResult.failure("Kling AI: failed to download result for task " + taskId);
        }

        Path outputPath = resolveOutputPath(input, ".mp4", request);
        Files.write(outputPath, resultBytes);
        logger.info("Kling AI result saved to {}", outputPath);
        return EnhancementResult.ok(outputPath, cleanBody, "");
    }

    private String extractTaskId(JsonObject json) {
        if (json.has("task_id")) return json.get("task_id").getAsString();
        if (json.has("id")) return json.get("id").getAsString();
        if (json.has("data") && json.get("data").isJsonObject()) {
            JsonObject data = json.getAsJsonObject("data");
            if (data.has("task_id")) return data.get("task_id").getAsString();
        }
        return null;
    }

    private String pollForResult(String taskId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/videos/video2video/" + taskId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) continue;
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);

                // Kling wraps response in data
                JsonObject data = json.has("data") && json.get("data").isJsonObject()
                    ? json.getAsJsonObject("data") : json;

                String status = data.has("task_status") ? data.get("task_status").getAsString()
                              : data.has("status") ? data.get("status").getAsString() : "";
                logger.debug("Kling AI task {} status: {}", taskId, status);

                if ("succeed".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
                    if (data.has("task_result") && data.get("task_result").isJsonObject()) {
                        JsonObject result = data.getAsJsonObject("task_result");
                        if (result.has("videos") && result.get("videos").isJsonArray()) {
                            JsonObject video = result.getAsJsonArray("videos").get(0).getAsJsonObject();
                            if (video.has("url")) return video.get("url").getAsString();
                        }
                    }
                    if (data.has("output_url")) return data.get("output_url").getAsString();
                }
                if ("failed".equalsIgnoreCase(status)) {
                    logger.error("Kling AI task {} failed: {}", taskId, resp.body());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                logger.debug("Kling AI poll error: {}", e.getMessage());
            }
        }
        return null;
    }

    private byte[] downloadResult(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofMinutes(10))
                .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.error("Kling AI download error: {}", e.getMessage());
            return null;
        }
    }

    private Path resolveOutputPath(Path input, String ext, EnhancementRequest request) {
        if (request.customOutputPath() != null) return request.customOutputPath();
        String base = input.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot >= 0) base = base.substring(0, dot);
        long epoch = System.currentTimeMillis() / 1000;
        String outName = base + "_" + getShortId() + "-" + epoch + ext;
        Path dir = request.outputDir() != null ? request.outputDir() : input.getParent();
        return dir.resolve(outName);
    }
}
