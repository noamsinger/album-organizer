package com.albumorganizer;

import com.albumorganizer.controller.MainController;
import com.albumorganizer.repository.ConfigRepository;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main application entry point for Album Organizer.
 */
public class AlbumOrganizerApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(AlbumOrganizerApp.class);
    private ConfigRepository configRepository;
    private MainController mainController;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting Album Organizer application");

        try {
            configRepository = new ConfigRepository();

            // Set application icon
            try {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/app-icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warn("Failed to load application icon: {}", e.getMessage());
            }

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/albumorganizer/view/MainView.fxml"));
            Parent root = loader.load();

            // Get controller reference
            mainController = loader.getController();

            // Create scene
            Scene scene = new Scene(root,
                configRepository.getWindowWidth(),
                configRepository.getWindowHeight());

            // Set scene fill to pale orange for window chrome
            scene.setFill(javafx.scene.paint.Color.web("#FFE5CC"));

            // Add stylesheet
            try {
                String stylesheet = getClass().getResource("/com/albumorganizer/styles.css").toExternalForm();
                scene.getStylesheets().add(stylesheet);
                logger.info("Loaded stylesheet: {}", stylesheet);
            } catch (Exception e) {
                logger.error("Failed to load stylesheet", e);
            }

            // Setup stage
            primaryStage.setTitle("Album Organizer");
            primaryStage.setScene(scene);

            // Restore window position if saved
            double x = configRepository.getWindowX();
            double y = configRepository.getWindowY();
            if (x >= 0 && y >= 0) {
                primaryStage.setX(x);
                primaryStage.setY(y);
            }

            // Save window size and position on close, and save snapshot
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Window close requested");
                try {
                    configRepository.setWindowWidth(primaryStage.getWidth());
                    configRepository.setWindowHeight(primaryStage.getHeight());
                    configRepository.setWindowX(primaryStage.getX());
                    configRepository.setWindowY(primaryStage.getY());
                    logger.info("Window position saved");

                    // Save snapshot before closing
                    if (mainController != null) {
                        logger.info("Calling saveSnapshotOnShutdown");
                        mainController.saveSnapshotOnShutdown();
                        logger.info("Snapshot saved on window close");
                    } else {
                        logger.warn("MainController is null, cannot save snapshot");
                    }
                } catch (Exception e) {
                    logger.error("Error during window close", e);
                }
            });

            primaryStage.show();
            logger.info("Application started successfully");

        } catch (IOException e) {
            logger.error("Failed to load FXML", e);
            throw new RuntimeException("Failed to start application", e);
        }
    }

    @Override
    public void stop() {
        logger.info("Stopping Album Organizer application");
        // Additional cleanup can happen here
    }

    public static void main(String[] args) {
        // Set macOS application name (appears in menu bar)
        // This must be set before Application.launch()
        System.setProperty("apple.awt.application.name", "Album Organizer");

        // Also try the alternative property
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Album Organizer");

        // Enable custom window appearance on macOS
        System.setProperty("prism.order", "sw");
        System.setProperty("glass.win.uiScale", "1.0");

        launch(args);
    }
}
