package com.albumorganizer.service.enhancement;

import java.nio.file.Path;

public record EnhancementResult(
    Path outputPath,
    boolean success,
    String errorMessage
) {

    public static EnhancementResult ok(Path outputPath) {
        return new EnhancementResult(outputPath, true, null);
    }

    public static EnhancementResult failure(String errorMessage) {
        return new EnhancementResult(null, false, errorMessage);
    }
}
