package com.albumorganizer.controller;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.repository.UsageRepository;
import com.albumorganizer.service.enhancement.ComfyUIProvider;
import com.albumorganizer.service.enhancement.EnhancementProvider;
import com.albumorganizer.service.enhancement.EnhancementRequest;
import com.albumorganizer.service.enhancement.EnhancementResult;
import com.albumorganizer.service.enhancement.GeminiProvider;
import com.albumorganizer.service.enhancement.GrokProvider;
import com.albumorganizer.service.enhancement.ImageEnhancementService;
import com.albumorganizer.service.enhancement.NamedPrompt;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class EnhancementDialog extends Dialog<EnhancementResult> {

    private static final Logger logger = LoggerFactory.getLogger(EnhancementDialog.class);

    private final ComboBox<EnhancementProvider> providerCombo;
    private final ComboBox<String> resolutionCombo;
    private TextField outputFilenameField;
    private boolean filenameUserOverridden = false;

    // Prompt list with checkboxes
    private final ObservableList<NamedPrompt> promptItems = FXCollections.observableArrayList();
    // map title -> CheckBox for easy lookup
    private final Map<String, CheckBox> promptCheckBoxes = new LinkedHashMap<>();
    // map title -> row HBox for selection highlight
    private final Map<String, HBox> promptRows = new LinkedHashMap<>();
    private NamedPrompt lastClickedPrompt = null; // tracks which prompt is "selected" for Edit/Delete
    private final VBox promptCheckList;
    private final ScrollPane promptScrollPane;
    private final Label statusLabel;
    private final Label providerInfoLabel;
    private final ProgressBar progressBar;
    private final Button enhanceButton;
    private Button editBtn;
    private Button deleteBtn;

    private final Consumer<List<NamedPrompt>> onPromptsChanged;
    private final Consumer<EnhancementProvider> onProviderUsed;
    private final Consumer<List<String>> onCheckedTitlesChanged;
    private final Consumer<String> onAdditionalPromptChanged;
    private final Path outputDir;
    private final boolean debugProtocol;

    // Set after a successful enhancement when debugProtocol is on
    private EnhancementRequest completedRequest;
    private EnhancementResult completedResult;
    private double capturedFontScale = 1.0;

    public record DebugData(EnhancementRequest request, EnhancementResult result, double fontScale) {
        public String rawRequestBody() { return result.rawRequestBody(); }
        public String rawResponseBody() { return result.rawResponseBody(); }
    }

    /** Returns debug data if debugProtocol is enabled and enhancement succeeded, otherwise null. */
    public DebugData getDebugData() {
        if (debugProtocol && completedRequest != null && completedResult != null) {
            return new DebugData(completedRequest, completedResult, capturedFontScale);
        }
        return null;
    }

    // record holding checkpoint selection + cached list to persist together
    public record ComfyUiCheckpointState(String selected, List<String> checkpoints) {}

    public record ProviderParams(double geminiTemperature, String grokModel) {}

    public EnhancementDialog(MediaFile mediaFile,
                             List<EnhancementProvider> providers,
                             List<NamedPrompt> savedPrompts,
                             double fontScale,
                             EnhancementProvider initialProvider,
                             List<String> initialCheckedTitles,
                             String initialAdditionalPrompt,
                             String initialComfyUiCheckpoint,
                             List<String> initialComfyUiCheckpoints,
                             String comfyUiBaseUrl,
                             ProviderParams initialProviderParams,
                             boolean debugProtocol,
                             Consumer<List<NamedPrompt>> onPromptsChanged,
                             Consumer<EnhancementProvider> onProviderUsed,
                             Consumer<List<String>> onCheckedTitlesChanged,
                             Consumer<String> onAdditionalPromptChanged,
                             Consumer<ComfyUiCheckpointState> onComfyUiCheckpointChanged,
                             Consumer<ProviderParams> onProviderParamsChanged,
                             Path outputDir) {
        this.onPromptsChanged = onPromptsChanged;
        this.onProviderUsed = onProviderUsed;
        this.onCheckedTitlesChanged = onCheckedTitlesChanged;
        this.onAdditionalPromptChanged = onAdditionalPromptChanged;
        this.outputDir = outputDir;
        this.debugProtocol = debugProtocol;
        promptItems.addAll(savedPrompts);

        setTitle("Enhance with AI");
        setHeaderText("Enhance: " + mediaFile.getFilename());
        getDialogPane().setStyle(String.format(
            "-fx-font-size: %.2fem; -fx-border-color: #C94A00; -fx-border-width: 3; -fx-border-radius: 4;",
            fontScale));
        getDialogPane().setPrefWidth(640);
        setResizable(true);

        // Strong orange header
        javafx.application.Platform.runLater(() -> {
            javafx.scene.Node header = getDialogPane().lookup(".header-panel");
            if (header != null) header.setStyle("-fx-background-color: #C94A00;");
            javafx.scene.Node headerLabel = getDialogPane().lookup(".header-panel .label");
            if (headerLabel != null) headerLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        });

        // No explicit buttons — window [X] and Esc close the dialog
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.CANCEL).setVisible(false);
        getDialogPane().lookupButton(ButtonType.CANCEL).setManaged(false);

        getDialogPane().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16, 16, 10, 16));

        // Label column fixed; value column grows
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(90);
        col0.setHgrow(Priority.NEVER);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setFillWidth(true);
        grid.getColumnConstraints().addAll(col0, col1);

        int row = 0;

        // Provider
        grid.add(new Label("Provider:"), 0, row);
        providerCombo = new ComboBox<>();
        providerCombo.getItems().addAll(providers);
        providerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(EnhancementProvider p) { return p == null ? "" : p.getName(); }
            @Override public EnhancementProvider fromString(String s) { return null; }
        });
        if (!providers.isEmpty()) providerCombo.getSelectionModel().selectFirst();
        if (initialProvider != null) {
            providers.stream()
                .filter(p -> p.getShortId().equals(initialProvider.getShortId()))
                .findFirst()
                .ifPresent(p -> providerCombo.getSelectionModel().select(p));
        }
        providerCombo.setPrefWidth(300);
        providerCombo.setMaxWidth(Double.MAX_VALUE);
        grid.add(providerCombo, 1, row); row++;

        // ── Separator after provider group ────────────────────────────────────
        grid.add(new Separator(), 0, row, 2, 1); row++;

        // ComfyUI checkpoint row — only visible when ComfyUI provider is selected
        Label ckptLabel = new Label("Checkpoint:");
        ComboBox<String> ckptCombo = new ComboBox<>();
        ckptCombo.setEditable(false);
        ckptCombo.setPrefWidth(240);
        ckptCombo.setPromptText("auto-detect");
        // Populate from cached list
        List<String> cachedCheckpoints = initialComfyUiCheckpoints != null
            ? new ArrayList<>(initialComfyUiCheckpoints) : new ArrayList<>();
        if (!cachedCheckpoints.isEmpty()) {
            ckptCombo.getItems().setAll(cachedCheckpoints);
            if (initialComfyUiCheckpoint != null && !initialComfyUiCheckpoint.isBlank()
                    && cachedCheckpoints.contains(initialComfyUiCheckpoint)) {
                ckptCombo.setValue(initialComfyUiCheckpoint);
            } else {
                ckptCombo.getSelectionModel().selectFirst();
            }
        } else if (initialComfyUiCheckpoint != null && !initialComfyUiCheckpoint.isBlank()) {
            ckptCombo.getItems().add(initialComfyUiCheckpoint);
            ckptCombo.setValue(initialComfyUiCheckpoint);
        }
        Button refreshCkptBtn = new Button("↺");
        refreshCkptBtn.setTooltip(new Tooltip("Fetch available checkpoints from ComfyUI"));
        refreshCkptBtn.setOnAction(e -> {
            refreshCkptBtn.setDisable(true);
            refreshCkptBtn.setText("…");
            Thread.ofVirtual().start(() -> {
                List<String> fetched = fetchCheckpointsFromComfyUI(comfyUiBaseUrl);
                Platform.runLater(() -> {
                    refreshCkptBtn.setDisable(false);
                    refreshCkptBtn.setText("↺");
                    if (fetched.isEmpty()) {
                        new Alert(Alert.AlertType.WARNING,
                            "Could not reach ComfyUI or no checkpoints found.")
                            .showAndWait();
                        return;
                    }
                    String current = ckptCombo.getValue();
                    ckptCombo.getItems().setAll(fetched);
                    if (current != null && fetched.contains(current)) {
                        ckptCombo.setValue(current);
                    } else {
                        ckptCombo.getSelectionModel().selectFirst();
                    }
                    if (onComfyUiCheckpointChanged != null) {
                        onComfyUiCheckpointChanged.accept(
                            new ComfyUiCheckpointState(ckptCombo.getValue(), new ArrayList<>(fetched)));
                    }
                });
            });
        });
        ckptCombo.setOnAction(e -> {
            if (onComfyUiCheckpointChanged != null && ckptCombo.getValue() != null) {
                onComfyUiCheckpointChanged.accept(
                    new ComfyUiCheckpointState(ckptCombo.getValue(),
                        new ArrayList<>(ckptCombo.getItems())));
            }
        });
        HBox ckptRow = new HBox(6, ckptCombo, refreshCkptBtn);
        ckptRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(ckptLabel, 0, row);
        grid.add(ckptRow, 1, row); row++;

        // Grok model row — declared before Gemini slider so the slider's listener can reference it
        Label grokModelLabel = new Label("Model:");
        ComboBox<String> grokModelCombo = new ComboBox<>();
        grokModelCombo.getItems().addAll(
            "grok-imagine-image-quality",
            "grok-imagine-image"
        );
        String initGrokModel = (initialProviderParams != null && initialProviderParams.grokModel() != null)
            ? initialProviderParams.grokModel() : "grok-imagine-image-quality";
        grokModelCombo.setValue(grokModelCombo.getItems().contains(initGrokModel)
            ? initGrokModel : "grok-imagine-image-quality");
        grokModelCombo.setPrefWidth(260);
        // action handler set below after geminiTempSlider is declared

        // Gemini temperature row
        Label geminiTempLabel = new Label("Temperature:");
        Slider geminiTempSlider = new Slider(0.0, 2.0, initialProviderParams != null ? initialProviderParams.geminiTemperature() : 1.0);
        geminiTempSlider.setMajorTickUnit(0.5);
        geminiTempSlider.setMinorTickCount(4);
        geminiTempSlider.setShowTickMarks(true);
        geminiTempSlider.setShowTickLabels(true);
        geminiTempSlider.setPrefWidth(220);
        Label geminiTempValue = new Label(String.format("%.1f", geminiTempSlider.getValue()));
        geminiTempValue.setPrefWidth(30);
        geminiTempSlider.valueProperty().addListener((obs, o, n) -> {
            geminiTempValue.setText(String.format("%.1f", n.doubleValue()));
            if (onProviderParamsChanged != null) {
                onProviderParamsChanged.accept(new ProviderParams(
                    n.doubleValue(), grokModelCombo.getValue()));
            }
        });
        grokModelCombo.setOnAction(e -> {
            if (onProviderParamsChanged != null) {
                onProviderParamsChanged.accept(new ProviderParams(
                    geminiTempSlider.getValue(), grokModelCombo.getValue()));
            }
        });
        HBox geminiTempRow = new HBox(8, geminiTempSlider, geminiTempValue);
        geminiTempRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Tooltip geminiTempTip = new Tooltip(
            "Controls creativity (0 = precise/literal, 1 = balanced, 2 = highly creative)");
        geminiTempTip.setWrapText(true);
        Tooltip.install(geminiTempLabel, geminiTempTip);
        Tooltip.install(geminiTempSlider, geminiTempTip);
        grid.add(geminiTempLabel, 0, row);
        grid.add(geminiTempRow, 1, row); row++;

        grid.add(grokModelLabel, 0, row);
        grid.add(grokModelCombo, 1, row); row++;

        // Resolution
        grid.add(new Label("Resolution:"), 0, row);
        resolutionCombo = new ComboBox<>();
        resolutionCombo.getItems().addAll("Original", "2×", "4×", "Custom...");
        resolutionCombo.getSelectionModel().selectFirst();
        resolutionCombo.setPrefWidth(300);
        resolutionCombo.setMaxWidth(Double.MAX_VALUE);
        grid.add(resolutionCombo, 1, row); row++;

        HBox customResBox = new HBox(6);
        TextField widthField = new TextField();
        widthField.setPromptText("Width");
        widthField.setPrefWidth(80);
        TextField heightField = new TextField();
        heightField.setPromptText("Height");
        heightField.setPrefWidth(80);
        customResBox.getChildren().addAll(widthField, new Label("×"), heightField);
        customResBox.setVisible(false);
        customResBox.setManaged(false);
        grid.add(customResBox, 1, row); row++;
        resolutionCombo.setOnAction(e -> {
            boolean custom = "Custom...".equals(resolutionCombo.getValue());
            customResBox.setVisible(custom);
            customResBox.setManaged(custom);
        });

        // ── Separator before prompts ──────────────────────────────────────────
        grid.add(new Separator(), 0, row, 2, 1); row++;

        // ── Prompt section ────────────────────────────────────────────────────
        grid.add(new Label("Prompts:"), 0, row);

        promptCheckList = new VBox(4);
        promptScrollPane = new ScrollPane(promptCheckList);
        promptScrollPane.setPrefHeight(140);
        promptScrollPane.setPrefWidth(300);
        promptScrollPane.setMaxWidth(Double.MAX_VALUE);
        promptScrollPane.setFitToWidth(true);

        rebuildCheckBoxes(initialCheckedTitles);

        // Toolbar: Add / Edit / Delete / Up / Down
        Button addBtn = new Button("+");
        editBtn = new Button("Edit");
        deleteBtn = new Button("✕");
        Button upBtn = new Button("↑");
        Button downBtn = new Button("↓");
        addBtn.setTooltip(new Tooltip("Add new prompt"));
        editBtn.setTooltip(new Tooltip("Edit selected prompt"));
        deleteBtn.setTooltip(new Tooltip("Delete selected prompt"));
        upBtn.setTooltip(new Tooltip("Move selected prompt up"));
        downBtn.setTooltip(new Tooltip("Move selected prompt down"));

        HBox promptToolbar = new HBox(4, addBtn, editBtn, deleteBtn, upBtn, downBtn);
        promptToolbar.setAlignment(Pos.CENTER_LEFT);

        VBox promptBox = new VBox(4, promptScrollPane, promptToolbar);
        grid.add(promptBox, 1, row); row++;

        // Edit/Delete act on the first checked prompt, or the most-recently focused one
        editBtn.setOnAction(e -> {
            NamedPrompt target = getFirstCheckedPrompt();
            if (target == null || target.isDefault()) return;
            showPromptEditDialog(target).ifPresent(np -> {
                int idx = promptItems.indexOf(target);
                promptItems.set(idx, np);
                boolean wasChecked = promptCheckBoxes.containsKey(target.title())
                    && promptCheckBoxes.get(target.title()).isSelected();
                List<String> checked = getCheckedTitles();
                checked.remove(target.title());
                if (wasChecked) checked.add(np.title());
                rebuildCheckBoxes(checked);
                onPromptsChanged.accept(List.copyOf(promptItems));
                fireCheckedChanged();
            });
        });

        deleteBtn.setOnAction(e -> {
            NamedPrompt target = getFirstCheckedPrompt();
            if (target == null || target.isDefault()) return;
            promptItems.remove(target);
            List<String> checked = getCheckedTitles();
            checked.remove(target.title());
            rebuildCheckBoxes(checked);
            onPromptsChanged.accept(List.copyOf(promptItems));
            fireCheckedChanged();
        });

        upBtn.setOnAction(e -> moveSelected(-1));
        downBtn.setOnAction(e -> moveSelected(1));

        // P.S. prompt field
        Label psLabel = new Label("P.S:");
        Tooltip psTt = new Tooltip("One-off extra instruction appended to the prompt.\nNot saved to the prompt list.");
        psTt.setWrapText(true);
        psTt.setMaxWidth(280);
        Tooltip.install(psLabel, psTt);
        grid.add(psLabel, 0, row);
        TextField additionalPromptField = new TextField(initialAdditionalPrompt != null ? initialAdditionalPrompt : "");
        additionalPromptField.setPromptText("One-off extra instruction (not saved)");
        additionalPromptField.setPrefWidth(260);
        additionalPromptField.setMaxWidth(Double.MAX_VALUE);
        Button clearAdditionalBtn = new Button("✕");
        clearAdditionalBtn.setTooltip(new Tooltip("Clear additional prompt"));
        clearAdditionalBtn.setOnAction(e -> additionalPromptField.clear());
        HBox additionalBox = new HBox(4, additionalPromptField, clearAdditionalBtn);
        additionalBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(additionalBox, 1, row); row++;
        additionalPromptField.textProperty().addListener((obs, o, n) -> {
            if (onAdditionalPromptChanged != null) onAdditionalPromptChanged.accept(n);
        });

        // ── Separator before status/info ──────────────────────────────────────
        grid.add(new Separator(), 0, row, 2, 1); row++;

        // Provider warning label
        Label providerWarningLabel = new Label();
        providerWarningLabel.setStyle("-fx-text-fill: #c47000; -fx-font-style: italic;");
        providerWarningLabel.setWrapText(true);
        providerWarningLabel.setMaxWidth(300);
        providerWarningLabel.setVisible(false);
        providerWarningLabel.setManaged(false);
        grid.add(providerWarningLabel, 1, row); row++;

        // Balance + cost info row
        providerInfoLabel = new Label();
        providerInfoLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 0.9em;");
        providerInfoLabel.setWrapText(true);
        providerInfoLabel.setMaxWidth(340);
        grid.add(providerInfoLabel, 0, row, 2, 1); row++;

        addBtn.setOnAction(e -> {
            showPromptEditDialog(null).ifPresent(np -> {
                promptItems.add(np);
                List<String> checked = getCheckedTitles();
                checked.add(np.title()); // auto-check newly added
                rebuildCheckBoxes(checked);
                onPromptsChanged.accept(List.copyOf(promptItems));
                fireCheckedChanged();
            });
        });

        providerCombo.setOnAction(e -> {
            EnhancementProvider sel = providerCombo.getValue();
            boolean hasPrompt = sel == null || sel.supportsPrompt();
            promptScrollPane.setDisable(!hasPrompt);
            addBtn.setDisable(!hasPrompt);

            boolean aiControlsSize = sel instanceof com.albumorganizer.service.enhancement.GeminiProvider
                || sel instanceof com.albumorganizer.service.enhancement.GrokProvider;
            resolutionCombo.getItems().setAll(aiControlsSize
                ? List.of("By AI")
                : List.of("Original", "2×", "4×", "Custom..."));
            resolutionCombo.getSelectionModel().selectFirst();
            resolutionCombo.setDisable(aiControlsSize);
            customResBox.setVisible(false);
            customResBox.setManaged(false);

            boolean noImageInput = false;
            providerWarningLabel.setVisible(noImageInput);
            providerWarningLabel.setManaged(noImageInput);

            boolean isComfyUi = sel instanceof com.albumorganizer.service.enhancement.ComfyUIProvider;
            ckptLabel.setVisible(isComfyUi);
            ckptLabel.setManaged(isComfyUi);
            ckptRow.setVisible(isComfyUi);
            ckptRow.setManaged(isComfyUi);

            boolean isGemini = sel instanceof com.albumorganizer.service.enhancement.GeminiProvider;
            geminiTempLabel.setVisible(isGemini);
            geminiTempLabel.setManaged(isGemini);
            geminiTempRow.setVisible(isGemini);
            geminiTempRow.setManaged(isGemini);

            boolean isGrok = sel instanceof com.albumorganizer.service.enhancement.GrokProvider;
            grokModelLabel.setVisible(isGrok);
            grokModelLabel.setManaged(isGrok);
            grokModelCombo.setVisible(isGrok);
            grokModelCombo.setManaged(isGrok);

            refreshProviderInfo(sel);

            if (outputFilenameField != null && sel != null && !filenameUserOverridden) {
                outputFilenameField.setText(previewOutputFilename(sel, mediaFile.getAbsolutePath()));
            }

            if (sel != null && onProviderUsed != null) onProviderUsed.accept(sel);
        });
        providerCombo.fireEvent(new javafx.event.ActionEvent());

        // Status + progress
        statusLabel = new Label("");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        grid.add(statusLabel, 0, row, 2, 1); row++;
        grid.add(progressBar, 0, row, 2, 1); row++;

        // ── Separator before output filename + enhance button ─────────────────
        grid.add(new Separator(), 0, row, 2, 1); row++;

        // Output filename field
        outputFilenameField = new TextField();
        outputFilenameField.setMaxWidth(Double.MAX_VALUE);
        outputFilenameField.setPromptText("Output filename");
        // Initialise now that the field exists and a provider is already selected
        EnhancementProvider currentProvider = providerCombo.getValue();
        if (currentProvider != null) {
            outputFilenameField.setText(previewOutputFilename(currentProvider, mediaFile.getAbsolutePath()));
        }
        Button refreshFilenameBtn = new Button("↺");
        refreshFilenameBtn.setTooltip(new Tooltip("Generate a new filename"));
        refreshFilenameBtn.setOnAction(e -> {
            EnhancementProvider sel = providerCombo.getValue();
            if (sel != null) {
                filenameUserOverridden = false;
                outputFilenameField.setText(previewOutputFilename(sel, mediaFile.getAbsolutePath()));
            }
        });
        HBox filenameBox = new HBox(6, outputFilenameField, refreshFilenameBtn);
        filenameBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(outputFilenameField, Priority.ALWAYS);
        grid.add(new Label("Output file:"), 0, row);
        grid.add(filenameBox, 1, row); row++;

        // Enhance button
        enhanceButton = new Button("Enhance");
        enhanceButton.setPrefWidth(120);
        enhanceButton.setDefaultButton(true);

        CheckBox copyToClipboardCheckBox = new CheckBox("Copy result to clipboard");
        copyToClipboardCheckBox.setSelected(false);
        boolean isVideo = mediaFile.getType() != null
            && mediaFile.getType() == com.albumorganizer.model.MediaType.VIDEO;
        copyToClipboardCheckBox.setVisible(!isVideo);
        copyToClipboardCheckBox.setManaged(!isVideo);

        HBox enhanceRow = new HBox(12, enhanceButton, copyToClipboardCheckBox);
        enhanceRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(enhanceRow, 1, row);

        enhanceButton.setOnAction(e -> {
            // Refresh filename to ensure epoch matches actual enhancement time (skip if user/caller overrode it)
            EnhancementProvider sel = providerCombo.getValue();
            if (sel != null && !filenameUserOverridden) {
                outputFilenameField.setText(previewOutputFilename(sel, mediaFile.getAbsolutePath()));
            }
            runEnhancement(mediaFile, widthField, heightField,
                additionalPromptField.getText(), copyToClipboardCheckBox.isSelected(),
                geminiTempSlider, grokModelCombo, outputFilenameField.getText().trim());
        });

        getDialogPane().setContent(grid);
        setResultConverter(btn -> null);
    }

    private void rebuildCheckBoxes(List<String> checkedTitles) {
        promptCheckBoxes.clear();
        promptRows.clear();
        promptCheckList.getChildren().clear();
        NamedPrompt previouslyClicked = lastClickedPrompt;
        lastClickedPrompt = null;
        for (NamedPrompt np : promptItems) {
            boolean isDefault = np.isDefault();
            CheckBox cb = new CheckBox(np.title());
            if (isDefault) {
                cb.setStyle("-fx-font-weight: bold;");
            }
            Tooltip tt = new Tooltip((isDefault ? "[Default] " : "") + np.prompt());
            tt.setMaxWidth(400);
            tt.setWrapText(true);
            tt.setShowDelay(javafx.util.Duration.millis(300));
            cb.setTooltip(tt);
            cb.setSelected(checkedTitles != null && checkedTitles.contains(np.title()));
            cb.selectedProperty().addListener((obs, o, n) -> fireCheckedChanged());

            HBox row = new HBox(cb);
            row.setPadding(new Insets(2, 6, 2, 6));
            row.setMaxWidth(Double.MAX_VALUE);

            final NamedPrompt captured = np;

            // Clicking the row background (not the checkbox) only selects — does not toggle check
            row.setOnMouseClicked(e -> {
                // Only act if the click landed on the HBox itself, not on the CheckBox
                if (e.getTarget() == row) {
                    selectPrompt(captured);
                }
            });
            // Clicking the checkbox: select the row AND let the checkbox toggle normally
            cb.setOnMouseClicked(e -> selectPrompt(captured));
            cb.focusedProperty().addListener((obs, o, focused) -> {
                if (focused) selectPrompt(captured);
            });
            // Space on a selected row toggles the checkbox
            row.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE) {
                    cb.setSelected(!cb.isSelected());
                    e.consume();
                } else if (e.getCode() == KeyCode.UP) {
                    moveSelected(-1);
                    e.consume();
                } else if (e.getCode() == KeyCode.DOWN) {
                    moveSelected(1);
                    e.consume();
                }
            });

            promptCheckBoxes.put(np.title(), cb);
            promptRows.put(np.title(), row);
            promptCheckList.getChildren().add(row);

            if (previouslyClicked != null && np.title().equals(previouslyClicked.title())) {
                lastClickedPrompt = np;
            }
        }
        if (lastClickedPrompt == null && !promptItems.isEmpty()) {
            lastClickedPrompt = promptItems.get(0);
        }
        updateRowHighlights();
    }

    private void selectPrompt(NamedPrompt np) {
        lastClickedPrompt = np;
        updateRowHighlights();
        updateEditDeleteButtons();
    }

    private void updateEditDeleteButtons() {
        if (editBtn == null || deleteBtn == null) return;
        boolean isDefault = lastClickedPrompt != null && lastClickedPrompt.isDefault();
        editBtn.setDisable(isDefault);
        deleteBtn.setDisable(isDefault);
    }

    private void updateRowHighlights() {
        for (Map.Entry<String, HBox> entry : promptRows.entrySet()) {
            boolean selected = lastClickedPrompt != null
                && entry.getKey().equals(lastClickedPrompt.title());
            entry.getValue().setStyle(selected
                ? "-fx-background-color: #3d7ab5; -fx-background-radius: 3;"
                : "-fx-background-color: transparent;");
            CheckBox cb = promptCheckBoxes.get(entry.getKey());
            if (cb != null) {
                boolean isDefault = promptItems.stream()
                .filter(p -> p.title().equals(entry.getKey()))
                .findFirst().map(NamedPrompt::isDefault).orElse(false);
                String boldPart = isDefault ? "-fx-font-weight: bold;" : "";
                cb.setStyle(selected ? boldPart + " -fx-text-fill: white;" : boldPart);
            }
        }
    }

    private void moveSelected(int delta) {
        if (lastClickedPrompt == null) return;
        int idx = promptItems.indexOf(lastClickedPrompt);
        if (idx < 0) return;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= promptItems.size()) return;
        List<String> checked = getCheckedTitles();
        promptItems.remove(idx);
        promptItems.add(newIdx, lastClickedPrompt);
        rebuildCheckBoxes(checked);
        onPromptsChanged.accept(List.copyOf(promptItems));
    }

    private void fireCheckedChanged() {
        if (onCheckedTitlesChanged != null) {
            onCheckedTitlesChanged.accept(getCheckedTitles());
        }
    }

    private List<String> getCheckedTitles() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : promptCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) result.add(entry.getKey());
        }
        return result;
    }

    private NamedPrompt getFirstCheckedPrompt() {
        // Prefer the last prompt the user explicitly clicked/focused
        if (lastClickedPrompt != null && promptItems.contains(lastClickedPrompt)) {
            return lastClickedPrompt;
        }
        // Fall back to first checked
        for (NamedPrompt np : promptItems) {
            CheckBox cb = promptCheckBoxes.get(np.title());
            if (cb != null && cb.isSelected()) return np;
        }
        return promptItems.isEmpty() ? null : promptItems.get(0);
    }

    private java.util.Optional<NamedPrompt> showPromptEditDialog(NamedPrompt existing) {
        Dialog<NamedPrompt> dlg = new Dialog<>();
        dlg.setTitle(existing == null ? "Add Prompt" : "Edit Prompt");
        dlg.initOwner(getDialogPane().getScene().getWindow());
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(16));

        TextField titleField = new TextField(existing != null ? existing.title() : "");
        titleField.setPromptText("Short title, e.g. \"Sharpen portrait\"");
        titleField.setPrefWidth(320);

        TextArea promptArea = new TextArea(existing != null ? existing.prompt() : "");
        promptArea.setPromptText("Full prompt text...");
        promptArea.setPrefRowCount(4);
        promptArea.setWrapText(true);
        promptArea.setPrefWidth(320);

        g.add(new Label("Title:"), 0, 0);
        g.add(titleField, 1, 0);
        g.add(new Label("Prompt:"), 0, 1);
        g.add(promptArea, 1, 1);

        Button saveBtn = (Button) dlg.getDialogPane().lookupButton(saveType);
        saveBtn.setDisable(true);
        Runnable validate = () -> saveBtn.setDisable(
            titleField.getText().isBlank() || promptArea.getText().isBlank());
        titleField.textProperty().addListener((obs, o, n) -> validate.run());
        promptArea.textProperty().addListener((obs, o, n) -> validate.run());
        if (existing != null) validate.run();

        dlg.getDialogPane().setContent(g);
        dlg.setResultConverter(btn -> btn == saveType
            ? new NamedPrompt(existing != null ? existing.id() : UsageRepository.randomPositiveId(),
                              titleField.getText().trim(), promptArea.getText().trim())
            : null);

        return dlg.showAndWait();
    }

    private List<String> fetchCheckpointsFromComfyUI(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return List.of();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/object_info/CheckpointLoaderSimple"))
                .GET().timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();
            JsonObject root = new Gson().fromJson(resp.body(), JsonObject.class);
            if (root == null || !root.has("CheckpointLoaderSimple")) return List.of();
            JsonArray names = root.getAsJsonObject("CheckpointLoaderSimple")
                .getAsJsonObject("input")
                .getAsJsonObject("required")
                .getAsJsonArray("ckpt_name")
                .get(0).getAsJsonArray();
            List<String> result = new ArrayList<>();
            for (JsonElement el : names) result.add(el.getAsString());
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void refreshProviderInfo(EnhancementProvider provider) {
        if (provider == null) {
            providerInfoLabel.setText("");
            return;
        }
        String cost = provider.estimatedCostPerImage();
        providerInfoLabel.setText(cost != null ? "Est. cost: " + cost : "Est. cost: Free / Local");
    }

    /** Override the output filename shown in the dialog (used by clipboard flow). */
    public void setInitialOutputFilename(String filename) {
        if (outputFilenameField != null && filename != null) {
            outputFilenameField.setText(filename);
            filenameUserOverridden = true;
        }
    }

    private String previewOutputFilename(EnhancementProvider provider, Path inputPath) {
        return ImageEnhancementService.resolveOutputPath(inputPath, provider.getShortId())
            .getFileName().toString();
    }

    private void runEnhancement(MediaFile mediaFile, TextField widthField, TextField heightField,
                                String additionalPrompt, boolean copyToClipboard,
                                Slider geminiTempSlider, ComboBox<String> grokModelCombo,
                                String customFilename) {
        EnhancementProvider provider = providerCombo.getValue();
        if (provider == null) return;

        // Collect all checked prompts in order, concatenate with title headers
        String basePrompt = null;
        if (provider.supportsPrompt()) {
            List<NamedPrompt> checked = new ArrayList<>();
            for (NamedPrompt np : promptItems) {
                CheckBox cb = promptCheckBoxes.get(np.title());
                if (cb != null && cb.isSelected()) checked.add(np);
            }
            if (!checked.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (NamedPrompt np : checked) {
                    sb.append(np.title()).append(":\n").append(np.prompt()).append("\n---\n");
                }
                basePrompt = sb.toString().stripTrailing();
            }
        }

        String prompt = basePrompt;
        if (additionalPrompt != null && !additionalPrompt.isBlank()) {
            String ps = "P.S:\n" + additionalPrompt.trim();
            prompt = (prompt != null && !prompt.isBlank())
                ? prompt + "\n---\n" + ps
                : ps;
        }

        Dimension targetSize = resolveSize(mediaFile, widthField, heightField);

        Map<String, String> params = new java.util.HashMap<>();
        params.put("temperature", String.format("%.2f", geminiTempSlider.getValue()));
        params.put("model", grokModelCombo.getValue() != null ? grokModelCombo.getValue() : "grok-imagine-image-quality");

        Path customOutputPath = (customFilename != null && !customFilename.isBlank() && outputDir != null)
            ? outputDir.resolve(customFilename)
            : null;

        EnhancementRequest request = new EnhancementRequest(
            mediaFile.getAbsolutePath(), prompt, targetSize, outputDir, params, customOutputPath);

        enhanceButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Enhancing...");

        Task<EnhancementResult> task = new Task<>() {
            @Override
            protected EnhancementResult call() throws Exception {
                return provider.enhance(request);
            }
        };

        task.setOnSucceeded(ev -> {
            EnhancementResult result = task.getValue();
            progressBar.setProgress(1.0);
            if (result.success()) {
                statusLabel.setText("Saved: " + result.outputPath().getFileName());
                if (copyToClipboard) {
                    try {
                        javafx.scene.image.Image image = new javafx.scene.image.Image(
                            result.outputPath().toUri().toString());
                        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        content.putImage(image);
                        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                        logger.debug("Enhanced image copied to clipboard: {}", result.outputPath());
                    } catch (Exception ex) {
                        logger.warn("Failed to copy enhanced image to clipboard", ex);
                    }
                }
                if (onProviderUsed != null) onProviderUsed.accept(provider);
                setResult(result);
                if (debugProtocol) {
                    completedRequest = request;
                    completedResult = result;
                    try {
                        String style = getDialogPane().getStyle();
                        java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("-fx-font-size:\\s*([0-9.]+)em").matcher(style);
                        if (m.find()) capturedFontScale = Double.parseDouble(m.group(1));
                    } catch (Exception ignored) {}
                }
                close();
            } else {
                progressBar.setVisible(false);
                enhanceButton.setDisable(false);
                statusLabel.setText("Enhancement failed.");
                showError("Enhancement Failed", result.errorMessage(), result.technicalDetail());
            }
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            logger.error("Enhancement task threw an exception", ex);
            progressBar.setVisible(false);
            enhanceButton.setDisable(false);
            statusLabel.setText("Error.");
            String friendly = friendlyExceptionMessage(ex, provider.getName());
            String technical = ex != null ? ex.toString() : null;
            showError("Enhancement Error", friendly, technical);
        });

        new Thread(task, "enhancement-thread").start();
    }

    private void showError(String title, String message) {
        showError(title, message, null);
    }

    private void showError(String title, String message, String technicalDetail) {
        logger.error("{}: {} | technical: {}", title, message, technicalDetail);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(getDialogPane().getScene().getWindow());
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        Label friendlyLabel = new Label(message != null ? message : "An unexpected error occurred.");
        friendlyLabel.setWrapText(true);
        friendlyLabel.setMaxWidth(Double.MAX_VALUE);
        friendlyLabel.setStyle("-fx-font-size: 1.05em;");

        VBox content = new VBox(10, friendlyLabel);
        content.setPadding(new Insets(10));

        if (technicalDetail != null && !technicalDetail.isBlank()) {
            TitledPane techPane = new TitledPane();
            techPane.setText("Technical details");
            techPane.setExpanded(false);

            TextArea techArea = new TextArea(technicalDetail);
            techArea.setEditable(false);
            techArea.setWrapText(true);
            techArea.setPrefWidth(520);
            techArea.setPrefHeight(200);
            techArea.setMaxHeight(Double.MAX_VALUE);

            Button copyBtn = new Button("Copy to clipboard");
            copyBtn.setOnAction(ev -> {
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString((message != null ? message + "\n\n" : "") + technicalDetail);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                copyBtn.setText("Copied!");
            });
            VBox techBox = new VBox(6, techArea, copyBtn);

            techPane.setContent(techBox);
            techPane.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(techPane, Priority.ALWAYS);
            content.getChildren().add(techPane);

            // Auto-resize window when TitledPane is expanded/collapsed
            techPane.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                javafx.application.Platform.runLater(() -> {
                    if (dialog.getDialogPane().getScene() != null && 
                        dialog.getDialogPane().getScene().getWindow() != null) {
                        dialog.getDialogPane().getScene().getWindow().sizeToScene();
                    }
                });
            });
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.showAndWait();
    }

    private String friendlyExceptionMessage(Throwable ex, String providerName) {
        if (ex == null) return "Unknown error.";
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        if (cause instanceof java.net.ConnectException || msg.contains("connection refused") || msg.contains("connect")) {
            return providerName + " could not be reached. Make sure the server is running and the URL in Settings is correct.";
        }
        if (cause instanceof java.net.UnknownHostException || msg.contains("unknown host")) {
            return "Could not resolve the server address for " + providerName + ". Check the URL in Settings.";
        }
        if (cause instanceof java.net.SocketTimeoutException || msg.contains("timed out") || msg.contains("timeout")) {
            return providerName + " took too long to respond. The server may be overloaded or offline.";
        }
        if (msg.contains("401") || msg.contains("unauthorized")) {
            return "Authentication failed for " + providerName + ". Check your API key in Settings.";
        }
        if (msg.contains("403") || msg.contains("forbidden")) {
            return "Access denied by " + providerName + ". Your API key may not have permission for this operation.";
        }
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("too many requests")) {
            return providerName + " rate limit reached. Wait a moment and try again.";
        }
        return ex.getMessage() != null ? ex.getMessage() : "Unexpected error from " + providerName + ".";
    }

    private Dimension resolveSize(MediaFile mediaFile, TextField widthField, TextField heightField) {
        String res = resolutionCombo.getValue();
        java.awt.Dimension orig = mediaFile.getResolution();
        if (orig == null) orig = new java.awt.Dimension(1024, 768);

        return switch (res) {
            case "2×" -> new Dimension(orig.width * 2, orig.height * 2);
            case "4×" -> new Dimension(orig.width * 4, orig.height * 4);
            case "Custom..." -> {
                try {
                    yield new Dimension(Integer.parseInt(widthField.getText()),
                                        Integer.parseInt(heightField.getText()));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
