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
 * Provider for InvokeAI local server (default port 9090).
 * Uses the /api/v1/images/i2i (img2img) endpoint.
 */
public class InvokeAIProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(InvokeAIProvider.class);

    private final String baseUrl;
    private final Gson gson = new Gson();

    public InvokeAIProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:9090";
    }

    @Override public String getName() { return "InvokeAI (Local)"; }
    @Override public String getShortId() { return "invokeai"; }
    @Override public boolean isConfigured() { return baseUrl != null && !baseUrl.isBlank(); }
    @Override public boolean supportsPrompt() { return true; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = ImageUtils.loadAsJpeg(request.inputPath());
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = "image/jpeg";

        String prompt = request.prompt() != null ? request.prompt() : "enhance, high quality, detailed";
        int width  = request.targetSize() != null ? request.targetSize().width  : 1024;
        int height = request.targetSize() != null ? request.targetSize().height : 1024;

        // Upload source image
        String imageId = uploadImage(b64, mimeType, request.inputPath().getFileName().toString());
        if (imageId == null) {
            return EnhancementResult.failure("InvokeAI image upload failed");
        }

        // Queue img2img job via graph API
        JsonObject payload = buildImgToImgGraph(imageId, prompt, width, height);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest enqueueReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v1/queue/default/enqueue_batch"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .timeout(Duration.ofSeconds(30))
            .build();

        try {
            HttpResponse<String> enqueueResp = client.send(enqueueReq, HttpResponse.BodyHandlers.ofString());
            if (enqueueResp.statusCode() != 200 && enqueueResp.statusCode() != 201) {
                return EnhancementResult.failure("InvokeAI enqueue error HTTP " + enqueueResp.statusCode()
                    + ": " + enqueueResp.body());
            }
            JsonObject enqueueJson = gson.fromJson(enqueueResp.body(), JsonObject.class);
            String batchId = enqueueJson.getAsJsonObject("batch").get("batch_id").getAsString();

            // Poll until complete
            String outputImageName = pollBatch(client, batchId);
            if (outputImageName == null) {
                return EnhancementResult.failure("InvokeAI did not produce output within timeout");
            }

            // Download result
            HttpRequest dlReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/images/i/" + outputImageName + "/full"))
                .GET().timeout(Duration.ofSeconds(60)).build();
            HttpResponse<byte[]> dlResp = client.send(dlReq, HttpResponse.BodyHandlers.ofByteArray());
            if (dlResp.statusCode() != 200) {
                return EnhancementResult.failure("InvokeAI download failed HTTP " + dlResp.statusCode());
            }

            Path outputPath = ImageUtils.saveAsJpeg(dlResp.body(), request.inputPath(), getShortId(), request.outputDir());
            logger.info("Saved InvokeAI enhanced image to {}", outputPath);
            return EnhancementResult.ok(outputPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
    }

    private String uploadImage(String b64, String mimeType, String filename) throws IOException {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String nl = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(nl);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"").append(nl);
        sb.append("Content-Type: ").append(mimeType).append(nl).append(nl);
        byte[] header = sb.toString().getBytes();
        byte[] imgBytes = Base64.getDecoder().decode(b64);
        String footer = nl + "--" + boundary + "--" + nl;

        byte[] body = new byte[header.length + imgBytes.length + footer.length()];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(imgBytes, 0, body, header.length, imgBytes.length);
        System.arraycopy(footer.getBytes(), 0, body, header.length + imgBytes.length, footer.length());

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/images/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) return null;
            return gson.fromJson(resp.body(), JsonObject.class).get("image_name").getAsString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String pollBatch(HttpClient client, String batchId) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + 10 * 60 * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(2000);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/queue/default/b/" + batchId + "/status"))
                .GET().timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) continue;
            JsonObject status = gson.fromJson(resp.body(), JsonObject.class);
            String state = status.get("status").getAsString();
            if ("completed".equals(state)) {
                // Fetch outputs for the batch
                HttpRequest outReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/images/?batch_id=" + batchId + "&is_intermediate=false&limit=1"))
                    .GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> outResp = client.send(outReq, HttpResponse.BodyHandlers.ofString());
                if (outResp.statusCode() != 200) return null;
                JsonObject outJson = gson.fromJson(outResp.body(), JsonObject.class);
                if (outJson.getAsJsonArray("items").size() > 0) {
                    return outJson.getAsJsonArray("items").get(0).getAsJsonObject()
                        .get("image_name").getAsString();
                }
            } else if ("failed".equals(state) || "cancelled".equals(state)) {
                return null;
            }
        }
        return null;
    }

    private JsonObject buildImgToImgGraph(String imageId, String prompt, int width, int height) {
        // InvokeAI batch API wraps a graph
        JsonObject graph = new JsonObject();
        graph.addProperty("id", "enhance_graph");

        com.google.gson.JsonArray nodes = new com.google.gson.JsonArray();

        JsonObject imgNode = new JsonObject();
        imgNode.addProperty("id", "src_image");
        imgNode.addProperty("type", "img_i2i");
        imgNode.addProperty("image_name", imageId);
        imgNode.addProperty("prompt", prompt);
        imgNode.addProperty("negative_prompt", "blurry, low quality, artifacts");
        imgNode.addProperty("width", width);
        imgNode.addProperty("height", height);
        imgNode.addProperty("steps", 20);
        imgNode.addProperty("cfg_scale", 7.5);
        imgNode.addProperty("strength", 0.4);
        imgNode.addProperty("is_intermediate", false);
        nodes.add(imgNode);

        graph.add("nodes", nodes);
        graph.add("edges", new com.google.gson.JsonArray());

        JsonObject batch = new JsonObject();
        batch.add("graph", graph);
        batch.add("runs", new com.google.gson.JsonPrimitive(1));

        JsonObject payload = new JsonObject();
        payload.add("batch", batch);
        return payload;
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "jpg";
    }
}
