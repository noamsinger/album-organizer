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
import java.util.UUID;

/**
 * Topaz Video AI cloud provider.
 * Submits a video to the Topaz Labs cloud API, polls until done, downloads result.
 * API key obtained from account.topazlabs.com.
 */
public class TopazVideoAIProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(TopazVideoAIProvider.class);
    private static final String BASE_URL = "https://api.topazlabs.com/video/v1";
    private static final int POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLLS = 720; // 1 hour

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Gson gson = new Gson();

    public TopazVideoAIProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override public String getName()           { return "Topaz Video AI (Cloud)"; }
    @Override public String getShortId()        { return "Topaz"; }
    @Override public boolean isConfigured()     { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean supportsPrompt()   { return false; }
    @Override public boolean supportsVideo()    { return true; }
    @Override public String estimatedCostPerImage() { return "~$0.01–$0.05 / min of video"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        Path input = request.inputPath();
        String filename = input.getFileName().toString();
        String model = request.param("model", "enhance-4x");

        logger.info("Uploading {} to Topaz Video AI (model={})", filename, model);

        // Step 1: upload video
        String jobId = uploadAndSubmit(input, filename, model);
        if (jobId == null) {
            return EnhancementResult.failure("Topaz: failed to submit job");
        }

        // Step 2: poll for completion
        logger.info("Topaz job {} submitted, polling…", jobId);
        String downloadUrl = pollForResult(jobId);
        if (downloadUrl == null) {
            return EnhancementResult.failure("Topaz: job did not complete in time (jobId=" + jobId + ")");
        }

        // Step 3: download result
        byte[] resultBytes = downloadResult(downloadUrl);
        if (resultBytes == null) {
            return EnhancementResult.failure("Topaz: failed to download result for job " + jobId);
        }

        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : ".mp4";
        Path outputPath = resolveOutputPath(input, ext, request);
        Files.write(outputPath, resultBytes);
        logger.info("Topaz result saved to {}", outputPath);
        return EnhancementResult.ok(outputPath);
    }

    private String uploadAndSubmit(Path input, String filename, String model) throws IOException {
        byte[] videoBytes = Files.readAllBytes(input);
        String boundary = UUID.randomUUID().toString();

        String bodyPart = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
            "Content-Type: video/mp4\r\n\r\n";
        String modelPart = "\r\n--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + model +
            "\r\n--" + boundary + "--\r\n";

        byte[] prefix = bodyPart.getBytes();
        byte[] suffix = modelPart.getBytes();
        byte[] body = new byte[prefix.length + videoBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(videoBytes, 0, body, prefix.length, videoBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + videoBytes.length, suffix.length);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/jobs"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .timeout(Duration.ofMinutes(10))
            .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                logger.error("Topaz upload error {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
            return json.has("job_id") ? json.get("job_id").getAsString()
                 : json.has("id")     ? json.get("id").getAsString()
                 : null;
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
                logger.debug("Topaz job {} status: {}", jobId, status);
                if ("completed".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status)) {
                    if (json.has("output_url")) return json.get("output_url").getAsString();
                    if (json.has("download_url")) return json.get("download_url").getAsString();
                    if (json.has("result") && json.get("result").isJsonObject()) {
                        JsonObject result = json.getAsJsonObject("result");
                        if (result.has("url")) return result.get("url").getAsString();
                    }
                    return null;
                }
                if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    logger.error("Topaz job {} failed: {}", jobId, resp.body());
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                logger.debug("Topaz poll error: {}", e.getMessage());
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
            logger.error("Topaz download error: {}", e.getMessage());
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
