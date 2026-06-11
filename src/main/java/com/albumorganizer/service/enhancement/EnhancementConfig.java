package com.albumorganizer.service.enhancement;

import java.util.List;

public record EnhancementConfig(
    boolean stabilityAiEnabled,
    String stabilityAiKey,
    boolean openAiEnabled,
    String openAiKey,
    boolean geminiEnabled,
    String geminiKey,
    double geminiTemperature,
    boolean grokEnabled,
    String grokKey,
    String grokModel,
    boolean sdLocalEnabled,
    String sdLocalUrl,
    boolean realEsrganEnabled,
    String realEsrganModelPath,
    boolean comfyUiEnabled,
    String comfyUiUrl,
    String comfyUiCheckpoint,
    List<String> comfyUiCheckpoints,
    boolean invokeAiEnabled,
    String invokeAiUrl,
    List<NamedPrompt> savedPrompts,
    List<String> checkedPromptTitles,
    boolean debugProtocol
) {

    public static EnhancementConfig defaults() {
        return new EnhancementConfig(
            false, "",
            false, "",
            false, "", 1.0,
            false, "", "grok-imagine-image-quality",
            false, "http://localhost:7860",
            false, "",
            false, "http://localhost:8188", "", List.of(),
            false, "http://localhost:9090",
            List.of(),
            List.of(),
            false
        );
    }
}
