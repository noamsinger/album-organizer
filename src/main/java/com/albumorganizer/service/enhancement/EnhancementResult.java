package com.albumorganizer.service.enhancement;

import java.nio.file.Path;

public record EnhancementResult(
    Path outputPath,
    boolean success,
    String errorMessage,
    String technicalDetail,
    String rawRequestBody,
    String rawResponseBody
) {

    public static EnhancementResult ok(Path outputPath) {
        return new EnhancementResult(outputPath, true, null, null, null, null);
    }

    public static EnhancementResult ok(Path outputPath, String rawRequestBody, String rawResponseBody) {
        return new EnhancementResult(outputPath, true, null, null, rawRequestBody, rawResponseBody);
    }

    public static EnhancementResult failure(String errorMessage) {
        return new EnhancementResult(null, false, errorMessage, null, null, null);
    }

    public static EnhancementResult failure(String errorMessage, String technicalDetail) {
        return new EnhancementResult(null, false, errorMessage, technicalDetail, null, null);
    }

    public static EnhancementResult failure(String errorMessage, String technicalDetail,
                                            String rawRequestBody, String rawResponseBody) {
        return new EnhancementResult(null, false, errorMessage, technicalDetail, rawRequestBody, rawResponseBody);
    }
}
