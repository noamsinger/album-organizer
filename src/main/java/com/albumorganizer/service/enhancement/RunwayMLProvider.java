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
 * RunwayML cloud provider (video-to-video enhancement).
 * Submits a video using the Gen-4 Turbo video-to-video endpoint, polls, downloads result.
 * API key obtained from app.runwayml.com → Account → API Keys.
 */
public class RunwayMLProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(RunwayMLProvider.class);
    private static final String BASE_URL = "https://api.dev.runwayml.com/v1";
    private static final int POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLLS = 360; // 30 minutes

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Gson gson = new Gson();

    public RunwayMLProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override public String getName()           { return "RunwayML (Cloud)"; }
    @Override public String getShortId()        { return "Runway"; }
    @Override public boolean isConfigured()     { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean supportsPrompt()   { return true; }
    @Override public boolean supportsVideo()    { return true; }
    @Override public String estimatedCostPerImage() { return "~$0.05 / sec of video"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        Path input = request.inputPath();
        String filename = input.getFileName().toString();
        String prompt = request.prompt() != null && !request.prompt().isBlank()
            ? request.prompt()
            : "Enhance video quality, improve sharpness and color.";

        logger.info("Submitting {} to RunwayML", filename);

        // Encode video as data URI for video-to-video
        byte[] videoBytes = Files.readAllBytes(input);
        String dataUri = "data:video/mp4;base64," + Base64.getEncoder().encodeToString(videoBytes);

        JsonObject payload = new JsonObject();
        payload.addProperty("promptText", prompt);
        payload.addProperty("promptVideo", dataUri);
        payload.addProperty("model", request.param("model", "gen4_turbo"));
        payload.addProperty("duration", Integer.parseInt(request.param("duration", "10")));
        payload.addProperty("ratio", request.param("ratio", "1280:720"));

        JsonObject cleanPayload = new JsonObject();
        cleanPayload.addProperty("promptText", prompt);
        cleanPayload.addProperty("promptVideo", "data:video/mp4;base64,<VIDEO_DATA_TRUNCATED>");
        cleanPayload.addProperty("model", request.param("model", "gen4_turbo"));

        String cleanBody = gson.toJson(cleanPayload);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/video_to_video"))
            .header("Authorization", "Bearer " + apiKey)
            .header("X-Runway-Version", "2024-11-06")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
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
            logger.error("RunwayML submit error {}: {}", resp.statusCode(), resp.body());
            return EnhancementResult.failure("RunwayML API error " + resp.statusCode(), null, cleanBody, resp.body());
        }

        JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
        String taskId = json.has("id") ? json.get("id").getAsString() : null;
        if (taskId == null) {
            return EnhancementResult.failure("RunwayML: no task ID in response", null, cleanBody, resp.body());
        }

        logger.info("RunwayML task {} submitted, polling…", taskId);
        String downloadUrl = pollForResult(taskId);
        if (downloadUrl == null) {
            return EnhancementResult.failure("RunwayML: task did not complete in time (taskId=" + taskId + ")", null, cleanBody, "");
        }

        byte[] resultBytes = downloadResult(downloadUrl);
        if (resultBytes == null) {
            return EnhancementResult.failure("RunwayML: failed to download result for task " + taskId);
        }

        String ext = ".mp4";
        Path outputPath = resolveOutputPath(input, ext, request);
        Files.write(outputPath, resultBytes);
        logger.info("RunwayML result saved to {}", outputPath);
        return EnhancementResult.ok(outputPath, cleanBody, "");
    }

    private String pollForResult(String taskId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/tasks/" + taskId))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Runway-Version", "2024-11-06")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) continue;
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                String status = json.has("status") ? json.get("status").getAsString() : "";
                logger.debug("RunwayML task {} status: {}", taskId, status);
                if ("SUCCEEDED".equalsIgnoreCase(status)) {
                    if (json.has("output") && json.get("output").isJsonArray()) {
                        return json.getAsJsonArray("output").get(0).getAsString();
                    }
                }
                if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                    logger.error("RunwayML task {} failed: {}", taskId, resp.body());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                logger.debug("RunwayML poll error: {}", e.getMessage());
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
            logger.error("RunwayML download error: {}", e.getMessage());
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
