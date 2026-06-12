package com.albumorganizer.service.enhancement;

import java.awt.Dimension;
import java.nio.file.Path;
import java.util.Map;

public record EnhancementRequest(
    Path inputPath,
    String prompt,
    Dimension targetSize,
    Path outputDir,
    Map<String, String> params,
    Path customOutputPath
) {
    public EnhancementRequest(Path inputPath, String prompt, Dimension targetSize) {
        this(inputPath, prompt, targetSize, null, Map.of(), null);
    }
    public EnhancementRequest(Path inputPath, String prompt, Dimension targetSize, Path outputDir) {
        this(inputPath, prompt, targetSize, outputDir, Map.of(), null);
    }
    public EnhancementRequest(Path inputPath, String prompt, Dimension targetSize, Path outputDir, Map<String, String> params) {
        this(inputPath, prompt, targetSize, outputDir, params, null);
    }

    public String param(String key) { return params != null ? params.get(key) : null; }
    public String param(String key, String defaultValue) {
        String v = param(key);
        return v != null ? v : defaultValue;
    }
}
