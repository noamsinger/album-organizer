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

        // ── Single AI Enhancement grid ────────────────────────────────────────
        GridPane aiGrid = new GridPane();
        aiGrid.setHgap(10);
        aiGrid.setVgap(8);
        aiGrid.setPadding(new Insets(16, 20, 16, 16));
        int ai = 0;

        // ═══ GLOBAL OPTIONS ═══════════════════════════════════════════════════
        aiGrid.add(sectionBanner("⚙  Global Options"), 0, ai, 2, 1); ai++;

        CheckBox debugProtocolCheck = new CheckBox("Enable Debug Protocol");
        debugProtocolCheck.setSelected(currentEnhancement.debugProtocol());
        debugProtocolCheck.setTooltip(new Tooltip(
            "After each enhancement, show a debug dialog with the source image, result image,\n" +
            "and the full request/response data (images replaced with XXXXXXXX)."));
        debugProtocolEnabled = currentEnhancement.debugProtocol();
        debugProtocolCheck.selectedProperty().addListener((obs, o, n) -> debugProtocolEnabled = n);
        aiGrid.add(debugProtocolCheck, 0, ai, 2, 1); ai++;

        // Reset default prompts button
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
        aiGrid.add(resetPromptsBtn, 0, ai, 2, 1); ai++;

        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;
        aiGrid.add(sectionBanner("☁  Cloud (API-based)"), 0, ai, 2, 1); ai++;
        aiGrid.add(categoryLabel("Image enhancement"), 0, ai, 2, 1); ai++;

        // Stability AI
        aiGrid.add(toolHeading("Stability AI",
            "Professional image upscaling and creative enhancement via Stability AI's cloud API. " +
            "Requires a paid API key. Best for high-quality upscaling and creative transformations.",
            "How do I set up Stability AI API access to use their image upscaling and enhancement API? " +
            "Step-by-step: create account at platform.stability.ai, add billing, generate API key, " +
            "and verify with curl to POST https://api.stability.ai/v2beta/stable-image/upscale/creative"),
            0, ai, 2, 1); ai++;
        stabilityEnabledCheck = new CheckBox("Enabled");
        stabilityEnabledCheck.setSelected(currentEnhancement.stabilityAiEnabled());
        aiGrid.add(stabilityEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("API Key:"), 0, ai);
        stabilityKeyField = new PasswordField();
        stabilityKeyField.setText(currentEnhancement.stabilityAiKey());
        stabilityKeyField.setPrefWidth(320);
        stabilityKeyField.setPromptText("sk-...");
        aiGrid.add(stabilityKeyField, 1, ai); ai++;
        bindToCheck(stabilityEnabledCheck, stabilityKeyField);
        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;

        // OpenAI DALL·E
        aiGrid.add(toolHeading("OpenAI DALL·E",
            "Image editing and generation using OpenAI's DALL·E model. " +
            "Supports prompt-driven inpainting and creative enhancement. Requires a paid OpenAI API key.",
            "How do I set up an OpenAI API key to use the DALL-E image editing API for image enhancement? " +
            "Step-by-step: create account at platform.openai.com, add payment, generate secret API key, " +
            "and verify with curl to POST https://api.openai.com/v1/images/edits"),
            0, ai, 2, 1); ai++;
        openAiEnabledCheck = new CheckBox("Enabled");
        openAiEnabledCheck.setSelected(currentEnhancement.openAiEnabled());
        aiGrid.add(openAiEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("API Key:"), 0, ai);
        openAiKeyField = new PasswordField();
        openAiKeyField.setText(currentEnhancement.openAiKey());
        openAiKeyField.setPrefWidth(320);
        openAiKeyField.setPromptText("sk-...");
        aiGrid.add(openAiKeyField, 1, ai); ai++;
        bindToCheck(openAiEnabledCheck, openAiKeyField);
        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;

        // Google Gemini
        aiGrid.add(toolHeading("Google Gemini",
            "Prompt-driven image generation and enhancement using Google's Gemini multimodal model. " +
            "Free tier available. Good for creative reinterpretation and style transfer.",
            "How do I set up a Google Gemini API key to use the Gemini image generation API? " +
            "Step-by-step: go to aistudio.google.com, create API key, " +
            "and verify with curl to the gemini-2.0-flash-exp-image-generation model"),
            0, ai, 2, 1); ai++;
        geminiEnabledCheck = new CheckBox("Enabled");
        geminiEnabledCheck.setSelected(currentEnhancement.geminiEnabled());
        aiGrid.add(geminiEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("API Key:"), 0, ai);
        geminiKeyField = new PasswordField();
        geminiKeyField.setText(currentEnhancement.geminiKey());
        geminiKeyField.setPrefWidth(320);
        geminiKeyField.setPromptText("AIza...");
        aiGrid.add(geminiKeyField, 1, ai); ai++;
        bindToCheck(geminiEnabledCheck, geminiKeyField);
        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;

        // Grok xAI
        aiGrid.add(toolHeading("Grok xAI",
            "Image generation using xAI's Grok model. " +
            "Prompt-driven enhancement via the Aurora image generation endpoint. Requires a paid xAI API key.",
            "How do I set up a Grok xAI API key to use their image generation API? " +
            "Step-by-step: go to console.x.ai, create API key, " +
            "and verify with curl to POST https://api.x.ai/v1/images/generations"),
            0, ai, 2, 1); ai++;
        grokEnabledCheck = new CheckBox("Enabled");
        grokEnabledCheck.setSelected(currentEnhancement.grokEnabled());
        aiGrid.add(grokEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("API Key:"), 0, ai);
        grokKeyField = new PasswordField();
        grokKeyField.setText(currentEnhancement.grokKey());
        grokKeyField.setPrefWidth(320);
        grokKeyField.setPromptText("xai-...");
        aiGrid.add(grokKeyField, 1, ai); ai++;
        bindToCheck(grokEnabledCheck, grokKeyField);

        // ═══ LOCAL ═══════════════════════════════════════════════════════════
        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;
        aiGrid.add(sectionBanner("🖥  Local (self-hosted)"), 0, ai, 2, 1); ai++;
        aiGrid.add(categoryLabel("Image enhancement"), 0, ai, 2, 1); ai++;

        // Stable Diffusion WebUI
        aiGrid.add(toolHeading("Stable Diffusion WebUI (AUTOMATIC1111)",
            "Local img2img enhancement using AUTOMATIC1111's Stable Diffusion web UI. " +
            "Runs fully on your machine — no API costs. Requires a GPU for practical speed.",
            "How do I install AUTOMATIC1111 Stable Diffusion WebUI on a Mac for REST API use at http://localhost:7860? " +
            "Step-by-step shell commands: install Homebrew, Python, git, " +
            "clone github.com/AUTOMATIC1111/stable-diffusion-webui, run with --api flag, " +
            "download a checkpoint, and verify with curl to /sdapi/v1/sd-models"),
            0, ai, 2, 1); ai++;
        sdLocalEnabledCheck = new CheckBox("Enabled");
        sdLocalEnabledCheck.setSelected(currentEnhancement.sdLocalEnabled());
        aiGrid.add(sdLocalEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("Base URL:"), 0, ai);
        sdLocalUrlField = new TextField(currentEnhancement.sdLocalUrl());
        sdLocalUrlField.setPrefWidth(300);
        sdLocalUrlField.setPromptText("http://localhost:7860");
        aiGrid.add(sdLocalUrlField, 1, ai); ai++;
        bindToCheck(sdLocalEnabledCheck, sdLocalUrlField);
        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;

        // InvokeAI
        aiGrid.add(toolHeading("InvokeAI",
            "Polished local Stable Diffusion server with a clean REST API. " +
            "Easier to set up than AUTOMATIC1111. Runs on your machine — no API costs. GPU recommended.",
            "How do I install InvokeAI on a Mac for REST API use at http://localhost:9090? " +
            "Step-by-step shell commands: install Python via Homebrew, " +
            "run the official installer or pip install invokeai, launch server, download a model, " +
            "and verify with curl to /api/v1/app/version"),
            0, ai, 2, 1); ai++;
        invokeAiEnabledCheck = new CheckBox("Enabled");
        invokeAiEnabledCheck.setSelected(currentEnhancement.invokeAiEnabled());
        aiGrid.add(invokeAiEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("Base URL:"), 0, ai);
        invokeAiUrlField = new TextField(currentEnhancement.invokeAiUrl());
        invokeAiUrlField.setPrefWidth(300);
        invokeAiUrlField.setPromptText("http://localhost:9090");
        aiGrid.add(invokeAiUrlField, 1, ai); ai++;
        bindToCheck(invokeAiEnabledCheck, invokeAiUrlField);
        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;

        // Real-ESRGAN
        aiGrid.add(toolHeading("Real-ESRGAN",
            "Fast, high-quality image upscaling using the Real-ESRGAN neural network model. " +
            "No prompts — pure upscaling. Runs on CPU via Java ONNX Runtime, no GPU required. " +
            "Download the ONNX model file once and point to it below.",
            "How do I download the Real-ESRGAN ONNX model for use with Java ONNX Runtime on a Mac? " +
            "Shell commands: mkdir ~/.config/album-organizer/models/, " +
            "download RealESRGAN_x4plus.onnx from github.com/xinntao/Real-ESRGAN releases via curl, " +
            "and verify the file size"),
            0, ai, 2, 1); ai++;
        realEsrganEnabledCheck = new CheckBox("Enabled");
        realEsrganEnabledCheck.setSelected(currentEnhancement.realEsrganEnabled());
        aiGrid.add(realEsrganEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("Model path:"), 0, ai);
        realEsrganPathField = new TextField(currentEnhancement.realEsrganModelPath());
        realEsrganPathField.setPrefWidth(300);
        realEsrganPathField.setPromptText("~/.config/album-organizer/models/RealESRGAN_x4plus.onnx");
        aiGrid.add(realEsrganPathField, 1, ai); ai++;
        bindToCheck(realEsrganEnabledCheck, realEsrganPathField);

        aiGrid.add(new Separator(), 0, ai, 2, 1); ai++;
        aiGrid.add(categoryLabel("Image + Video enhancement"), 0, ai, 2, 1); ai++;

        // ComfyUI
        aiGrid.add(toolHeading("ComfyUI",
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
            0, ai, 2, 1); ai++;
        comfyUiEnabledCheck = new CheckBox("Enabled");
        comfyUiEnabledCheck.setSelected(currentEnhancement.comfyUiEnabled());
        aiGrid.add(comfyUiEnabledCheck, 0, ai, 2, 1); ai++;
        aiGrid.add(new Label("Base URL:"), 0, ai);
        comfyUiUrlField = new TextField(currentEnhancement.comfyUiUrl());
        comfyUiUrlField.setPrefWidth(300);
        comfyUiUrlField.setPromptText("http://localhost:8188");
        aiGrid.add(comfyUiUrlField, 1, ai);
        bindToCheck(comfyUiEnabledCheck, comfyUiUrlField);

        // ── Top-level tabs ────────────────────────────────────────────────────
        ScrollPane aiScroll = new ScrollPane(aiGrid);
        aiScroll.setFitToWidth(true);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
            new Tab("Organize", organizeGrid),
            new Tab("AI Enhancement", aiScroll)
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
            prompts,
            checkedTitles,
            debugProtocolEnabled
        );
    }
}
