package com.albumorganizer.service.enhancement;

import java.awt.Dimension;
import java.nio.file.Path;

public record EnhancementRequest(
    Path inputPath,
    String prompt,
    Dimension targetSize,
    Path outputDir
) {
    public EnhancementRequest(Path inputPath, String prompt, Dimension targetSize) {
        this(inputPath, prompt, targetSize, null);
    }
}
