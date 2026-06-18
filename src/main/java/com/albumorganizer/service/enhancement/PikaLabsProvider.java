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

/**
 * Pika Labs cloud video enhancement provider.
 * Submits a video to the Pika v2 API for video-to-video enhancement, polls, downloads result.
 * API key obtained from pika.art → Settings → API.
 */
public class PikaLabsProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(PikaLabsProvider.class);
    private static final String BASE_URL = "https://api.pika.art/v2";
    private static final int POLL_INTERVAL_MS = 4000;
    private static final int MAX_POLLS = 450; // 30 minutes

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Gson gson = new Gson();

    public PikaLabsProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override public String getName()           { return "Pika Labs (Cloud)"; }
    @Override public String getShortId()        { return "Pika"; }
    @Override public boolean isConfigured()     { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean supportsPrompt()   { return true; }
    @Override public boolean supportsVideo()    { return true; }
    @Override public String estimatedCostPerImage() { return "Credits-based (see pika.art)"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        Path input = request.inputPath();
        String filename = input.getFileName().toString();
        String prompt = request.prompt() != null && !request.prompt().isBlank()
            ? request.prompt()
            : "Enhance video quality.";

        logger.info("Submitting {} to Pika Labs", filename);

        // Upload the video file first, then reference by URL in the generation request
        String uploadedUrl = uploadVideo(input, filename);
        if (uploadedUrl == null) {
            return EnhancementResult.failure("Pika: failed to upload video");
        }

        JsonObject videoObj = new JsonObject();
        videoObj.addProperty("url", uploadedUrl);

        JsonObject payload = new JsonObject();
        payload.addProperty("promptText", prompt);
        payload.add("video", videoObj);
        payload.addProperty("style", request.param("style", "default"));

        String cleanBody = "{\"promptText\":\"" + prompt + "\",\"video\":{\"url\":\"<uploaded_url>\"}}";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/generate/video-to-video"))
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

        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            logger.error("Pika submit error {}: {}", resp.statusCode(), resp.body());
            return EnhancementResult.failure("Pika API error " + resp.statusCode(), null, cleanBody, resp.body());
        }

        JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
        String jobId = json.has("id") ? json.get("id").getAsString()
                     : json.has("job_id") ? json.get("job_id").getAsString()
                     : null;
        if (jobId == null) {
            return EnhancementResult.failure("Pika: no job ID in response", null, cleanBody, resp.body());
        }

        logger.info("Pika job {} submitted, polling…", jobId);
        String downloadUrl = pollForResult(jobId);
        if (downloadUrl == null) {
            return EnhancementResult.failure("Pika: job did not complete in time (jobId=" + jobId + ")");
        }

        byte[] resultBytes = downloadResult(downloadUrl);
        if (resultBytes == null) {
            return EnhancementResult.failure("Pika: failed to download result for job " + jobId);
        }

        Path outputPath = resolveOutputPath(input, ".mp4", request);
        Files.write(outputPath, resultBytes);
        logger.info("Pika result saved to {}", outputPath);
        return EnhancementResult.ok(outputPath, cleanBody, "");
    }

    private String uploadVideo(Path input, String filename) throws IOException {
        byte[] videoBytes = Files.readAllBytes(input);
        String boundary = UUID.randomUUID().toString();

        String header = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
            "Content-Type: video/mp4\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes();
        byte[] footerBytes = footer.getBytes();
        byte[] body = new byte[headerBytes.length + videoBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(videoBytes, 0, body, headerBytes.length, videoBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + videoBytes.length, footerBytes.length);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/uploads"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .timeout(Duration.ofMinutes(10))
            .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                logger.error("Pika upload error {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
            return json.has("url") ? json.get("url").getAsString() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String pollForResult(String jobId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/jobs/" + jobId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) continue;
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                String status = json.has("status") ? json.get("status").getAsString() : "";
                logger.debug("Pika job {} status: {}", jobId, status);
                if ("completed".equalsIgnoreCase(status) || "finished".equalsIgnoreCase(status)) {
                    if (json.has("video") && json.get("video").isJsonObject()) {
                        JsonObject video = json.getAsJsonObject("video");
                        if (video.has("url")) return video.get("url").getAsString();
                    }
                    if (json.has("result_url")) return json.get("result_url").getAsString();
                    if (json.has("output_url")) return json.get("output_url").getAsString();
                }
                if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    logger.error("Pika job {} failed: {}", jobId, resp.body());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                logger.debug("Pika poll error: {}", e.getMessage());
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
            logger.error("Pika download error: {}", e.getMessage());
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
