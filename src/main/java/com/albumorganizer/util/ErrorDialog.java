package com.albumorganizer.util;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for displaying error dialogs with stack traces.
 */
public class ErrorDialog {

    private static final Logger logger = LoggerFactory.getLogger(ErrorDialog.class);

    /**
     * Shows an error dialog with exception details.
     *
     * @param title     the dialog title
     * @param header    the header text
     * @param content   the content message
     * @param exception the exception (optional)
     */
    public static void show(String title, String header, String content, Throwable exception) {
        logger.error("{}: {}", title, content, exception);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        // Show stack trace if exception provided
        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            String exceptionText = sw.toString();

            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(textArea, 0, 0);

            alert.getDialogPane().setExpandableContent(expContent);
        }

        alert.showAndWait();
    }

    /**
     * Shows an error dialog without exception details.
     *
     * @param title   the dialog title
     * @param header  the header text
     * @param content the content message
     */
    public static void show(String title, String header, String content) {
        show(title, header, content, null);
    }

    private ErrorDialog() {
        // Utility class
    }
}
