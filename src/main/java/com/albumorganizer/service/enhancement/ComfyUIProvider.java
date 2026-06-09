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
import java.util.UUID;

/**
 * ComfyUI local server provider (default port 8188).
 *
 * Images  — img2img workflow: LoadImage → VAEEncode → KSampler → VAEDecode → SaveImage
 * Videos  — requires the ComfyUI-VideoHelperSuite (VHS) custom node.
 *           Workflow: VHS_LoadVideo → VAEEncode (per-frame) → KSampler → VAEDecode → VHS_VideoCombine
 *
 * Install VHS: cd ComfyUI/custom_nodes && git clone https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite
 */
public class ComfyUIProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(ComfyUIProvider.class);

    private static final long POLL_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long POLL_INTERVAL_MS = 2_000L;

    private final String baseUrl;
    private final Gson gson = new Gson();

    public ComfyUIProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "http://localhost:8188";
    }

    @Override public String getName()       { return "ComfyUI (Local)"; }
    @Override public String getShortId()    { return "comfyui"; }
    @Override public boolean isConfigured() { return baseUrl != null && !baseUrl.isBlank(); }
    @Override public boolean supportsPrompt()  { return true; }
    @Override public boolean supportsVideo()   { return true; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        String filename = request.inputPath().getFileName().toString().toLowerCase();
        boolean isVideo = filename.endsWith(".mp4") || filename.endsWith(".mov")
            || filename.endsWith(".avi") || filename.endsWith(".mkv")
            || filename.endsWith(".webm");
        return isVideo ? enhanceVideo(request) : enhanceImage(request);
    }

    // ── Image enhancement ────────────────────────────────────────────────────

    private EnhancementResult enhanceImage(EnhancementRequest request) throws IOException {
        byte[] imageBytes;
        try {
            imageBytes = ImageUtils.loadAsJpeg(request.inputPath());
        } catch (IOException e) {
            return EnhancementResult.failure("Could not read input image.", e.toString());
        }
        String clientId = UUID.randomUUID().toString();
        String uploadedName;
        try {
            uploadedName = uploadFile(imageBytes, "image/jpeg",
                request.inputPath().getFileName().toString(), "/upload/image");
        } catch (java.net.ConnectException e) {
            return EnhancementResult.failure(
                "Cannot reach ComfyUI at " + baseUrl + ". Make sure it is running.",
                e.toString());
        }
        if (uploadedName == null) return EnhancementResult.failure(
            "ComfyUI rejected the image upload. Check the server logs.",
            "HTTP non-200 from " + baseUrl + "/upload/image");

        String prompt = coalesce(request.prompt(), "enhance, high quality, detailed");
        int width  = request.targetSize() != null ? request.targetSize().width  : 1024;
        int height = request.targetSize() != null ? request.targetSize().height : 1024;

        JsonObject workflow = buildImageWorkflow(uploadedName, prompt, width, height);
        HttpClient client = newClient();
        String promptId;
        try {
            promptId = queuePrompt(client, workflow, clientId);
        } catch (java.net.ConnectException e) {
            return EnhancementResult.failure(
                "Cannot reach ComfyUI at " + baseUrl + ". Make sure it is running.",
                e.toString());
        }
        if (promptId == null) return EnhancementResult.failure(
            "ComfyUI refused the workflow. A required node may be missing (e.g. the checkpoint model).",
            "HTTP non-200 from " + baseUrl + "/prompt");

        byte[] result;
        try {
            result = pollImageOutput(client, promptId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
        if (result == null) return EnhancementResult.failure(
            "ComfyUI did not finish within 10 minutes.",
            "Prompt ID: " + promptId + " — check ComfyUI queue for errors.");

        Path outputPath = ImageUtils.saveAsJpeg(result, request.inputPath(), getShortId(), request.outputDir());
        logger.info("Saved ComfyUI enhanced image to {}", outputPath);
        return EnhancementResult.ok(outputPath);
    }

    // ── Video enhancement ────────────────────────────────────────────────────

    private EnhancementResult enhanceVideo(EnhancementRequest request) throws IOException {
        byte[] videoBytes;
        try {
            videoBytes = Files.readAllBytes(request.inputPath());
        } catch (IOException e) {
            return EnhancementResult.failure("Could not read input video.", e.toString());
        }
        String origFilename = request.inputPath().getFileName().toString();
        String clientId = UUID.randomUUID().toString();

        String uploadedName;
        try {
            uploadedName = uploadFile(videoBytes, "video/mp4", origFilename, "/upload/image");
        } catch (java.net.ConnectException e) {
            return EnhancementResult.failure(
                "Cannot reach ComfyUI at " + baseUrl + ". Make sure it is running.",
                e.toString());
        }
        if (uploadedName == null) return EnhancementResult.failure(
            "ComfyUI rejected the video upload. Check the server logs.",
            "HTTP non-200 from " + baseUrl + "/upload/image");

        String prompt = coalesce(request.prompt(), "enhance, high quality, detailed");
        JsonObject workflow = buildVideoWorkflow(uploadedName, prompt);
        HttpClient client = newClient();
        String promptId;
        try {
            promptId = queuePrompt(client, workflow, clientId);
        } catch (java.net.ConnectException e) {
            return EnhancementResult.failure(
                "Cannot reach ComfyUI at " + baseUrl + ". Make sure it is running.",
                e.toString());
        }
        if (promptId == null) return EnhancementResult.failure(
            "ComfyUI refused the video workflow. The ComfyUI-VideoHelperSuite (VHS) node may not be installed.",
            "HTTP non-200 from " + baseUrl + "/prompt — install: https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite");

        byte[] result;
        try {
            result = pollVideoOutput(client, promptId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }
        if (result == null) return EnhancementResult.failure(
            "ComfyUI did not finish the video within 10 minutes.",
            "Prompt ID: " + promptId + " — check ComfyUI queue for errors.");

        Path outputPath = saveVideo(result, request.inputPath(), request.outputDir());
        logger.info("Saved ComfyUI enhanced video to {}", outputPath);
        return EnhancementResult.ok(outputPath);
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    private String uploadFile(byte[] fileBytes, String mimeType, String filename, String endpoint) throws IOException {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String nl = "\r\n";
        byte[] header = (
            "--" + boundary + nl
            + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"" + nl
            + "Content-Type: " + mimeType + nl + nl
        ).getBytes();
        byte[] footer = (nl + "--" + boundary + "--" + nl).getBytes();

        byte[] body = new byte[header.length + fileBytes.length + footer.length];
        System.arraycopy(header,     0, body, 0,                                 header.length);
        System.arraycopy(fileBytes,  0, body, header.length,                     fileBytes.length);
        System.arraycopy(footer,     0, body, header.length + fileBytes.length,  footer.length);

        try {
            HttpClient client = newClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(60))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                logger.warn("ComfyUI upload failed HTTP {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            return gson.fromJson(resp.body(), JsonObject.class).get("name").getAsString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ── Queue ────────────────────────────────────────────────────────────────

    private String queuePrompt(HttpClient client, JsonObject workflow, String clientId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.add("prompt", workflow);
        payload.addProperty("client_id", clientId);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/prompt"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .timeout(Duration.ofSeconds(30))
            .build();
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                logger.warn("ComfyUI queue error HTTP {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            return gson.fromJson(resp.body(), JsonObject.class).get("prompt_id").getAsString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ── Poll ─────────────────────────────────────────────────────────────────

    private byte[] pollImageOutput(HttpClient client, String promptId) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            JsonObject outputs = fetchOutputs(client, promptId);
            if (outputs == null) continue;
            for (var entry : outputs.entrySet()) {
                JsonObject node = entry.getValue().getAsJsonObject();
                if (!node.has("images")) continue;
                JsonObject img = node.getAsJsonArray("images").get(0).getAsJsonObject();
                byte[] bytes = fetchView(client, img);
                if (bytes != null) return bytes;
            }
        }
        return null;
    }

    private byte[] pollVideoOutput(HttpClient client, String promptId) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            JsonObject outputs = fetchOutputs(client, promptId);
            if (outputs == null) continue;
            for (var entry : outputs.entrySet()) {
                JsonObject node = entry.getValue().getAsJsonObject();
                // VHS_VideoCombine outputs under "gifs" or "videos"
                String key = node.has("gifs") ? "gifs" : node.has("videos") ? "videos" : null;
                if (key == null) continue;
                JsonObject vid = node.getAsJsonArray(key).get(0).getAsJsonObject();
                byte[] bytes = fetchView(client, vid);
                if (bytes != null) return bytes;
            }
        }
        return null;
    }

    private JsonObject fetchOutputs(HttpClient client, String promptId) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/history/" + promptId))
                .GET().timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonObject history = gson.fromJson(resp.body(), JsonObject.class);
            if (!history.has(promptId)) return null;
            return history.getAsJsonObject(promptId).getAsJsonObject("outputs");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private byte[] fetchView(HttpClient client, JsonObject item) throws IOException {
        String name      = item.get("filename").getAsString();
        String subfolder = item.has("subfolder") ? item.get("subfolder").getAsString() : "";
        String type      = item.has("type")      ? item.get("type").getAsString()      : "output";
        String url = baseUrl + "/view?filename=" + name + "&subfolder=" + subfolder + "&type=" + type;
        try {
            HttpResponse<byte[]> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ── Save video ───────────────────────────────────────────────────────────

    private Path saveVideo(byte[] videoBytes, Path inputPath, Path outputDir) throws IOException {
        String filename = inputPath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String base = dot >= 0 ? filename.substring(0, dot) : filename;
        String ext  = dot >= 0 ? filename.substring(dot)    : ".mp4";
        long epoch = System.currentTimeMillis() / 1000;
        String outName = String.format("%s_AI-comfyui-%d%s", base, epoch, ext);

        Path dir = outputDir != null ? outputDir : inputPath.getParent();
        Files.createDirectories(dir);
        Path out = dir.resolve(outName);
        Files.write(out, videoBytes);
        return out;
    }

    // ── Workflow builders ────────────────────────────────────────────────────

    private JsonObject buildImageWorkflow(String imageName, String prompt, int width, int height) {
        JsonObject wf = new JsonObject();

        // Node 1 — LoadImage
        wf.add("1", node("LoadImage", inputs -> inputs.addProperty("image", imageName)));

        // Node 2 — positive CLIPTextEncode
        wf.add("2", node("CLIPTextEncode", inputs -> {
            inputs.addProperty("text", prompt);
            inputs.add("clip", ref("4", 1));
        }));

        // Node 3 — negative CLIPTextEncode
        wf.add("3", node("CLIPTextEncode", inputs -> {
            inputs.addProperty("text", "blurry, low quality, artifacts");
            inputs.add("clip", ref("4", 1));
        }));

        // Node 4 — CheckpointLoaderSimple
        wf.add("4", node("CheckpointLoaderSimple",
            inputs -> inputs.addProperty("ckpt_name", "v1-5-pruned-emaonly.ckpt")));

        // Node 5 — VAEEncode
        wf.add("5", node("VAEEncode", inputs -> {
            inputs.add("pixels", ref("1", 0));
            inputs.add("vae", ref("4", 2));
        }));

        // Node 6 — KSampler
        wf.add("6", node("KSampler", inputs -> {
            inputs.add("model",        ref("4", 0));
            inputs.add("positive",     ref("2", 0));
            inputs.add("negative",     ref("3", 0));
            inputs.add("latent_image", ref("5", 0));
            inputs.addProperty("seed",         (long) (Math.random() * Long.MAX_VALUE));
            inputs.addProperty("steps",        20);
            inputs.addProperty("cfg",          7.0);
            inputs.addProperty("sampler_name", "euler");
            inputs.addProperty("scheduler",    "normal");
            inputs.addProperty("denoise",      0.4);
        }));

        // Node 7 — VAEDecode
        wf.add("7", node("VAEDecode", inputs -> {
            inputs.add("samples", ref("6", 0));
            inputs.add("vae",     ref("4", 2));
        }));

        // Node 8 — SaveImage
        wf.add("8", node("SaveImage", inputs -> {
            inputs.add("images", ref("7", 0));
            inputs.addProperty("filename_prefix", "enhanced");
        }));

        return wf;
    }

    /**
     * VHS video workflow.
     * Requires ComfyUI-VideoHelperSuite: https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite
     *
     * Node map:
     *   1  VHS_LoadVideo         — load the uploaded video
     *   2  CheckpointLoaderSimple
     *   3  positive CLIPTextEncode
     *   4  negative CLIPTextEncode
     *   5  VAEEncode (per-frame, via VHS_VideoBatchNumFrames implicit batching)
     *   6  KSampler
     *   7  VAEDecode
     *   8  VHS_VideoCombine      — reassemble frames into video
     */
    private JsonObject buildVideoWorkflow(String videoName, String prompt) {
        JsonObject wf = new JsonObject();

        // Node 1 — VHS_LoadVideo
        wf.add("1", node("VHS_LoadVideo", inputs -> {
            inputs.addProperty("video",          videoName);
            inputs.addProperty("force_rate",     0);       // keep original fps
            inputs.addProperty("force_size",     "Disabled");
            inputs.addProperty("custom_width",   512);
            inputs.addProperty("custom_height",  512);
            inputs.addProperty("frame_load_cap", 0);       // 0 = all frames
            inputs.addProperty("skip_first_frames", 0);
            inputs.addProperty("select_every_nth",  1);
        }));

        // Node 2 — CheckpointLoaderSimple
        wf.add("2", node("CheckpointLoaderSimple",
            inputs -> inputs.addProperty("ckpt_name", "v1-5-pruned-emaonly.ckpt")));

        // Node 3 — positive prompt
        wf.add("3", node("CLIPTextEncode", inputs -> {
            inputs.addProperty("text", prompt);
            inputs.add("clip", ref("2", 1));
        }));

        // Node 4 — negative prompt
        wf.add("4", node("CLIPTextEncode", inputs -> {
            inputs.addProperty("text", "blurry, low quality, artifacts, watermark");
            inputs.add("clip", ref("2", 1));
        }));

        // Node 5 — VAEEncode (frames from VHS_LoadVideo output 0 = IMAGE batch)
        wf.add("5", node("VAEEncode", inputs -> {
            inputs.add("pixels", ref("1", 0));
            inputs.add("vae",    ref("2", 2));
        }));

        // Node 6 — KSampler
        wf.add("6", node("KSampler", inputs -> {
            inputs.add("model",        ref("2", 0));
            inputs.add("positive",     ref("3", 0));
            inputs.add("negative",     ref("4", 0));
            inputs.add("latent_image", ref("5", 0));
            inputs.addProperty("seed",         (long) (Math.random() * Long.MAX_VALUE));
            inputs.addProperty("steps",        15);
            inputs.addProperty("cfg",          7.0);
            inputs.addProperty("sampler_name", "euler");
            inputs.addProperty("scheduler",    "normal");
            inputs.addProperty("denoise",      0.35);
        }));

        // Node 7 — VAEDecode
        wf.add("7", node("VAEDecode", inputs -> {
            inputs.add("samples", ref("6", 0));
            inputs.add("vae",     ref("2", 2));
        }));

        // Node 8 — VHS_VideoCombine
        wf.add("8", node("VHS_VideoCombine", inputs -> {
            inputs.add("images",          ref("7", 0));
            inputs.add("audio",           ref("1", 2));   // VHS_LoadVideo output 2 = audio
            inputs.addProperty("frame_rate",    24);
            inputs.addProperty("loop_count",    0);
            inputs.addProperty("filename_prefix", "enhanced_video");
            inputs.addProperty("format",          "video/h264-mp4");
            inputs.addProperty("pingpong",        false);
            inputs.addProperty("save_output",     true);
        }));

        return wf;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JsonObject node(String classType, java.util.function.Consumer<JsonObject> fillInputs) {
        JsonObject n = new JsonObject();
        n.addProperty("class_type", classType);
        JsonObject inputs = new JsonObject();
        fillInputs.accept(inputs);
        n.add("inputs", inputs);
        return n;
    }

    private JsonArray ref(String nodeId, int slot) {
        JsonArray a = new JsonArray();
        a.add(nodeId);
        a.add(slot);
        return a;
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private String coalesce(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
