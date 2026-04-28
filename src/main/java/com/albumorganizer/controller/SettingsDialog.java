package com.albumorganizer.controller;

import com.albumorganizer.model.AlbumOrganizerSettings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

/**
 * Settings dialog for configuring organize options.
 */
public class SettingsDialog extends Dialog<AlbumOrganizerSettings> {

    private final ChoiceBox<String> modeChoice;
    private final CheckBox createYearFolderCheck;
    private final CheckBox createMonthFolderCheck;
    private final CheckBox createDayFolderCheck;
    private final CheckBox splitResolutionCheck;
    private final TextField lowResPixelsField;
    private final TextField hiResPixelsField;

    public SettingsDialog(AlbumOrganizerSettings currentSettings, double fontScale) {
        setTitle("Settings");
        setHeaderText("Organize Settings");

        // Apply font scale to dialog
        getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        int row = 0;

        // Organize mode
        Label modeLabel = new Label("Organize with:");
        modeChoice = new ChoiceBox<>();
        modeChoice.getItems().addAll("Copy", "Move");
        modeChoice.setValue(currentSettings.getMode() == AlbumOrganizerSettings.OrganizeMode.COPY ? "Copy" : "Move");
        modeChoice.setDisable(true); // Keep disabled for now
        grid.add(modeLabel, 0, row);
        grid.add(modeChoice, 1, row);
        row++;

        // Create Year Folder
        createYearFolderCheck = new CheckBox("Organize with Year Folder");
        createYearFolderCheck.setSelected(currentSettings.isCreateYearFolder());
        grid.add(createYearFolderCheck, 0, row, 2, 1);
        row++;

        // Create Month Folder
        createMonthFolderCheck = new CheckBox("Organize with Month Folder");
        createMonthFolderCheck.setSelected(currentSettings.isCreateMonthFolder());
        grid.add(createMonthFolderCheck, 0, row, 2, 1);
        row++;

        // Create Day Folder
        createDayFolderCheck = new CheckBox("Organize with Day Folder");
        createDayFolderCheck.setSelected(currentSettings.isCreateDayFolder());
        grid.add(createDayFolderCheck, 0, row, 2, 1);
        row++;

        // Split by Resolution (combined checkbox)
        splitResolutionCheck = new CheckBox("Split to low resolution folders (low-res/med-res)");
        splitResolutionCheck.setSelected(currentSettings.isSplitLowRes() || currentSettings.isSplitMedRes());
        grid.add(splitResolutionCheck, 0, row, 2, 1);
        row++;

        // Low-res threshold (in total pixels)
        Label lowResLabel = new Label("Low-res threshold (pixels):");
        lowResPixelsField = new TextField(String.valueOf(currentSettings.getLowResThresholdPixels()));
        lowResPixelsField.setPrefWidth(150);
        grid.add(lowResLabel, 0, row);
        grid.add(lowResPixelsField, 1, row);
        row++;

        // Hi-res threshold (in total pixels)
        Label hiResLabel = new Label("Hi-res threshold (pixels):");
        hiResPixelsField = new TextField(String.valueOf(currentSettings.getHiResThresholdPixels()));
        hiResPixelsField.setPrefWidth(150);
        grid.add(hiResLabel, 0, row);
        grid.add(hiResPixelsField, 1, row);

        // Set up listeners for cascading enable/disable
        createYearFolderCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            createMonthFolderCheck.setDisable(!newVal);
            if (!newVal) {
                createMonthFolderCheck.setSelected(false);
            }
        });

        createMonthFolderCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            createDayFolderCheck.setDisable(!newVal);
            if (!newVal) {
                createDayFolderCheck.setSelected(false);
            }
        });

        splitResolutionCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            lowResPixelsField.setDisable(!newVal);
            hiResPixelsField.setDisable(!newVal);
        });

        // Initialize disabled states
        createMonthFolderCheck.setDisable(!createYearFolderCheck.isSelected());
        createDayFolderCheck.setDisable(!createMonthFolderCheck.isSelected());
        lowResPixelsField.setDisable(!splitResolutionCheck.isSelected());
        hiResPixelsField.setDisable(!splitResolutionCheck.isSelected());

        getDialogPane().setContent(grid);

        // Add validation to Save button
        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String validationError = validateSettings();
            if (validationError != null) {
                // Show error and consume the event to prevent dialog from closing
                event.consume();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Validation Error");
                alert.setHeaderText("Invalid Settings");
                alert.setContentText(validationError);
                alert.showAndWait();
            }
        });

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return buildSettings();
            }
            return null;
        });
    }

    /**
     * Validates the settings and returns an error message if invalid, or null if valid.
     */
    private String validateSettings() {
        // Validate threshold values
        try {
            int lowRes = Integer.parseInt(lowResPixelsField.getText());
            int hiRes = Integer.parseInt(hiResPixelsField.getText());

            if (lowRes <= 0) {
                return "Low-res threshold must be a positive number.";
            }

            if (hiRes <= lowRes) {
                return "Hi-res threshold must be greater than low-res threshold.\n\n" +
                       "Current values:\n" +
                       "Low-res: " + lowRes + " pixels\n" +
                       "Hi-res: " + hiRes + " pixels";
            }
        } catch (NumberFormatException e) {
            return "Threshold values must be valid numbers.";
        }

        return null; // Valid
    }

    private AlbumOrganizerSettings buildSettings() {
        AlbumOrganizerSettings settings = new AlbumOrganizerSettings();

        settings.setMode(modeChoice.getValue().equals("Copy")
            ? AlbumOrganizerSettings.OrganizeMode.COPY
            : AlbumOrganizerSettings.OrganizeMode.MOVE);
        settings.setCreateYearFolder(createYearFolderCheck.isSelected());
        settings.setCreateMonthFolder(createMonthFolderCheck.isSelected());
        settings.setCreateDayFolder(createDayFolderCheck.isSelected());

        boolean splitRes = splitResolutionCheck.isSelected();
        settings.setSplitLowRes(splitRes);
        settings.setSplitMedRes(splitRes);

        try {
            settings.setLowResThresholdPixels(Integer.parseInt(lowResPixelsField.getText()));
            settings.setHiResThresholdPixels(Integer.parseInt(hiResPixelsField.getText()));
        } catch (NumberFormatException e) {
            // Keep defaults if parsing fails
        }

        return settings;
    }
}
