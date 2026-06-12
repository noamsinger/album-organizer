package com.albumorganizer.service.enhancement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class GeminiProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(GeminiProvider.class);
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";

    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public GeminiProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override public String getName()          { return "Google Gemini (Cloud)"; }
    @Override public String getShortId()       { return "Gemini"; }
    @Override public boolean isConfigured()    { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean supportsPrompt()  { return true; }
    @Override public String estimatedCostPerImage() { return "~$0.04 / image (gemini-2.5-flash-image)"; }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        byte[] imageBytes = ImageUtils.loadAsJpeg(request.inputPath());
        String mimeType   = "image/jpeg";
        String b64Image   = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = (request.prompt() != null && !request.prompt().isBlank())
            ? request.prompt()
            : "Enhance this image: improve sharpness, color, and overall quality.";

        String body = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "%s" },
                    { "inlineData": { "mimeType": "%s", "data": "%s" } }
                  ]
                }
              ],
              "generationConfig": { "responseModalities": ["IMAGE", "TEXT"], "temperature": %s }
            }
            """.formatted(escapeJson(prompt), mimeType, b64Image,
                request.param("temperature", "1.0"));

        String cleanBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": "%s" },
                    { "inlineData": { "mimeType": "%s", "data": "<BASE64_IMAGE_DATA_TRUNCATED>" } }
                  ]
                }
              ],
              "generationConfig": { "responseModalities": ["IMAGE", "TEXT"], "temperature": %s }
            }
            """.formatted(escapeJson(prompt), mimeType,
                request.param("temperature", "1.0"));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL + "?key=" + apiKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("Request interrupted");
        }

        if (resp.statusCode() != 200) {
            String responseBody = resp.body();
            logger.error("Gemini API error {}: {}", resp.statusCode(), responseBody);
            String errMsg = responseBody;
            try {
                com.google.gson.JsonObject errJson = new com.google.gson.Gson().fromJson(responseBody, com.google.gson.JsonObject.class);
                if (errJson.has("error")) {
                    com.google.gson.JsonObject err = errJson.getAsJsonObject("error");
                    errMsg = err.has("message") ? err.get("message").getAsString() : err.toString();
                }
            } catch (Exception ignored) {}
            String technicalDetail = "Request Sent:\n" + cleanBody + "\n\nResponse Received (HTTP " + resp.statusCode() + "):\n" + responseBody;
            return EnhancementResult.failure("Gemini API error " + resp.statusCode() + ": " + errMsg, technicalDetail, cleanBody, responseBody);
        }

        byte[] resultBytes = extractImageBytes(resp.body());
        if (resultBytes == null) {
            logger.error("Gemini response contained no image data. Body snippet: {}",
                resp.body().length() > 500 ? resp.body().substring(0, 500) : resp.body());
            String technicalDetail = "Request Sent:\n" + cleanBody + "\n\nResponse Received (HTTP " + resp.statusCode() + "):\n" + resp.body();
            return EnhancementResult.failure("Gemini response contained no image data", technicalDetail, cleanBody, resp.body());
        }

        Path outputPath = request.customOutputPath() != null
            ? ImageUtils.saveAsJpeg(resultBytes, request.customOutputPath())
            : ImageUtils.saveAsJpeg(resultBytes, request.inputPath(), getShortId(), request.outputDir());
        logger.info("Gemini enhanced image saved to {}", outputPath);
        return EnhancementResult.ok(outputPath, cleanBody, resp.body());
    }

    private byte[] extractImageBytes(String json) {
        String marker = "\"inlineData\"";
        int start = json.indexOf(marker);
        if (start == -1) return null;

        // Find "data": after inlineData
        int dataStart = json.indexOf("\"data\":", start);
        if (dataStart == -1) return null;
        dataStart = json.indexOf('"', dataStart + 7) + 1; // skip past opening quote
        if (dataStart <= 0) return null;

        // Walk to end of JSON string, respecting backslash escapes
        int end = dataStart;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') { end += 2; continue; }
            if (c == '"') break;
            end++;
        }
        if (end >= json.length()) return null;

        try {
            return Base64.getDecoder().decode(json.substring(dataStart, end));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode Gemini image data", e);
            return null;
        }
    }

    private String extractError(String json) {
        String marker = "\"message\":\"";
        int start = json.indexOf(marker);
        if (start == -1) return json.length() > 200 ? json.substring(0, 200) : json;
        start += marker.length();
        int end = json.indexOf('"', start);
        return end == -1 ? json.substring(start) : json.substring(start, end);
    }

    private String detectMime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif"))  return "image/gif";
        return "image/jpeg";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
