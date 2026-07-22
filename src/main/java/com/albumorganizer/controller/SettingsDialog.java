package com.albumorganizer.controller;

import com.albumorganizer.model.AlbumOrganizerSettings;
import com.albumorganizer.service.enhancement.EnhancementConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SettingsDialog extends Dialog<SettingsDialog.Result> {

    public record Result(AlbumOrganizerSettings settings, EnhancementConfig enhancementConfig) {}

    // Organize fields
    private final ChoiceBox<String> modeChoice;
    private final CheckBox createYearFolderCheck;
    private final CheckBox createMonthFolderCheck;
    private final CheckBox createDayFolderCheck;
    private final CheckBox splitResolutionCheck;
    private final TextField lowResPixelsField;
    private final TextField hiResPixelsField;

    // Cloud AI fields
    private final CheckBox stabilityEnabledCheck;
    private final PasswordField stabilityKeyField;
    private final CheckBox openAiEnabledCheck;
    private final PasswordField openAiKeyField;
    private final CheckBox geminiEnabledCheck;
    private final PasswordField geminiKeyField;
    private final CheckBox grokEnabledCheck;
    private final PasswordField grokKeyField;

    // Local AI fields
    private final CheckBox sdLocalEnabledCheck;
    private final TextField sdLocalUrlField;
    private final CheckBox invokeAiEnabledCheck;
    private final TextField invokeAiUrlField;
    private final CheckBox realEsrganEnabledCheck;
    private final TextField realEsrganPathField;
    private final CheckBox comfyUiEnabledCheck;
    private final TextField comfyUiUrlField;

    // Cloud video fields
    private final CheckBox topazEnabledCheck;
    private final PasswordField topazKeyField;
    private final CheckBox runwayEnabledCheck;
    private final PasswordField runwayKeyField;
    private final CheckBox pikaEnabledCheck;
    private final PasswordField pikaKeyField;
    private final CheckBox klingEnabledCheck;
    private final PasswordField klingKeyField;

    // Local video fields
    private final CheckBox ffmpegEsrganEnabledCheck;
    private final TextField ffmpegPathField;
    private final TextField esrganBinaryPathField;
    private final CheckBox video2xEnabledCheck;
    private final TextField video2xPathField;

    private boolean debugProtocolEnabled;
    private boolean resetPromptsRequested = false;

    public SettingsDialog(AlbumOrganizerSettings currentSettings, EnhancementConfig currentEnhancement, double fontScale) {
        setTitle("Settings");
        getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // ── Organize tab ──────────────────────────────────────────────────────
        GridPane organizeGrid = new GridPane();
        organizeGrid.setHgap(10);
        organizeGrid.setVgap(10);
        organizeGrid.setPadding(new Insets(20, 150, 10, 10));
        int row = 0;

        modeChoice = new ChoiceBox<>();
        modeChoice.getItems().addAll("Copy", "Move");
        modeChoice.setValue(currentSettings.getMode() == AlbumOrganizerSettings.OrganizeMode.COPY ? "Copy" : "Move");
        modeChoice.setDisable(true);
        organizeGrid.add(new Label("Organize with:"), 0, row);
        organizeGrid.add(modeChoice, 1, row); row++;

        createYearFolderCheck = new CheckBox("Organize with Year Folder");
        createYearFolderCheck.setSelected(currentSettings.isCreateYearFolder());
        organizeGrid.add(createYearFolderCheck, 0, row, 2, 1); row++;

        createMonthFolderCheck = new CheckBox("Organize with Month Folder");
        createMonthFolderCheck.setSelected(currentSettings.isCreateMonthFolder());
        organizeGrid.add(createMonthFolderCheck, 0, row, 2, 1); row++;

        createDayFolderCheck = new CheckBox("Organize with Day Folder");
        createDayFolderCheck.setSelected(currentSettings.isCreateDayFolder());
        organizeGrid.add(createDayFolderCheck, 0, row, 2, 1); row++;

        splitResolutionCheck = new CheckBox("Split to low resolution folders (low-res/med-res)");
        splitResolutionCheck.setSelected(currentSettings.isSplitLowRes() || currentSettings.isSplitMedRes());
        organizeGrid.add(splitResolutionCheck, 0, row, 2, 1); row++;

        lowResPixelsField = new TextField(String.valueOf(currentSettings.getLowResThresholdPixels()));
        lowResPixelsField.setPrefWidth(150);
        organizeGrid.add(new Label("Low-res threshold (pixels):"), 0, row);
        organizeGrid.add(lowResPixelsField, 1, row); row++;

        hiResPixelsField = new TextField(String.valueOf(currentSettings.getHiResThresholdPixels()));
        hiResPixelsField.setPrefWidth(150);
        organizeGrid.add(new Label("Hi-res threshold (pixels):"), 0, row);
        organizeGrid.add(hiResPixelsField, 1, row);

        createYearFolderCheck.selectedProperty().addListener((obs, o, n) -> {
            createMonthFolderCheck.setDisable(!n);
            if (!n) createMonthFolderCheck.setSelected(false);
        });
        createMonthFolderCheck.selectedProperty().addListener((obs, o, n) -> {
            createDayFolderCheck.setDisable(!n);
            if (!n) createDayFolderCheck.setSelected(false);
        });
        splitResolutionCheck.selectedProperty().addListener((obs, o, n) -> {
            lowResPixelsField.setDisable(!n);
            hiResPixelsField.setDisable(!n);
        });
        createMonthFolderCheck.setDisable(!createYearFolderCheck.isSelected());
        createDayFolderCheck.setDisable(!createMonthFolderCheck.isSelected());
        lowResPixelsField.setDisable(!splitResolutionCheck.isSelected());
        hiResPixelsField.setDisable(!splitResolutionCheck.isSelected());

        // ── Global options (always visible at top of AI Enhancement tab) ────────
        GridPane globalGrid = new GridPane();
        globalGrid.setHgap(10);
        globalGrid.setVgap(8);
        globalGrid.setPadding(new Insets(12, 16, 8, 16));
        int gi = 0;

        CheckBox debugProtocolCheck = new CheckBox("Enable Debug Protocol");
        debugProtocolCheck.setSelected(currentEnhancement.debugProtocol());
        debugProtocolCheck.setTooltip(new Tooltip(
            "After each enhancement, show a debug dialog with the source image, result image,\n" +
            "and the full request/response data (images replaced with XXXXXXXX)."));
        debugProtocolEnabled = currentEnhancement.debugProtocol();
        debugProtocolCheck.selectedProperty().addListener((obs, o, n) -> debugProtocolEnabled = n);
        globalGrid.add(debugProtocolCheck, 0, gi, 2, 1); gi++;

        Button resetPromptsBtn = new Button("Reset Default Prompts…");
        resetPromptsBtn.setTooltip(new Tooltip(
            "Remove all user-saved prompts and restore only the built-in default prompts."));
        resetPromptsBtn.setStyle("-fx-font-size: 0.9em;");
        resetPromptsBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Reset Default Prompts");
            confirm.setHeaderText("Reset prompts to defaults?");
            confirm.setContentText(
                "This will remove all your saved prompts and restore only the built-in defaults.\n" +
                "This action cannot be undone.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    resetPromptsRequested = true;
                    resetPromptsBtn.setText("✓ Will reset on Save");
                    resetPromptsBtn.setDisable(true);
                }
            });
        });
        globalGrid.add(resetPromptsBtn, 0, gi, 2, 1);

        // ── Image / Cloud sub-tab ─────────────────────────────────────────────
        GridPane imageCloudGrid = new GridPane();
        imageCloudGrid.setHgap(10);
        imageCloudGrid.setVgap(8);
        imageCloudGrid.setPadding(new Insets(12, 16, 12, 16));
        int ic = 0;

        // Stability AI
        imageCloudGrid.add(toolHeading("Stability AI",
            "Professional image upscaling and creative enhancement via Stability AI's cloud API. " +
            "Requires a paid API key. Best for high-quality upscaling and creative transformations.",
            "How do I set up Stability AI API access to use their image upscaling and enhancement API? " +
            "Step-by-step: create account at platform.stability.ai, add billing, generate API key, " +
            "and verify with curl to POST https://api.stability.ai/v2beta/stable-image/upscale/creative"),
            0, ic, 2, 1); ic++;
        stabilityEnabledCheck = new CheckBox("Enabled");
        stabilityEnabledCheck.setSelected(currentEnhancement.stabilityAiEnabled());
        imageCloudGrid.add(stabilityEnabledCheck, 0, ic, 2, 1); ic++;
        imageCloudGrid.add(new Label("API Key:"), 0, ic);
        stabilityKeyField = new PasswordField();
        stabilityKeyField.setText(currentEnhancement.stabilityAiKey());
        stabilityKeyField.setPrefWidth(320);
        stabilityKeyField.setPromptText("sk-...");
        imageCloudGrid.add(stabilityKeyField, 1, ic); ic++;
        bindToCheck(stabilityEnabledCheck, stabilityKeyField);
        imageCloudGrid.add(new Separator(), 0, ic, 2, 1); ic++;

        // OpenAI DALL·E
        imageCloudGrid.add(toolHeading("OpenAI DALL·E",
            "Image editing and generation using OpenAI's DALL·E model. " +
            "Supports prompt-driven inpainting and creative enhancement. Requires a paid OpenAI API key.",
            "How do I set up an OpenAI API key to use the DALL-E image editing API for image enhancement? " +
            "Step-by-step: create account at platform.openai.com, add payment, generate secret API key, " +
            "and verify with curl to POST https://api.openai.com/v1/images/edits"),
            0, ic, 2, 1); ic++;
        openAiEnabledCheck = new CheckBox("Enabled");
        openAiEnabledCheck.setSelected(currentEnhancement.openAiEnabled());
        imageCloudGrid.add(openAiEnabledCheck, 0, ic, 2, 1); ic++;
        imageCloudGrid.add(new Label("API Key:"), 0, ic);
        openAiKeyField = new PasswordField();
        openAiKeyField.setText(currentEnhancement.openAiKey());
        openAiKeyField.setPrefWidth(320);
        openAiKeyField.setPromptText("sk-...");
        imageCloudGrid.add(openAiKeyField, 1, ic); ic++;
        bindToCheck(openAiEnabledCheck, openAiKeyField);
        imageCloudGrid.add(new Separator(), 0, ic, 2, 1); ic++;

        // Google Gemini
        imageCloudGrid.add(toolHeading("Google Gemini",
            "Prompt-driven image generation and enhancement using Google's Gemini multimodal model. " +
            "Free tier available. Good for creative reinterpretation and style transfer.",
            "How do I set up a Google Gemini API key to use the Gemini image generation API? " +
            "Step-by-step: go to aistudio.google.com, create API key, " +
            "and verify with curl to the gemini-2.0-flash-exp-image-generation model"),
            0, ic, 2, 1); ic++;
        geminiEnabledCheck = new CheckBox("Enabled");
        geminiEnabledCheck.setSelected(currentEnhancement.geminiEnabled());
        imageCloudGrid.add(geminiEnabledCheck, 0, ic, 2, 1); ic++;
        imageCloudGrid.add(new Label("API Key:"), 0, ic);
        geminiKeyField = new PasswordField();
        geminiKeyField.setText(currentEnhancement.geminiKey());
        geminiKeyField.setPrefWidth(320);
        geminiKeyField.setPromptText("AIza...");
        imageCloudGrid.add(geminiKeyField, 1, ic); ic++;
        bindToCheck(geminiEnabledCheck, geminiKeyField);
        imageCloudGrid.add(new Separator(), 0, ic, 2, 1); ic++;

        // Grok xAI
        imageCloudGrid.add(toolHeading("Grok xAI",
            "Image generation using xAI's Grok model. " +
            "Prompt-driven enhancement via the Aurora image generation endpoint. Requires a paid xAI API key.",
            "How do I set up a Grok xAI API key to use their image generation API? " +
            "Step-by-step: go to console.x.ai, create API key, " +
            "and verify with curl to POST https://api.x.ai/v1/images/generations"),
            0, ic, 2, 1); ic++;
        grokEnabledCheck = new CheckBox("Enabled");
        grokEnabledCheck.setSelected(currentEnhancement.grokEnabled());
        imageCloudGrid.add(grokEnabledCheck, 0, ic, 2, 1); ic++;
        imageCloudGrid.add(new Label("API Key:"), 0, ic);
        grokKeyField = new PasswordField();
        grokKeyField.setText(currentEnhancement.grokKey());
        grokKeyField.setPrefWidth(320);
        grokKeyField.setPromptText("xai-...");
        imageCloudGrid.add(grokKeyField, 1, ic);
        bindToCheck(grokEnabledCheck, grokKeyField);

        // ── Image / Local sub-tab ─────────────────────────────────────────────
        GridPane imageLocalGrid = new GridPane();
        imageLocalGrid.setHgap(10);
        imageLocalGrid.setVgap(8);
        imageLocalGrid.setPadding(new Insets(12, 16, 12, 16));
        int il = 0;

        // Stable Diffusion WebUI
        imageLocalGrid.add(toolHeading("Stable Diffusion WebUI (AUTOMATIC1111)",
            "Local img2img enhancement using AUTOMATIC1111's Stable Diffusion web UI. " +
            "Runs fully on your machine — no API costs. Requires a GPU for practical speed.",
            "How do I install AUTOMATIC1111 Stable Diffusion WebUI on a Mac for REST API use at http://localhost:7860? " +
            "Step-by-step shell commands: install Homebrew, Python, git, " +
            "clone github.com/AUTOMATIC1111/stable-diffusion-webui, run with --api flag, " +
            "download a checkpoint, and verify with curl to /sdapi/v1/sd-models"),
            0, il, 2, 1); il++;
        sdLocalEnabledCheck = new CheckBox("Enabled");
        sdLocalEnabledCheck.setSelected(currentEnhancement.sdLocalEnabled());
        imageLocalGrid.add(sdLocalEnabledCheck, 0, il, 2, 1); il++;
        imageLocalGrid.add(new Label("Base URL:"), 0, il);
        sdLocalUrlField = new TextField(currentEnhancement.sdLocalUrl());
        sdLocalUrlField.setPrefWidth(300);
        sdLocalUrlField.setPromptText("http://localhost:7860");
        imageLocalGrid.add(sdLocalUrlField, 1, il); il++;
        bindToCheck(sdLocalEnabledCheck, sdLocalUrlField);
        imageLocalGrid.add(new Separator(), 0, il, 2, 1); il++;

        // InvokeAI
        imageLocalGrid.add(toolHeading("InvokeAI",
            "Polished local Stable Diffusion server with a clean REST API. " +
            "Easier to set up than AUTOMATIC1111. Runs on your machine — no API costs. GPU recommended.",
            "How do I install InvokeAI on a Mac for REST API use at http://localhost:9090? " +
            "Step-by-step shell commands: install Python via Homebrew, " +
            "run the official installer or pip install invokeai, launch server, download a model, " +
            "and verify with curl to /api/v1/app/version"),
            0, il, 2, 1); il++;
        invokeAiEnabledCheck = new CheckBox("Enabled");
        invokeAiEnabledCheck.setSelected(currentEnhancement.invokeAiEnabled());
        imageLocalGrid.add(invokeAiEnabledCheck, 0, il, 2, 1); il++;
        imageLocalGrid.add(new Label("Base URL:"), 0, il);
        invokeAiUrlField = new TextField(currentEnhancement.invokeAiUrl());
        invokeAiUrlField.setPrefWidth(300);
        invokeAiUrlField.setPromptText("http://localhost:9090");
        imageLocalGrid.add(invokeAiUrlField, 1, il); il++;
        bindToCheck(invokeAiEnabledCheck, invokeAiUrlField);
        imageLocalGrid.add(new Separator(), 0, il, 2, 1); il++;

        // Real-ESRGAN
        imageLocalGrid.add(toolHeading("Real-ESRGAN",
            "Fast, high-quality image upscaling using the Real-ESRGAN neural network model. " +
            "No prompts — pure upscaling. Runs on CPU via Java ONNX Runtime, no GPU required. " +
            "Download the ONNX model file once and point to it below.",
            "How do I download the Real-ESRGAN ONNX model for use with Java ONNX Runtime? " +
            "Create the models directory at: " + com.albumorganizer.util.AppDirs.modelsDir() + ", " +
            "download RealESRGAN_x4plus.onnx from github.com/xinntao/Real-ESRGAN releases via curl, " +
            "and verify the file size"),
            0, il, 2, 1); il++;
        realEsrganEnabledCheck = new CheckBox("Enabled");
        realEsrganEnabledCheck.setSelected(currentEnhancement.realEsrganEnabled());
        imageLocalGrid.add(realEsrganEnabledCheck, 0, il, 2, 1); il++;
        imageLocalGrid.add(new Label("Model path:"), 0, il);
        realEsrganPathField = new TextField(currentEnhancement.realEsrganModelPath());
        realEsrganPathField.setPrefWidth(300);
        realEsrganPathField.setPromptText(
            com.albumorganizer.util.AppDirs.modelsDir().resolve("RealESRGAN_x4plus.onnx").toString());
        imageLocalGrid.add(lookupField(realEsrganPathField, "realesrgan-ncnn-vulkan"), 1, il); il++;
        bindToCheck(realEsrganEnabledCheck, realEsrganPathField);
        imageLocalGrid.add(new Separator(), 0, il, 2, 1); il++;

        // ComfyUI (image + video — lives in local image tab with a note)
        imageLocalGrid.add(toolHeading("ComfyUI  (Image + Video)",
            "Flexible node-based AI pipeline that supports both image and video enhancement. " +
            "Image: img2img via KSampler. Video: frame-by-frame enhancement via ComfyUI-VideoHelperSuite (VHS). " +
            "Runs fully on your machine. GPU strongly recommended for video.",
            "How do I install ComfyUI on a Mac for REST API use at http://localhost:8188, " +
            "and also install ComfyUI-VideoHelperSuite for video support? " +
            "Step-by-step: install Python and git via Homebrew, clone github.com/comfyanonymous/ComfyUI, " +
            "create venv, install requirements, launch with python main.py --listen, " +
            "download a checkpoint into models/checkpoints, " +
            "then clone github.com/Kosinkadink/ComfyUI-VideoHelperSuite into custom_nodes and restart. " +
            "Verify with curl to /history"),
            0, il, 2, 1); il++;
        comfyUiEnabledCheck = new CheckBox("Enabled");
        comfyUiEnabledCheck.setSelected(currentEnhancement.comfyUiEnabled());
        imageLocalGrid.add(comfyUiEnabledCheck, 0, il, 2, 1); il++;
        imageLocalGrid.add(new Label("Base URL:"), 0, il);
        comfyUiUrlField = new TextField(currentEnhancement.comfyUiUrl());
        comfyUiUrlField.setPrefWidth(300);
        comfyUiUrlField.setPromptText("http://localhost:8188");
        imageLocalGrid.add(comfyUiUrlField, 1, il);
        bindToCheck(comfyUiEnabledCheck, comfyUiUrlField);

        // ── Video / Cloud sub-tab ─────────────────────────────────────────────
        GridPane videoCloudGrid = new GridPane();
        videoCloudGrid.setHgap(10);
        videoCloudGrid.setVgap(8);
        videoCloudGrid.setPadding(new Insets(12, 16, 12, 16));
        int vc = 0;

        // Topaz Video AI
        videoCloudGrid.add(toolHeading("Topaz Video AI",
            "Professional cloud video upscaling and enhancement via Topaz Labs API. " +
            "Best-in-class quality for upscaling, denoising and frame interpolation. Requires a paid API key.",
            "How do I get a Topaz Video AI API key for cloud video enhancement? " +
            "Visit account.topazlabs.com, subscribe, generate API key."),
            0, vc, 2, 1); vc++;
        topazEnabledCheck = new CheckBox("Enabled");
        topazEnabledCheck.setSelected(currentEnhancement.topazEnabled());
        videoCloudGrid.add(topazEnabledCheck, 0, vc, 2, 1); vc++;
        videoCloudGrid.add(new Label("API Key:"), 0, vc);
        topazKeyField = new PasswordField();
        topazKeyField.setText(currentEnhancement.topazKey());
        topazKeyField.setPrefWidth(320);
        topazKeyField.setPromptText("tvai-...");
        videoCloudGrid.add(topazKeyField, 1, vc); vc++;
        bindToCheck(topazEnabledCheck, topazKeyField);
        videoCloudGrid.add(new Separator(), 0, vc, 2, 1); vc++;

        // RunwayML
        videoCloudGrid.add(toolHeading("RunwayML",
            "AI video-to-video enhancement using RunwayML Gen-4 Turbo. " +
            "Supports prompt-driven style and quality changes. Requires a paid RunwayML API key.",
            "How do I get a RunwayML API key for video enhancement? " +
            "Visit app.runwayml.com, create account, go to Account → API Keys."),
            0, vc, 2, 1); vc++;
        runwayEnabledCheck = new CheckBox("Enabled");
        runwayEnabledCheck.setSelected(currentEnhancement.runwayEnabled());
        videoCloudGrid.add(runwayEnabledCheck, 0, vc, 2, 1); vc++;
        videoCloudGrid.add(new Label("API Key:"), 0, vc);
        runwayKeyField = new PasswordField();
        runwayKeyField.setText(currentEnhancement.runwayKey());
        runwayKeyField.setPrefWidth(320);
        runwayKeyField.setPromptText("rml-...");
        videoCloudGrid.add(runwayKeyField, 1, vc); vc++;
        bindToCheck(runwayEnabledCheck, runwayKeyField);
        videoCloudGrid.add(new Separator(), 0, vc, 2, 1); vc++;

        // Pika Labs
        videoCloudGrid.add(toolHeading("Pika Labs",
            "Cloud video-to-video enhancement with prompt control. Credits-based pricing. " +
            "Requires a Pika API key.",
            "How do I get a Pika Labs API key for video enhancement? " +
            "Visit pika.art, create account, go to Settings → API."),
            0, vc, 2, 1); vc++;
        pikaEnabledCheck = new CheckBox("Enabled");
        pikaEnabledCheck.setSelected(currentEnhancement.pikaEnabled());
        videoCloudGrid.add(pikaEnabledCheck, 0, vc, 2, 1); vc++;
        videoCloudGrid.add(new Label("API Key:"), 0, vc);
        pikaKeyField = new PasswordField();
        pikaKeyField.setText(currentEnhancement.pikaKey());
        pikaKeyField.setPrefWidth(320);
        pikaKeyField.setPromptText("pika-...");
        videoCloudGrid.add(pikaKeyField, 1, vc); vc++;
        bindToCheck(pikaEnabledCheck, pikaKeyField);
        videoCloudGrid.add(new Separator(), 0, vc, 2, 1); vc++;

        // Kling AI
        videoCloudGrid.add(toolHeading("Kling AI",
            "Cloud video-to-video enhancement and generation using Kling AI. Credits-based pricing. " +
            "Requires a Kling API key.",
            "How do I get a Kling AI API key for video enhancement? " +
            "Visit klingai.com, create account, go to Settings → API Keys."),
            0, vc, 2, 1); vc++;
        klingEnabledCheck = new CheckBox("Enabled");
        klingEnabledCheck.setSelected(currentEnhancement.klingEnabled());
        videoCloudGrid.add(klingEnabledCheck, 0, vc, 2, 1); vc++;
        videoCloudGrid.add(new Label("API Key:"), 0, vc);
        klingKeyField = new PasswordField();
        klingKeyField.setText(currentEnhancement.klingKey());
        klingKeyField.setPrefWidth(320);
        klingKeyField.setPromptText("kling-...");
        videoCloudGrid.add(klingKeyField, 1, vc);
        bindToCheck(klingEnabledCheck, klingKeyField);

        // ── Video / Local sub-tab ─────────────────────────────────────────────
        GridPane videoLocalGrid = new GridPane();
        videoLocalGrid.setHgap(10);
        videoLocalGrid.setVgap(8);
        videoLocalGrid.setPadding(new Insets(12, 16, 12, 16));
        int vl = 0;

        // FFmpeg + Real-ESRGAN
        videoLocalGrid.add(toolHeading("FFmpeg + Real-ESRGAN NCNN",
            "Free local video upscaling: FFmpeg extracts frames, realesrgan-ncnn-vulkan upscales each frame, " +
            "FFmpeg reassembles. GPU-accelerated via Vulkan. No API costs.",
            "How do I install ffmpeg and realesrgan-ncnn-vulkan on a Mac for video upscaling? " +
            "brew install ffmpeg, then download realesrgan-ncnn-vulkan from github.com/xinntao/Real-ESRGAN/releases"),
            0, vl, 2, 1); vl++;
        ffmpegEsrganEnabledCheck = new CheckBox("Enabled");
        ffmpegEsrganEnabledCheck.setSelected(currentEnhancement.ffmpegEsrganEnabled());
        videoLocalGrid.add(ffmpegEsrganEnabledCheck, 0, vl, 2, 1); vl++;
        videoLocalGrid.add(new Label("FFmpeg path:"), 0, vl);
        ffmpegPathField = new TextField(currentEnhancement.ffmpegPath());
        ffmpegPathField.setPrefWidth(300);
        ffmpegPathField.setPromptText("ffmpeg  (or full path)");
        videoLocalGrid.add(lookupField(ffmpegPathField, "ffmpeg"), 1, vl); vl++;
        videoLocalGrid.add(new Label("ESRGAN binary:"), 0, vl);
        esrganBinaryPathField = new TextField(currentEnhancement.esrganBinaryPath());
        esrganBinaryPathField.setPrefWidth(300);
        esrganBinaryPathField.setPromptText("/usr/local/bin/realesrgan-ncnn-vulkan");
        videoLocalGrid.add(lookupField(esrganBinaryPathField, "realesrgan-ncnn-vulkan"), 1, vl); vl++;
        bindToCheck(ffmpegEsrganEnabledCheck, ffmpegPathField);
        bindToCheck(ffmpegEsrganEnabledCheck, esrganBinaryPathField);
        videoLocalGrid.add(new Separator(), 0, vl, 2, 1); vl++;

        // video2x
        videoLocalGrid.add(toolHeading("video2x",
            "Open-source video upscaling tool that chains FFmpeg with Real-ESRGAN, Waifu2x, or SRMD. " +
            "Easy to use, GPU-accelerated. No API costs.",
            "How do I install video2x on a Mac for video upscaling? " +
            "pip install video2x, or download binary from github.com/k4yt3x/video2x/releases"),
            0, vl, 2, 1); vl++;
        video2xEnabledCheck = new CheckBox("Enabled");
        video2xEnabledCheck.setSelected(currentEnhancement.video2xEnabled());
        videoLocalGrid.add(video2xEnabledCheck, 0, vl, 2, 1); vl++;
        videoLocalGrid.add(new Label("video2x path:"), 0, vl);
        video2xPathField = new TextField(currentEnhancement.video2xPath());
        video2xPathField.setPrefWidth(300);
        video2xPathField.setPromptText("video2x  (or full path)");
        videoLocalGrid.add(lookupField(video2xPathField, "video2x"), 1, vl);
        bindToCheck(video2xEnabledCheck, video2xPathField);

        // ── Sub-TabPane: Image Cloud / Image Local / Video Cloud / Video Local ─
        TabPane subTabs = new TabPane();
        subTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        subTabs.getTabs().addAll(
            new Tab("Image · Cloud",  new ScrollPane(imageCloudGrid) {{ setFitToWidth(true); }}),
            new Tab("Image · Local",  new ScrollPane(imageLocalGrid) {{ setFitToWidth(true); }}),
            new Tab("Video · Cloud",  new ScrollPane(videoCloudGrid) {{ setFitToWidth(true); }}),
            new Tab("Video · Local",  new ScrollPane(videoLocalGrid) {{ setFitToWidth(true); }})
        );

        VBox aiTabContent = new VBox(0, globalGrid, new Separator(), subTabs);
        VBox.setVgrow(subTabs, javafx.scene.layout.Priority.ALWAYS);

        // ── Top-level tabs ────────────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
            new Tab("Organize", organizeGrid),
            new Tab("AI Enhancement", aiTabContent)
        );
        tabPane.setPrefHeight(560);

        getDialogPane().setContent(tabPane);

        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String err = validateOrganizeSettings();
            if (err != null) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Validation Error");
                alert.setHeaderText("Invalid Settings");
                alert.setContentText(err);
                alert.showAndWait();
            }
        });

        setResultConverter(btn -> btn == saveButtonType
            ? new Result(buildOrganizeSettings(), buildEnhancementConfig(currentEnhancement))
            : null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private VBox toolHeading(String title, String description, String searchQuery) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.05em;");
        Button btn = new Button("How to set up");
        btn.setStyle("-fx-font-size: 0.85em;");
        btn.setOnAction(e -> openSearch(searchQuery));
        HBox titleRow = new HBox(10, titleLabel, btn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(480);
        descLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 0.9em;");

        VBox box = new VBox(3, titleRow, descLabel);
        box.setPadding(new Insets(4, 0, 2, 0));
        return box;
    }

    /** Bold banner for a major section (Cloud / Local). */
    private Label sectionBanner(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 1.15em; -fx-padding: 8 0 2 0;");
        return label;
    }

    /** Italic sub-category label (Image / Video / Image+Video). */
    private Label categoryLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-style: italic; -fx-text-fill: #444; -fx-padding: 2 0 4 0;");
        return label;
    }

    private HBox sectionHeading(String title, String searchQuery) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        Button btn = new Button("Instructions");
        btn.setOnAction(e -> openSearch(searchQuery));
        HBox box = new HBox(10, label, btn);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void bindToCheck(CheckBox check, Control field) {
        field.setDisable(!check.isSelected());
        check.selectedProperty().addListener((obs, o, n) -> field.setDisable(!n));
    }

    /**
     * Wraps a TextField in an HBox with a small "Find" button that runs
     * `which <binaryName>` and populates the field on success.
     */
    private HBox lookupField(TextField field, String binaryName) {
        Button findBtn = new Button("Find");
        findBtn.setStyle("-fx-font-size: 0.82em;");
        findBtn.setTooltip(new Tooltip("Run `which " + binaryName + "` to locate the binary"));
        findBtn.setOnAction(e -> {
            try {
                Process p = new ProcessBuilder("which", binaryName).start();
                String found = new BufferedReader(new InputStreamReader(p.getInputStream()))
                    .lines().findFirst().orElse("").trim();
                p.waitFor();
                if (!found.isEmpty()) {
                    field.setText(found);
                } else {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Not Found");
                    alert.setHeaderText(null);
                    alert.setContentText("`" + binaryName + "` was not found on PATH.");
                    alert.showAndWait();
                }
            } catch (Exception ex) {
                // silently ignore — which not available on this platform
            }
        });
        HBox box = new HBox(6, field, findBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void openSearch(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            Desktop.getDesktop().browse(URI.create("https://www.google.com/search?q=" + encoded));
        } catch (Exception ex) {
            // browser unavailable — silently ignore
        }
    }

    // ── Build settings from UI ───────────────────────────────────────────────

    private String validateOrganizeSettings() {
        try {
            int lowRes = Integer.parseInt(lowResPixelsField.getText());
            int hiRes = Integer.parseInt(hiResPixelsField.getText());
            if (lowRes <= 0) return "Low-res threshold must be a positive number.";
            if (hiRes <= lowRes) return "Hi-res threshold must be greater than low-res threshold.\n\n" +
                "Current values:\nLow-res: " + lowRes + " pixels\nHi-res: " + hiRes + " pixels";
        } catch (NumberFormatException e) {
            return "Threshold values must be valid numbers.";
        }
        return null;
    }

    private AlbumOrganizerSettings buildOrganizeSettings() {
        AlbumOrganizerSettings s = new AlbumOrganizerSettings();
        s.setMode(modeChoice.getValue().equals("Copy")
            ? AlbumOrganizerSettings.OrganizeMode.COPY
            : AlbumOrganizerSettings.OrganizeMode.MOVE);
        s.setCreateYearFolder(createYearFolderCheck.isSelected());
        s.setCreateMonthFolder(createMonthFolderCheck.isSelected());
        s.setCreateDayFolder(createDayFolderCheck.isSelected());
        boolean splitRes = splitResolutionCheck.isSelected();
        s.setSplitLowRes(splitRes);
        s.setSplitMedRes(splitRes);
        try {
            s.setLowResThresholdPixels(Integer.parseInt(lowResPixelsField.getText()));
            s.setHiResThresholdPixels(Integer.parseInt(hiResPixelsField.getText()));
        } catch (NumberFormatException ignored) {}
        return s;
    }

    private EnhancementConfig buildEnhancementConfig(EnhancementConfig current) {
        List<com.albumorganizer.service.enhancement.NamedPrompt> prompts;
        List<String> checkedTitles;
        if (resetPromptsRequested) {
            // Keep only user-added prompts (those not in the default set), remove all defaults.
            // seedPrompts() will re-insert fresh defaults at the front on next open.
            prompts = current.savedPrompts().stream()
                .filter(p -> !p.isDefault())
                .collect(java.util.stream.Collectors.toList());
            // Also drop checked-titles that belonged to defaults
            java.util.Set<String> keptTitles = prompts.stream()
                .map(com.albumorganizer.service.enhancement.NamedPrompt::title)
                .collect(java.util.stream.Collectors.toSet());
            checkedTitles = current.checkedPromptTitles().stream()
                .filter(keptTitles::contains)
                .collect(java.util.stream.Collectors.toList());
        } else {
            prompts = current.savedPrompts();
            checkedTitles = current.checkedPromptTitles();
        }
        return new EnhancementConfig(
            stabilityEnabledCheck.isSelected(), stabilityKeyField.getText().trim(),
            openAiEnabledCheck.isSelected(), openAiKeyField.getText().trim(),
            geminiEnabledCheck.isSelected(), geminiKeyField.getText().trim(), current.geminiTemperature(),
            grokEnabledCheck.isSelected(), grokKeyField.getText().trim(), current.grokModel(),
            sdLocalEnabledCheck.isSelected(), sdLocalUrlField.getText().trim(),
            realEsrganEnabledCheck.isSelected(), realEsrganPathField.getText().trim(),
            comfyUiEnabledCheck.isSelected(), comfyUiUrlField.getText().trim(),
            current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
            invokeAiEnabledCheck.isSelected(), invokeAiUrlField.getText().trim(),
            topazEnabledCheck.isSelected(), topazKeyField.getText().trim(),
            runwayEnabledCheck.isSelected(), runwayKeyField.getText().trim(),
            pikaEnabledCheck.isSelected(), pikaKeyField.getText().trim(),
            klingEnabledCheck.isSelected(), klingKeyField.getText().trim(),
            ffmpegEsrganEnabledCheck.isSelected(), ffmpegPathField.getText().trim(), esrganBinaryPathField.getText().trim(),
            video2xEnabledCheck.isSelected(), video2xPathField.getText().trim(),
            prompts,
            checkedTitles,
            debugProtocolEnabled
        );
    }
}
