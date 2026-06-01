package com.albumorganizer.service.enhancement;

import java.util.List;

public record EnhancementConfig(
    boolean stabilityAiEnabled,
    String stabilityAiKey,
    boolean openAiEnabled,
    String openAiKey,
    boolean geminiEnabled,
    String geminiKey,
    boolean grokEnabled,
    String grokKey,
    boolean sdLocalEnabled,
    String sdLocalUrl,
    boolean realEsrganEnabled,
    String realEsrganModelPath,
    boolean comfyUiEnabled,
    String comfyUiUrl,
    boolean invokeAiEnabled,
    String invokeAiUrl,
    List<NamedPrompt> savedPrompts,
    List<String> checkedPromptTitles
) {

    public static EnhancementConfig defaults() {
        return new EnhancementConfig(
            false, "",
            false, "",
            false, "",
            false, "",
            false, "http://localhost:7860",
            false, "",
            false, "http://localhost:8188",
            false, "http://localhost:9090",
            List.of(),
            List.of()
        );
    }
}
