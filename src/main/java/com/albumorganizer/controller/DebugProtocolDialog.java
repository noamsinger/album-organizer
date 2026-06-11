package com.albumorganizer.controller;

import com.albumorganizer.service.enhancement.EnhancementRequest;
import com.albumorganizer.service.enhancement.EnhancementResult;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.FontWeight;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Post-enhancement debug dialog showing source image, result image,
 * request data (images redacted), and result data (images redacted).
 */
public class DebugProtocolDialog extends Dialog<Void> {

    private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile(
        "(?i)(\"(?:image|data|content|inline_data|b64_json|url)\"\\s*:\\s*\"(?:data:[^,]+,)?)[A-Za-z0-9+/]{20,}={0,2}(\"?)");
    private static final Pattern DATA_URI_PATTERN = Pattern.compile(
        "(?i)(data:[a-z]+/[a-z0-9.+-]+;base64,)[A-Za-z0-9+/]{20,}={0,2}");

    public DebugProtocolDialog(EnhancementRequest request, EnhancementResult result,
                               String rawRequestJson, String rawResponseJson,
                               double fontScale) {
        setTitle("Debug Protocol — Enhancement Details");
        setResizable(true);
        getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // ── Source image (top-left) ───────────────────────────────────────────
        ScrollPane sourceScroll = wrapInScroll(loadImageView(request.inputPath()));
        VBox sourceBox = quadrantBox("Source Image", sourceScroll);

        // ── Result image (bottom-left) ────────────────────────────────────────
        Node resultContent;
        if (result.success() && result.outputPath() != null) {
            resultContent = wrapInScroll(loadImageView(result.outputPath()));
        } else {
            resultContent = centeredLabel(result.errorMessage() != null
                ? "Error: " + result.errorMessage() : "No result image");
        }
        VBox resultBox = quadrantBox("Result Image", resultContent);

        // ── Request info (top-right) ──────────────────────────────────────────
        VBox requestBox = quadrantBox("Request Info", makeTextArea(buildRequestText(request, rawRequestJson)));

        // ── Response info (bottom-right) ──────────────────────────────────────
        VBox responseBox = quadrantBox("Result Info", makeTextArea(buildResponseText(result, rawResponseJson)));

        // Left column: source + result images
        SplitPane leftSplit = new SplitPane(sourceBox, resultBox);
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.setDividerPositions(0.5);
        SplitPane.setResizableWithParent(sourceBox, true);
        SplitPane.setResizableWithParent(resultBox, true);

        // Right column: request + response info
        SplitPane rightSplit = new SplitPane(requestBox, responseBox);
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.5);
        SplitPane.setResizableWithParent(requestBox, true);
        SplitPane.setResizableWithParent(responseBox, true);

        // Main horizontal split
        SplitPane mainSplit = new SplitPane(leftSplit, rightSplit);
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.5);
        SplitPane.setResizableWithParent(leftSplit, true);
        SplitPane.setResizableWithParent(rightSplit, true);
        mainSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        getDialogPane().setContent(mainSplit);
        getDialogPane().setPrefWidth(1100);
        getDialogPane().setPrefHeight(750);
        setResultConverter(btn -> null);
    }

    /** A VBox with a bold header label + content node that grows to fill. */
    private VBox quadrantBox(String title, Node content) {
        Label header = new Label(title);
        header.setStyle("-fx-font-weight: bold; -fx-padding: 4 6 4 6; -fx-background-color: #e0e0e0;");
        header.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(header, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        box.setStyle("-fx-border-color: #aaa; -fx-border-width: 1;");
        return box;
    }

    private ImageView loadImageView(Path path) {
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.setSmooth(true);
        if (path != null && Files.exists(path)) {
            try {
                view.setImage(new Image(path.toUri().toString(), true));
            } catch (Exception ignored) {}
        }
        return view;
    }

    private ScrollPane wrapInScroll(ImageView view) {
        StackPane stack = new StackPane(view);
        stack.setStyle("-fx-background-color: #2a2a2a;");
        ScrollPane scroll = new ScrollPane(stack);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        view.fitWidthProperty().bind(scroll.widthProperty().subtract(20));
        return scroll;
    }

    private Label centeredLabel(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-text-fill: #c00; -fx-font-style: italic; -fx-padding: 12;");
        return lbl;
    }

    private TextArea makeTextArea(String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle("-fx-font-family: monospace; -fx-font-size: 0.85em;");
        area.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private String buildRequestText(EnhancementRequest request, String rawJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Enhancement Request ===\n\n");
        sb.append("Input:   ").append(request.inputPath()).append("\n");
        sb.append("Output:  ").append(request.outputDir() != null ? request.outputDir() : "(next to source)").append("\n");
        sb.append("Size:    ").append(request.targetSize() != null
            ? request.targetSize().width + "×" + request.targetSize().height : "original").append("\n");
        sb.append("Prompt:  ").append(request.prompt() != null ? request.prompt() : "(none)").append("\n");
        if (request.params() != null && !request.params().isEmpty()) {
            sb.append("\n--- Params ---\n");
            for (Map.Entry<String, String> e : request.params().entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
            }
        }
        if (rawJson != null && !rawJson.isBlank()) {
            sb.append("\n--- Raw Request Body (images redacted) ---\n");
            sb.append(redactImages(rawJson));
        }
        return sb.toString();
    }

    private String buildResponseText(EnhancementResult result, String rawJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Enhancement Result ===\n\n");
        sb.append("Success:  ").append(result.success()).append("\n");
        if (result.outputPath() != null) {
            sb.append("Output:   ").append(result.outputPath()).append("\n");
            try {
                long size = Files.size(result.outputPath());
                sb.append("Size:     ").append(size / 1024).append(" KB\n");
            } catch (Exception ignored) {}
        }
        if (result.errorMessage() != null) {
            sb.append("\n--- Error ---\n").append(result.errorMessage()).append("\n");
        }
        if (result.technicalDetail() != null && !result.technicalDetail().isBlank()) {
            sb.append("\n--- Technical Detail ---\n").append(result.technicalDetail()).append("\n");
        }
        if (rawJson != null && !rawJson.isBlank()) {
            sb.append("\n--- Raw Response Body (images redacted) ---\n");
            sb.append(redactImages(rawJson));
        }
        return sb.toString();
    }

    static String redactImages(String text) {
        if (text == null) return "";
        String r = BASE64_IMAGE_PATTERN.matcher(text).replaceAll("$1XXXXXXXX$2");
        return DATA_URI_PATTERN.matcher(r).replaceAll("$1XXXXXXXX");
    }
}

