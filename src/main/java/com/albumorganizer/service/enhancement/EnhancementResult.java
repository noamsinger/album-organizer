package com.albumorganizer.service.enhancement;

import java.nio.file.Path;

public record EnhancementResult(
    Path outputPath,
    boolean success,
    String errorMessage,
    String technicalDetail
) {

    public static EnhancementResult ok(Path outputPath) {
        return new EnhancementResult(outputPath, true, null, null);
    }

    /** Failure with a user-friendly message only. */
    public static EnhancementResult failure(String errorMessage) {
        return new EnhancementResult(null, false, errorMessage, null);
    }

    /** Failure with a user-friendly message and a technical detail for the expandable section. */
    public static EnhancementResult failure(String errorMessage, String technicalDetail) {
        return new EnhancementResult(null, false, errorMessage, technicalDetail);
    }
}
