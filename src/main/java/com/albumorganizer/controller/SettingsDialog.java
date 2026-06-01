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

    // General fields
    private final RadioButton aiOutputNextToOriginalRadio;
    private final RadioButton aiOutputToTargetFolderRadio;

    // Cloud image AI fields
    private final CheckBox stabilityEnabledCheck;
    private final PasswordField stabilityKeyField;
    private final CheckBox openAiEnabledCheck;
    private final PasswordField openAiKeyField;
    private final CheckBox geminiEnabledCheck;
    private final PasswordField geminiKeyField;
    private final CheckBox grokEnabledCheck;
    private final PasswordField grokKeyField;

    // Local image AI fields
    private final CheckBox sdLocalEnabledCheck;
    private final TextField sdLocalUrlField;
    private final CheckBox comfyUiEnabledCheck;
    private final TextField comfyUiUrlField;
    private final CheckBox invokeAiEnabledCheck;
    private final TextField invokeAiUrlField;
    private final CheckBox realEsrganEnabledCheck;
    private final TextField realEsrganPathField;

    public SettingsDialog(AlbumOrganizerSettings currentSettings, EnhancementConfig currentEnhancement, double fontScale) {
        setTitle("Settings");
        getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // ── General tab ───────────────────────────────────────────────────────
        GridPane generalGrid = new GridPane();
        generalGrid.setHgap(10);
        generalGrid.setVgap(10);
        generalGrid.setPadding(new Insets(20, 20, 10, 10));
        int gRow = 0;

        Label aiOutputLabel = new Label("AI-generated file location:");
        aiOutputLabel.setStyle("-fx-font-weight: bold;");
        generalGrid.add(aiOutputLabel, 0, gRow, 2, 1); gRow++;

        ToggleGroup aiOutputGroup = new ToggleGroup();
        aiOutputNextToOriginalRadio = new RadioButton("Next to the original file");
        aiOutputNextToOriginalRadio.setToggleGroup(aiOutputGroup);
        aiOutputNextToOriginalRadio.setSelected(!currentSettings.isAiOutputToTargetFolder());
        generalGrid.add(aiOutputNextToOriginalRadio, 0, gRow, 2, 1); gRow++;

        aiOutputToTargetFolderRadio = new RadioButton("In the target folder under 'AI-Generated'");
        aiOutputToTargetFolderRadio.setToggleGroup(aiOutputGroup);
        aiOutputToTargetFolderRadio.setSelected(currentSettings.isAiOutputToTargetFolder());
        generalGrid.add(aiOutputToTargetFolderRadio, 0, gRow, 2, 1);

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

        // ── Image Cloud AI tab ────────────────────────────────────────────────
        GridPane cloudGrid = new GridPane();
        cloudGrid.setHgap(10);
        cloudGrid.setVgap(8);
        cloudGrid.setPadding(new Insets(20, 20, 10, 10));
        int cRow = 0;

        cloudGrid.add(sectionHeading("Stability AI",
            "How do I set up Stability AI API access to use their image upscaling and enhancement API? " +
            "Give me step-by-step instructions: how to create an account at platform.stability.ai, " +
            "add billing, generate an API key, and verify it works using a curl command that calls " +
            "the creative upscale endpoint POST https://api.stability.ai/v2beta/stable-image/upscale/creative " +
            "with a sample image. Include the exact curl command I can run in my terminal."),
            0, cRow, 2, 1); cRow++;
        stabilityEnabledCheck = new CheckBox("Enabled");
        stabilityEnabledCheck.setSelected(currentEnhancement.stabilityAiEnabled());
        cloudGrid.add(stabilityEnabledCheck, 0, cRow, 2, 1); cRow++;
        cloudGrid.add(new Label("API Key:"), 0, cRow);
        stabilityKeyField = new PasswordField();
        stabilityKeyField.setText(currentEnhancement.stabilityAiKey());
        stabilityKeyField.setPrefWidth(320);
        stabilityKeyField.setPromptText("sk-...");
        cloudGrid.add(stabilityKeyField, 1, cRow); cRow++;
        bindToCheck(stabilityEnabledCheck, stabilityKeyField);

        cloudGrid.add(new Separator(), 0, cRow, 2, 1); cRow++;

        cloudGrid.add(sectionHeading("OpenAI DALL·E",
            "How do I set up an OpenAI API key to use the DALL-E image editing API for image enhancement? " +
            "Give me step-by-step instructions: how to create an OpenAI account at platform.openai.com, " +
            "add a payment method, generate a secret API key, and verify it works with a curl command " +
            "that calls POST https://api.openai.com/v1/images/edits with a PNG image and a prompt. " +
            "Include the exact curl command I can copy and run in my terminal."),
            0, cRow, 2, 1); cRow++;
        openAiEnabledCheck = new CheckBox("Enabled");
        openAiEnabledCheck.setSelected(currentEnhancement.openAiEnabled());
        cloudGrid.add(openAiEnabledCheck, 0, cRow, 2, 1); cRow++;
        cloudGrid.add(new Label("API Key:"), 0, cRow);
        openAiKeyField = new PasswordField();
        openAiKeyField.setText(currentEnhancement.openAiKey());
        openAiKeyField.setPrefWidth(320);
        openAiKeyField.setPromptText("sk-...");
        cloudGrid.add(openAiKeyField, 1, cRow);
        bindToCheck(openAiEnabledCheck, openAiKeyField);

        cloudGrid.add(new Separator(), 0, cRow + 1, 2, 1); cRow += 2;

        cloudGrid.add(sectionHeading("Google Gemini",
            "How do I set up a Google Gemini API key to use the Gemini image generation API for image enhancement? " +
            "Give me step-by-step instructions: how to go to aistudio.google.com, create an API key, " +
            "and verify it works with a curl command that calls the gemini-2.0-flash-exp-image-generation model. " +
            "Include the exact curl command I can run in my terminal."),
            0, cRow, 2, 1); cRow++;
        geminiEnabledCheck = new CheckBox("Enabled");
        geminiEnabledCheck.setSelected(currentEnhancement.geminiEnabled());
        cloudGrid.add(geminiEnabledCheck, 0, cRow, 2, 1); cRow++;
        cloudGrid.add(new Label("API Key:"), 0, cRow);
        geminiKeyField = new PasswordField();
        geminiKeyField.setText(currentEnhancement.geminiKey());
        geminiKeyField.setPrefWidth(320);
        geminiKeyField.setPromptText("AIza...");
        cloudGrid.add(geminiKeyField, 1, cRow);
        bindToCheck(geminiEnabledCheck, geminiKeyField);

        cloudGrid.add(new Separator(), 0, cRow + 1, 2, 1); cRow += 2;

        cloudGrid.add(sectionHeading("Grok xAI",
            "How do I set up a Grok xAI API key to use their image generation API for image enhancement? " +
            "Give me step-by-step instructions: how to go to console.x.ai, create an API key, " +
            "and verify it works with a curl command that calls POST https://api.x.ai/v1/images/generations " +
            "with a prompt. Include the exact curl command I can run in my terminal."),
            0, cRow, 2, 1); cRow++;
        grokEnabledCheck = new CheckBox("Enabled");
        grokEnabledCheck.setSelected(currentEnhancement.grokEnabled());
        cloudGrid.add(grokEnabledCheck, 0, cRow, 2, 1); cRow++;
        cloudGrid.add(new Label("API Key:"), 0, cRow);
        grokKeyField = new PasswordField();
        grokKeyField.setText(currentEnhancement.grokKey());
        grokKeyField.setPrefWidth(320);
        grokKeyField.setPromptText("xai-...");
        cloudGrid.add(grokKeyField, 1, cRow);
        bindToCheck(grokEnabledCheck, grokKeyField);

        // ── Image Local AI tab ────────────────────────────────────────────────
        GridPane localGrid = new GridPane();
        localGrid.setHgap(10);
        localGrid.setVgap(8);
        localGrid.setPadding(new Insets(20, 20, 10, 10));
        int lRow = 0;

        localGrid.add(sectionHeading("Stable Diffusion WebUI (AUTOMATIC1111)",
            "How do I install AUTOMATIC1111 Stable Diffusion WebUI on a Mac so I can use its REST API " +
            "for image-to-image enhancement at http://localhost:7860? " +
            "Give me step-by-step shell commands starting from a clean Mac: " +
            "install Homebrew, Python, git, clone the repo from github.com/AUTOMATIC1111/stable-diffusion-webui, " +
            "run the installer script with the --api flag enabled, download a model checkpoint, " +
            "and verify the API is running with a curl test to /sdapi/v1/sd-models. " +
            "Include every shell command I need to copy and run."),
            0, lRow, 2, 1); lRow++;
        sdLocalEnabledCheck = new CheckBox("Enabled");
        sdLocalEnabledCheck.setSelected(currentEnhancement.sdLocalEnabled());
        localGrid.add(sdLocalEnabledCheck, 0, lRow, 2, 1); lRow++;
        localGrid.add(new Label("Base URL:"), 0, lRow);
        sdLocalUrlField = new TextField(currentEnhancement.sdLocalUrl());
        sdLocalUrlField.setPrefWidth(300);
        sdLocalUrlField.setPromptText("http://localhost:7860");
        localGrid.add(sdLocalUrlField, 1, lRow); lRow++;
        bindToCheck(sdLocalEnabledCheck, sdLocalUrlField);

        localGrid.add(new Separator(), 0, lRow, 2, 1); lRow++;

        localGrid.add(sectionHeading("ComfyUI",
            "How do I install ComfyUI on a Mac so I can use its REST API for image enhancement at http://localhost:8188? " +
            "Give me step-by-step shell commands: install Python and git using Homebrew, " +
            "clone https://github.com/comfyanonymous/ComfyUI, create a Python virtual environment, " +
            "install requirements, launch with python main.py --listen, download a model checkpoint into the models/checkpoints folder, " +
            "and verify the API works with a curl test to /history. " +
            "Include every shell command I need to copy and run."),
            0, lRow, 2, 1); lRow++;
        comfyUiEnabledCheck = new CheckBox("Enabled");
        comfyUiEnabledCheck.setSelected(currentEnhancement.comfyUiEnabled());
        localGrid.add(comfyUiEnabledCheck, 0, lRow, 2, 1); lRow++;
        localGrid.add(new Label("Base URL:"), 0, lRow);
        comfyUiUrlField = new TextField(currentEnhancement.comfyUiUrl());
        comfyUiUrlField.setPrefWidth(300);
        comfyUiUrlField.setPromptText("http://localhost:8188");
        localGrid.add(comfyUiUrlField, 1, lRow); lRow++;
        bindToCheck(comfyUiEnabledCheck, comfyUiUrlField);

        localGrid.add(new Separator(), 0, lRow, 2, 1); lRow++;

        localGrid.add(sectionHeading("InvokeAI",
            "How do I install InvokeAI on a Mac so I can use its REST API for image enhancement at http://localhost:9090? " +
            "Give me step-by-step shell commands: install Python using Homebrew, " +
            "run the official installer script or pip install invokeai, launch the server, " +
            "download a model through the web UI or CLI, " +
            "and verify the API works with a curl test to /api/v1/app/version. " +
            "Include every shell command I need to copy and run."),
            0, lRow, 2, 1); lRow++;
        invokeAiEnabledCheck = new CheckBox("Enabled");
        invokeAiEnabledCheck.setSelected(currentEnhancement.invokeAiEnabled());
        localGrid.add(invokeAiEnabledCheck, 0, lRow, 2, 1); lRow++;
        localGrid.add(new Label("Base URL:"), 0, lRow);
        invokeAiUrlField = new TextField(currentEnhancement.invokeAiUrl());
        invokeAiUrlField.setPrefWidth(300);
        invokeAiUrlField.setPromptText("http://localhost:9090");
        localGrid.add(invokeAiUrlField, 1, lRow); lRow++;
        bindToCheck(invokeAiEnabledCheck, invokeAiUrlField);

        localGrid.add(new Separator(), 0, lRow, 2, 1); lRow++;

        localGrid.add(sectionHeading("Real-ESRGAN (no prompt)",
            "How do I download the Real-ESRGAN ONNX model file for use with Java ONNX Runtime on a Mac? " +
            "Give me the exact shell commands to: create the directory ~/.album-organizer/models/, " +
            "download RealESRGAN_x4plus.onnx from the official GitHub release at " +
            "https://github.com/xinntao/Real-ESRGAN using curl or wget, " +
            "and verify the file downloaded correctly by checking its size. " +
            "No GPU or Python required — it runs on CPU via Java ONNX Runtime."),
            0, lRow, 2, 1); lRow++;
        realEsrganEnabledCheck = new CheckBox("Enabled");
        realEsrganEnabledCheck.setSelected(currentEnhancement.realEsrganEnabled());
        localGrid.add(realEsrganEnabledCheck, 0, lRow, 2, 1); lRow++;
        localGrid.add(new Label("Model path:"), 0, lRow);
        realEsrganPathField = new TextField(currentEnhancement.realEsrganModelPath());
        realEsrganPathField.setPrefWidth(300);
        realEsrganPathField.setPromptText("~/.album-organizer/models/RealESRGAN_x4plus.onnx");
        localGrid.add(realEsrganPathField, 1, lRow);
        bindToCheck(realEsrganEnabledCheck, realEsrganPathField);

        // ── Placeholder tabs ──────────────────────────────────────────────────
        VBox videoCloudPane = placeholderPane("Video Cloud AI enhancement coming soon.");
        VBox videoLocalPane = placeholderPane("Video Local AI enhancement coming soon.");

        // ── Assemble AI sub-tabs ──────────────────────────────────────────────
        TabPane aiTabPane = new TabPane();
        aiTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        aiTabPane.getTabs().addAll(
            new Tab("Image Cloud AI", new ScrollPane(cloudGrid) {{ setFitToWidth(true); }}),
            new Tab("Image Local AI", new ScrollPane(localGrid) {{ setFitToWidth(true); }}),
            new Tab("Video Cloud AI", videoCloudPane),
            new Tab("Video Local AI", videoLocalPane)
        );

        // ── Top-level tabs ────────────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
            new Tab("General", generalGrid),
            new Tab("Organize", organizeGrid),
            new Tab("AI Enhancement", aiTabPane)
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

    private HBox sectionHeading(String title, String searchQuery) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        Button btn = new Button("Instructions");
        btn.setOnAction(e -> openSearch(searchQuery));
        HBox box = new HBox(10, label, btn);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox placeholderPane(String message) {
        Label lbl = new Label(message);
        lbl.setStyle("-fx-text-fill: #888; -fx-font-style: italic;");
        VBox box = new VBox(lbl);
        box.setPadding(new Insets(40));
        box.setAlignment(Pos.CENTER);
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
        s.setAiOutputToTargetFolder(aiOutputToTargetFolderRadio.isSelected());
        return s;
    }

    private EnhancementConfig buildEnhancementConfig(EnhancementConfig current) {
        return new EnhancementConfig(
            stabilityEnabledCheck.isSelected(), stabilityKeyField.getText().trim(),
            openAiEnabledCheck.isSelected(), openAiKeyField.getText().trim(),
            geminiEnabledCheck.isSelected(), geminiKeyField.getText().trim(),
            grokEnabledCheck.isSelected(), grokKeyField.getText().trim(),
            sdLocalEnabledCheck.isSelected(), sdLocalUrlField.getText().trim(),
            realEsrganEnabledCheck.isSelected(), realEsrganPathField.getText().trim(),
            comfyUiEnabledCheck.isSelected(), comfyUiUrlField.getText().trim(),
            invokeAiEnabledCheck.isSelected(), invokeAiUrlField.getText().trim(),
            current.savedPrompts(),
            current.checkedPromptTitles()
        );
    }
}
