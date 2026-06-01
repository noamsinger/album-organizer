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
import java.util.UUID;

/**
 * Provider for ComfyUI local server (default port 8188).
 * Uses the /prompt API with an img2img upscale workflow.
 */
public class ComfyUIProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(ComfyUIProvider.class);

    private final String baseUrl;
    private final Gson gson = new Gson();

    public ComfyUIProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:8188";
    }

    @Override public String getName() { return "ComfyUI (Local)"; }
    @Override public String getShortId() { return "comfyui"; }
    @Override public boolean isConfigured() { return baseUrl != null && !baseUrl.isBlank(); }
    @Override public boolean supportsPrompt() { return true; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = ImageUtils.loadAsJpeg(request.inputPath());
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        String clientId = UUID.randomUUID().toString();

        // Upload image first
        String uploadedName = uploadImage(b64, clientId, request.inputPath().getFileName().toString());
        if (uploadedName == null) {
            return EnhancementResult.failure("ComfyUI image upload failed");
        }

        // Build a minimal img2img workflow
        String prompt = request.prompt() != null ? request.prompt() : "enhance, high quality, detailed";
        int width  = request.targetSize() != null ? request.targetSize().width  : 1024;
        int height = request.targetSize() != null ? request.targetSize().height : 1024;

        JsonObject workflow = buildWorkflow(uploadedName, prompt, width, height);
        JsonObject payload = new JsonObject();
        payload.add("prompt", workflow);
        payload.addProperty("client_id", clientId);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest queueReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/prompt"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .timeout(Duration.ofSeconds(30))
            .build();

        try {
            HttpResponse<String> queueResp = client.send(queueReq, HttpResponse.BodyHandlers.ofString());
            if (queueResp.statusCode() != 200) {
                return EnhancementResult.failure("ComfyUI queue error HTTP " + queueResp.statusCode());
            }
            String promptId = gson.fromJson(queueResp.body(), JsonObject.class)
                .get("prompt_id").getAsString();

            // Poll history until done
            byte[] resultBytes = pollResult(client, promptId);
            if (resultBytes == null) {
                return EnhancementResult.failure("ComfyUI did not produce output within timeout");
            }
            Path outputPath = ImageUtils.saveAsJpeg(resultBytes, request.inputPath(), getShortId(), request.outputDir());
            logger.info("Saved ComfyUI enhanced image to {}", outputPath);
            return EnhancementResult.ok(outputPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
    }

    private String uploadImage(String b64, String clientId, String filename) throws IOException {
        // ComfyUI /upload/image expects multipart
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String nl = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(nl);
        sb.append("Content-Disposition: form-data; name=\"image\"; filename=\"").append(filename).append("\"").append(nl);
        sb.append("Content-Type: image/jpeg").append(nl).append(nl);
        byte[] header = sb.toString().getBytes();
        byte[] imageBytes = Base64.getDecoder().decode(b64);
        String footer = nl + "--" + boundary + "--" + nl;

        byte[] body = new byte[header.length + imageBytes.length + footer.length()];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(imageBytes, 0, body, header.length, imageBytes.length);
        System.arraycopy(footer.getBytes(), 0, body, header.length + imageBytes.length, footer.length());

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/upload/image"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            return gson.fromJson(resp.body(), JsonObject.class).get("name").getAsString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private byte[] pollResult(HttpClient client, String promptId) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + 10 * 60 * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(2000);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/history/" + promptId))
                .GET().timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) continue;
            JsonObject history = gson.fromJson(resp.body(), JsonObject.class);
            if (!history.has(promptId)) continue;
            JsonObject outputs = history.getAsJsonObject(promptId)
                .getAsJsonObject("outputs");
            // Walk outputs to find first image
            for (var entry : outputs.entrySet()) {
                JsonObject node = entry.getValue().getAsJsonObject();
                if (node.has("images")) {
                    JsonObject img = node.getAsJsonArray("images").get(0).getAsJsonObject();
                    String imgName = img.get("filename").getAsString();
                    String subfolder = img.has("subfolder") ? img.get("subfolder").getAsString() : "";
                    String type = img.has("type") ? img.get("type").getAsString() : "output";
                    String viewUrl = baseUrl + "/view?filename=" + imgName
                        + "&subfolder=" + subfolder + "&type=" + type;
                    HttpRequest viewReq = HttpRequest.newBuilder()
                        .uri(URI.create(viewUrl)).GET().timeout(Duration.ofSeconds(30)).build();
                    HttpResponse<byte[]> viewResp = client.send(viewReq, HttpResponse.BodyHandlers.ofByteArray());
                    if (viewResp.statusCode() == 200) return viewResp.body();
                }
            }
        }
        return null;
    }

    private JsonObject buildWorkflow(String imageName, String prompt, int width, int height) {
        // Minimal img2img workflow: LoadImage → VAEEncode → KSampler → VAEDecode → SaveImage
        JsonObject workflow = new JsonObject();

        JsonObject loadImage = new JsonObject();
        loadImage.addProperty("class_type", "LoadImage");
        JsonObject loadImageInputs = new JsonObject();
        loadImageInputs.addProperty("image", imageName);
        loadImage.add("inputs", loadImageInputs);
        workflow.add("1", loadImage);

        JsonObject clipTextEncode = new JsonObject();
        clipTextEncode.addProperty("class_type", "CLIPTextEncode");
        JsonObject clipInputs = new JsonObject();
        clipInputs.addProperty("text", prompt);
        JsonArray clipModel = new JsonArray(); clipModel.add("4"); clipModel.add(1);
        clipInputs.add("clip", clipModel);
        clipTextEncode.add("inputs", clipInputs);
        workflow.add("2", clipTextEncode);

        JsonObject negEncode = new JsonObject();
        negEncode.addProperty("class_type", "CLIPTextEncode");
        JsonObject negInputs = new JsonObject();
        negInputs.addProperty("text", "blurry, low quality, artifacts");
        JsonArray negClip = new JsonArray(); negClip.add("4"); negClip.add(1);
        negInputs.add("clip", negClip);
        negEncode.add("inputs", negInputs);
        workflow.add("3", negEncode);

        JsonObject checkpointLoader = new JsonObject();
        checkpointLoader.addProperty("class_type", "CheckpointLoaderSimple");
        JsonObject ckptInputs = new JsonObject();
        ckptInputs.addProperty("ckpt_name", "v1-5-pruned-emaonly.ckpt");
        checkpointLoader.add("inputs", ckptInputs);
        workflow.add("4", checkpointLoader);

        JsonObject vaeEncode = new JsonObject();
        vaeEncode.addProperty("class_type", "VAEEncodeForInpaint");
        JsonObject vaeEncInputs = new JsonObject();
        JsonArray imgRef = new JsonArray(); imgRef.add("1"); imgRef.add(0);
        vaeEncInputs.add("pixels", imgRef);
        JsonArray vaeRef = new JsonArray(); vaeRef.add("4"); vaeRef.add(2);
        vaeEncInputs.add("vae", vaeRef);
        vaeEncode.add("inputs", vaeEncInputs);
        workflow.add("5", vaeEncode);

        JsonObject ksampler = new JsonObject();
        ksampler.addProperty("class_type", "KSampler");
        JsonObject ksInputs = new JsonObject();
        JsonArray modelRef = new JsonArray(); modelRef.add("4"); modelRef.add(0);
        ksInputs.add("model", modelRef);
        JsonArray posRef = new JsonArray(); posRef.add("2"); posRef.add(0);
        ksInputs.add("positive", posRef);
        JsonArray negRef = new JsonArray(); negRef.add("3"); negRef.add(0);
        ksInputs.add("negative", negRef);
        JsonArray latentRef = new JsonArray(); latentRef.add("5"); latentRef.add(0);
        ksInputs.add("latent_image", latentRef);
        ksInputs.addProperty("seed", (long)(Math.random() * Long.MAX_VALUE));
        ksInputs.addProperty("steps", 20);
        ksInputs.addProperty("cfg", 7.0);
        ksInputs.addProperty("sampler_name", "euler");
        ksInputs.addProperty("scheduler", "normal");
        ksInputs.addProperty("denoise", 0.4);
        ksampler.add("inputs", ksInputs);
        workflow.add("6", ksampler);

        JsonObject vaeDecode = new JsonObject();
        vaeDecode.addProperty("class_type", "VAEDecode");
        JsonObject vaeDecInputs = new JsonObject();
        JsonArray samplesRef = new JsonArray(); samplesRef.add("6"); samplesRef.add(0);
        vaeDecInputs.add("samples", samplesRef);
        JsonArray vaeRef2 = new JsonArray(); vaeRef2.add("4"); vaeRef2.add(2);
        vaeDecInputs.add("vae", vaeRef2);
        vaeDecode.add("inputs", vaeDecInputs);
        workflow.add("7", vaeDecode);

        JsonObject saveImage = new JsonObject();
        saveImage.addProperty("class_type", "SaveImage");
        JsonObject saveInputs = new JsonObject();
        JsonArray imageRef = new JsonArray(); imageRef.add("7"); imageRef.add(0);
        saveInputs.add("images", imageRef);
        saveInputs.addProperty("filename_prefix", "enhanced");
        saveImage.add("inputs", saveInputs);
        workflow.add("8", saveImage);

        return workflow;
    }
}
