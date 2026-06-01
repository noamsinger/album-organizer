package com.albumorganizer.controller;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.service.enhancement.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EnhancementDialog extends Dialog<EnhancementResult> {

    private static final Logger logger = LoggerFactory.getLogger(EnhancementDialog.class);

    private final ComboBox<EnhancementProvider> providerCombo;
    private final ComboBox<String> resolutionCombo;

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

    private final Consumer<List<NamedPrompt>> onPromptsChanged;
    private final Consumer<EnhancementProvider> onProviderUsed;
    private final Consumer<List<String>> onCheckedTitlesChanged;
    private final Consumer<String> onAdditionalPromptChanged;
    private final Path outputDir; // null = next to original; non-null = use this dir

    public EnhancementDialog(MediaFile mediaFile,
                             List<EnhancementProvider> providers,
                             List<NamedPrompt> savedPrompts,
                             double fontScale,
                             EnhancementProvider initialProvider,
                             List<String> initialCheckedTitles,
                             String initialAdditionalPrompt,
                             Consumer<List<NamedPrompt>> onPromptsChanged,
                             Consumer<EnhancementProvider> onProviderUsed,
                             Consumer<List<String>> onCheckedTitlesChanged,
                             Consumer<String> onAdditionalPromptChanged,
                             Path outputDir) {
        this.onPromptsChanged = onPromptsChanged;
        this.onProviderUsed = onProviderUsed;
        this.onCheckedTitlesChanged = onCheckedTitlesChanged;
        this.onAdditionalPromptChanged = onAdditionalPromptChanged;
        this.outputDir = outputDir;
        promptItems.addAll(savedPrompts);

        setTitle("Enhance with AI");
        setHeaderText("Enhance: " + mediaFile.getFilename());
        getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));

        // No explicit buttons — window [X] and Esc close the dialog
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.CANCEL).setVisible(false);
        getDialogPane().lookupButton(ButtonType.CANCEL).setManaged(false);

        getDialogPane().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

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
        grid.add(providerCombo, 1, row); row++;

        // Resolution
        grid.add(new Label("Resolution:"), 0, row);
        resolutionCombo = new ComboBox<>();
        resolutionCombo.getItems().addAll("Original", "2×", "4×", "Custom...");
        resolutionCombo.getSelectionModel().selectFirst();
        resolutionCombo.setPrefWidth(300);
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

        // ── Prompt section ────────────────────────────────────────────────────
        grid.add(new Label("Prompts:"), 0, row);

        promptCheckList = new VBox(4);
        promptScrollPane = new ScrollPane(promptCheckList);
        promptScrollPane.setPrefHeight(140);
        promptScrollPane.setPrefWidth(300);
        promptScrollPane.setFitToWidth(true);

        rebuildCheckBoxes(initialCheckedTitles);

        // Toolbar: Add / Edit / Delete / Up / Down
        Button addBtn = new Button("+");
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("✕");
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
            if (target == null) return;
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
            if (target == null) return;
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
        Button clearAdditionalBtn = new Button("✕");
        clearAdditionalBtn.setTooltip(new Tooltip("Clear additional prompt"));
        clearAdditionalBtn.setOnAction(e -> additionalPromptField.clear());
        HBox additionalBox = new HBox(4, additionalPromptField, clearAdditionalBtn);
        additionalBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(additionalBox, 1, row); row++;
        additionalPromptField.textProperty().addListener((obs, o, n) -> {
            if (onAdditionalPromptChanged != null) onAdditionalPromptChanged.accept(n);
        });

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

            refreshProviderInfo(sel);
        });
        providerCombo.fireEvent(new javafx.event.ActionEvent());

        // Status + progress
        statusLabel = new Label("");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);
        grid.add(statusLabel, 0, row, 2, 1); row++;
        grid.add(progressBar, 0, row, 2, 1); row++;

        // Enhance button
        enhanceButton = new Button("Enhance");
        enhanceButton.setPrefWidth(120);
        enhanceButton.setDefaultButton(true);
        grid.add(enhanceButton, 1, row);

        enhanceButton.setOnAction(e -> runEnhancement(mediaFile, widthField, heightField, additionalPromptField.getText()));

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
            CheckBox cb = new CheckBox(np.title());
            Tooltip tt = new Tooltip(np.prompt());
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
                cb.setStyle(selected ? "-fx-text-fill: white;" : "");
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
            ? new NamedPrompt(titleField.getText().trim(), promptArea.getText().trim())
            : null);

        return dlg.showAndWait();
    }

    private void refreshProviderInfo(EnhancementProvider provider) {
        if (provider == null) {
            providerInfoLabel.setText("");
            return;
        }
        String cost = provider.estimatedCostPerImage();
        providerInfoLabel.setText(cost != null ? "Est. cost: " + cost : "Est. cost: Free / Local");
    }

    private void runEnhancement(MediaFile mediaFile, TextField widthField, TextField heightField, String additionalPrompt) {
        EnhancementProvider provider = providerCombo.getValue();
        if (provider == null) return;

        // Collect all checked prompts in order, concatenate
        String basePrompt = null;
        if (provider.supportsPrompt()) {
            List<String> parts = new ArrayList<>();
            for (NamedPrompt np : promptItems) {
                CheckBox cb = promptCheckBoxes.get(np.title());
                if (cb != null && cb.isSelected()) parts.add(np.prompt());
            }
            if (!parts.isEmpty()) basePrompt = String.join(". ", parts);
        }

        String prompt = basePrompt;
        if (additionalPrompt != null && !additionalPrompt.isBlank()) {
            prompt = (prompt != null && !prompt.isBlank())
                ? prompt + ". " + additionalPrompt.trim()
                : additionalPrompt.trim();
        }

        Dimension targetSize = resolveSize(mediaFile, widthField, heightField);
        EnhancementRequest request = new EnhancementRequest(mediaFile.getAbsolutePath(), prompt, targetSize, outputDir);

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
                if (onProviderUsed != null) onProviderUsed.accept(provider);
                setResult(result);
                close();
            } else {
                progressBar.setVisible(false);
                enhanceButton.setDisable(false);
                statusLabel.setText("Enhancement failed.");
                showError("Enhancement Failed", result.errorMessage());
            }
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            logger.error("Enhancement task threw an exception", ex);
            progressBar.setVisible(false);
            enhanceButton.setDisable(false);
            statusLabel.setText("Error.");
            showError("Enhancement Error", msg);
        });

        new Thread(task, "enhancement-thread").start();
    }

    private void showError(String title, String message) {
        logger.error("{}: {}", title, message);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(getDialogPane().getScene().getWindow());
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea textArea = new TextArea(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefWidth(560);
        textArea.setPrefHeight(220);

        VBox content = new VBox(8,
            new Label("Enhancement failed. You can select and copy the error text below:"),
            textArea);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
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
