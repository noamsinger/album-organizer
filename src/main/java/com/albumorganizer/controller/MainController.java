package com.albumorganizer.controller;

import com.albumorganizer.model.DirectoryNode;
import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.FileIndexEntry;
import javafx.collections.FXCollections;
import com.albumorganizer.model.AlbumOrganizerSettings;
import com.albumorganizer.model.ScanResult;
import com.albumorganizer.repository.ConfigRepository;
import com.albumorganizer.repository.SnapshotRepository;
import com.albumorganizer.service.ScannerService;
import com.albumorganizer.service.DirectoryScanService;
import com.albumorganizer.service.FileOrganizeService;
import com.albumorganizer.service.OrganizeReportWriter;
import com.albumorganizer.task.ScanTask;
import com.albumorganizer.util.ErrorDialog;
import com.albumorganizer.util.FileTrashUtil;
import com.albumorganizer.util.FormatUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Main controller for the application window.
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String NO_HASH_KEY = "__NO_HASH__"; // Special key for files without hashes (quick scan)

    @FXML private BorderPane rootPane;
    @FXML private SplitPane mainSplitPane;
    @FXML private TreeView<DirectoryNode> directoryTree;
    @FXML private AnchorPane mediaViewContainer;
    @FXML private TableView<MediaFile> mediaTable;
    @FXML private ScrollPane thumbnailScrollPane;
    @FXML private FlowPane thumbnailPane;
    @FXML private RadioMenuItem listViewMenuItem;
    @FXML private RadioMenuItem thumbnailViewMenuItem;
    @FXML private TableColumn<MediaFile, String> filenameColumn;
    @FXML private TableColumn<MediaFile, Instant> dateColumn;
    @FXML private TableColumn<MediaFile, MediaFile> dateTakenColumn;
    @FXML private TableColumn<MediaFile, String> typeColumn;
    @FXML private TableColumn<MediaFile, Number> durationColumn;
    @FXML private TableColumn<MediaFile, Number> sizeColumn;
    @FXML private TableColumn<MediaFile, String> resolutionColumn;
    @FXML private TableColumn<MediaFile, String> locationColumn;
    @FXML private TableColumn<MediaFile, String> hashColumn;
    @FXML private Label statusLabel;
    @FXML private Label fileCountLabel;
    @FXML private Label errorCountLabel;

    // Simplified Progress Panel Components
    @FXML private VBox progressPanel;
    @FXML private ProgressBar progressBar;
    @FXML private ListView<String> progressLogList;
    @FXML private CheckMenuItem showProgressPanelMenuItem;
    @FXML private Button viewReportButton;
    @FXML private Button stopProgressButton;
    @FXML private Button hideProgressButton;

    private final ScannerService scannerService;
    private final DirectoryScanService directoryScanService;
    private final ConfigRepository configRepository;
    private final SnapshotRepository snapshotRepository;
    private final Map<String, List<FileIndexEntry>> fileIndex; // Hash -> List of (directory, filename) pairs
    private List<Path> baseFolders;
    private List<Path> currentlyScannedFolders; // Track which folders are being scanned
    private ScanTask currentScanTask;
    private Task<?> currentOrganizeTask; // Track organize task for stop button
    private String preservedStatusMessage = null; // Preserve status message across automatic rescans
    private Path lastOrganizeReportFile = null; // Track last organization report file
    private boolean thumbnailViewActive = false;
    private ObservableList<MediaFile> currentDisplayedFiles;
    private ExecutorService thumbnailLoadExecutor;
    private AlbumOrganizerSettings settings; // All settings including targetFolder and fontSizeFactor
    private Path currentSelectedDirectory; // Currently selected directory for on-demand scanning
    // REMOVED: private int progressFileCounter = 0; // Counter for discovered files during scan

    public MainController() {
        this.scannerService = new ScannerService();
        this.directoryScanService = new DirectoryScanService();
        this.configRepository = new ConfigRepository();
        this.snapshotRepository = new SnapshotRepository();
        this.fileIndex = new HashMap<>();
        this.baseFolders = new ArrayList<>();
        this.currentlyScannedFolders = new ArrayList<>();
        this.currentDisplayedFiles = FXCollections.observableArrayList();
        this.thumbnailLoadExecutor = Executors.newFixedThreadPool(4);
    }

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");

        // Setup table columns
        setupTableColumns();

        // Setup table double-click handler
        setupTableDoubleClick();

        // Setup table context menu
        setupTableContextMenu();

        // Setup directory tree selection
        setupDirectoryTree();

        // Setup directory tree context menu
        setupTreeContextMenu();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Load settings including font size and target folder FIRST
        settings = configRepository.getOrganizeSettings();
        applyFontSize();

        // Load saved folders (needs settings to be initialized)
        loadBaseFolders();

        // Load snapshot from compressed cache
        loadSnapshot();

        // Build initial tree with album folders (even if not scanned)
        buildDirectoryTree();

        // Initialize with empty displayed files (will scan on selection)
        currentDisplayedFiles.clear();

        // Run quick scan on startup for all album folders
        if (!baseFolders.isEmpty()) {
            logger.info("Running startup quick scan on {} album folders", baseFolders.size());
            Platform.runLater(() -> startStartupQuickScan());
        }

        logger.info("MainController initialized");
    }

    private void setupTableColumns() {
        // Filename column
        filenameColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getFilename()));

        // Date column - use UTC format like Date Taken
        dateColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getLastModified()));
        dateColumn.setCellFactory(column -> new TableCell<MediaFile, Instant>() {
            @Override
            protected void updateItem(Instant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Use same format as Date Taken (UTC format)
                    setText(FormatUtils.formatDateTaken(item, false));
                }
            }
        });

        // Type column
        typeColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getType().toString()));

        // Date Taken column
        dateTakenColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        dateTakenColumn.setCellFactory(column -> new TableCell<MediaFile, MediaFile>() {
            @Override
            protected void updateItem(MediaFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.getDateTaken() == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatDateTaken(item.getDateTaken(), item.isDateEstimated()));
                }
            }
        });

        // Duration column (for videos)
        durationColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getDurationSeconds()));
        durationColumn.setCellFactory(column -> new TableCell<MediaFile, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatDuration(item.longValue()));
                }
            }
        });

        // Size column
        sizeColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleLongProperty(data.getValue().getSizeBytes()));
        sizeColumn.setCellFactory(column -> new TableCell<MediaFile, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatSize(item.longValue()));
                }
            }
        });

        // Resolution column
        resolutionColumn.setCellValueFactory(data -> {
            MediaFile file = data.getValue();
            if (file.getResolution() != null) {
                return new javafx.beans.property.SimpleStringProperty(
                    FormatUtils.formatResolution(file.getResolution().width, file.getResolution().height));
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });

        // Location column - HIDDEN
        locationColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getLocation()));
        locationColumn.setVisible(false);

        // Hash column - show full hash
        hashColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getSha1Hash()));

        // Set table items
        mediaTable.setItems(currentDisplayedFiles);

        // Set up row factory for duplicate/corrupted highlighting
        setupRowFactory();
    }

    private void setupRowFactory() {
        mediaTable.setRowFactory(tv -> {
            TableRow<MediaFile> row = new TableRow<MediaFile>() {
                @Override
                protected void updateItem(MediaFile item, boolean empty) {
                    super.updateItem(item, empty);

                    // Clear all style classes first
                    getStyleClass().removeAll("duplicate", "corrupted");

                    if (empty || item == null) {
                        // Remove inline styles to let CSS handle everything
                    } else {
                        String hash = item.getSha1Hash();

                        // Check if this file is a duplicate (fileIndex has multiple entries for this hash)
                        // Exclude NO_HASH_KEY files (quick scanned without hash)
                        boolean isDuplicate = hash != null && !hash.isEmpty() && !hash.equals(NO_HASH_KEY)
                            && fileIndex.containsKey(hash)
                            && fileIndex.get(hash).size() > 1;

                        // Corrupted files - add CSS class only
                        if (item.isCorrupted()) {
                            getStyleClass().add("corrupted");
                            logger.info("Styling CORRUPTED: {}", item.getFilename());
                        }
                        // Duplicate files - add CSS class only
                        else if (isDuplicate) {
                            getStyleClass().add("duplicate");
                        }
                        // Normal files have no special class
                    }
                }
            };

            // Double-click handler
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openFile(row.getItem());
                }
            });

            return row;
        });
    }

    private void setupTableDoubleClick() {
        // Row factory setup is now handled in setupRowFactory() during initialization
        // This method is kept for backwards compatibility but does nothing
    }

    private void setupTableContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem openItem = new MenuItem("Open File");
        openItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openFile(selected);
            }
        });

        MenuItem showInFolderItem = new MenuItem("Show in Folder");
        showInFolderItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showInFolder(selected);
            }
        });

        MenuItem copyPathItem = new MenuItem("Copy Full Path");
        copyPathItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyPathToClipboard(selected);
            }
        });

        MenuItem copyFilenameItem = new MenuItem("Copy Filename");
        copyFilenameItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyFilenameToClipboard(selected);
            }
        });

        MenuItem removeOtherDuplicatesItem = new MenuItem("Remove Other Duplicates");
        removeOtherDuplicatesItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                removeOtherDuplicates(selected);
            }
        });

        MenuItem organizeFileItem = new MenuItem("Organize File");
        organizeFileItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                organizeFile(selected);
            }
        });

        // Enable/disable menu items based on context
        contextMenu.setOnShowing(event -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            boolean hasDuplicates = selected != null && hasDuplicates(selected);
            removeOtherDuplicatesItem.setDisable(!hasDuplicates);

            // Only enable "Organize File" if target folder is set
            boolean hasTargetFolder = settings != null && settings.getTargetFolder() != null;
            organizeFileItem.setDisable(!hasTargetFolder);
        });

        contextMenu.getItems().addAll(
            openItem,
            showInFolderItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyFilenameItem,
            new SeparatorMenuItem(),
            removeOtherDuplicatesItem,
            new SeparatorMenuItem(),
            organizeFileItem
        );

        mediaTable.setContextMenu(contextMenu);
    }

    private void setupTreeContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem fullScanWithHashItem = new MenuItem("Full Scan with Hash");
        fullScanWithHashItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().getPath() != null) {
                fullScanWithHashFolder(selected.getValue().getPath());
            }
        });

        MenuItem quickScanItem = new MenuItem("Quick Scan");
        quickScanItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().getPath() != null) {
                quickScanFolder(selected.getValue().getPath());
            }
        });

        MenuItem openInBrowserItem = new MenuItem("Open in File Browser");
        openInBrowserItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().getPath() != null) {
                openInFileBrowser(selected.getValue().getPath());
            }
        });

        MenuItem removeAlbumItem = new MenuItem("Remove from Album Folders");
        removeAlbumItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().isAlbum() && selected.getValue().getPath() != null) {
                removeAlbumFolder(selected.getValue().getPath());
            }
        });

        MenuItem makeTargetItem = new MenuItem("Make Target Folder");
        makeTargetItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().isAlbum() && selected.getValue().getPath() != null) {
                setTargetFolder(selected.getValue().getPath());
            }
        });

        MenuItem clearTargetItem = new MenuItem("Unset as Target Folder");
        clearTargetItem.setOnAction(e -> {
            clearTargetFolder();
        });

        MenuItem organizeFolderRecursivelyItem = new MenuItem("Organize Folder Recursively");
        organizeFolderRecursivelyItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().getPath() != null) {
                organizeFolderRecursively(selected.getValue());
            }
        });

        // Enable/disable menu items based on selection
        contextMenu.setOnShowing(event -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            boolean isAlbum = selected != null && selected.getValue().isAlbum();
            boolean hasPath = selected != null && selected.getValue().getPath() != null;
            boolean isCurrentTarget = selected != null && selected.getValue().getPath() != null
                && selected.getValue().getPath().equals(settings.getTargetFolder());
            boolean canOrganizeRecursively = selected != null && selected.getValue().getRecursiveFileCount() > 0
                && settings.getTargetFolder() != null;

            fullScanWithHashItem.setDisable(!hasPath);
            quickScanItem.setDisable(!hasPath);
            removeAlbumItem.setDisable(!isAlbum);
            makeTargetItem.setDisable(!isAlbum || isCurrentTarget);
            clearTargetItem.setDisable(!isCurrentTarget);
            organizeFolderRecursivelyItem.setDisable(!canOrganizeRecursively);
        });

        contextMenu.getItems().addAll(
            fullScanWithHashItem,
            quickScanItem,
            new SeparatorMenuItem(),
            openInBrowserItem,
            new SeparatorMenuItem(),
            organizeFolderRecursivelyItem,
            new SeparatorMenuItem(),
            makeTargetItem,
            clearTargetItem,
            new SeparatorMenuItem(),
            removeAlbumItem
        );

        directoryTree.setContextMenu(contextMenu);
    }

    private void setupKeyboardShortcuts() {
        rootPane.setOnKeyPressed(event -> {
            if (event.isShortcutDown()) {
                switch (event.getCode()) {
                    case EQUALS:
                    case PLUS:
                        onIncreaseFontSize();
                        event.consume();
                        break;
                    case MINUS:
                        onDecreaseFontSize();
                        event.consume();
                        break;
                    case DIGIT0:
                    case NUMPAD0:
                        onResetFontSize();
                        event.consume();
                        break;
                }
            }
        });
        logger.debug("Keyboard shortcuts configured");
    }

    private void setupDirectoryTree() {
        // Tree selection listener - triggers on-demand scan of selected directory
        directoryTree.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null && newValue.getValue().getPath() != null) {
                    onDirectorySelected(newValue.getValue().getPath());
                }
            });

        // Custom cell factory for bold album folders and purple target folder
        directoryTree.setCellFactory(tv -> new TreeCell<DirectoryNode>() {
            @Override
            protected void updateItem(DirectoryNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                } else {
                    setText(item.toString());
                    // Tooltip with tilde-shortened full path
                    if (item.getPath() != null) {
                        String fullPath = item.getPath().toString()
                            .replace(System.getProperty("user.home"), "~");
                        setTooltip(new Tooltip(fullPath));
                    } else {
                        setTooltip(null);
                    }
                    // Target folder: purple and bold
                    if (item.getPath() != null && item.getPath().equals(settings.getTargetFolder())) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: purple;");
                    }
                    // Regular album folder: bold
                    else if (item.isAlbum()) {
                        setStyle("-fx-font-weight: bold;");
                    }
                    // Normal folder
                    else {
                        setStyle("");
                    }
                }
            }
        });
    }

    /**
     * Called when user selects a directory in the tree.
     * Performs non-recursive quick scan of that directory and displays results.
     */
    private void onDirectorySelected(Path directory) {
        if (directory == null) {
            return;
        }

        currentSelectedDirectory = directory;
        logger.debug("Directory selected: {}", directory);

        // Build hash lookup from fileIndex to avoid recalculating hashes on display
        Map<Path, String> knownHashes = new HashMap<>();
        fileIndex.forEach((hash, entries) -> {
            if (!hash.equals(NO_HASH_KEY)) {
                for (FileIndexEntry entry : entries) {
                    if (entry.getDirectory().equals(directory)) {
                        knownHashes.put(entry.getAbsolutePath(), hash);
                    }
                }
            }
        });

        // Scan the directory (non-recursive) in background
        Task<List<MediaFile>> scanTask = new Task<>() {
            @Override
            protected List<MediaFile> call() {
                return directoryScanService.scanDirectory(directory, knownHashes);
            }
        };

        scanTask.setOnSucceeded(event -> {
            List<MediaFile> files = scanTask.getValue();
            currentDisplayedFiles.clear();
            currentDisplayedFiles.addAll(files);

            // Log corrupted files
            long corruptedCount = files.stream().filter(MediaFile::isCorrupted).count();
            if (corruptedCount > 0) {
                logger.info("Loaded {} corrupted files from directory", corruptedCount);
                files.stream().filter(MediaFile::isCorrupted).limit(3).forEach(f ->
                    logger.info("Corrupted file in view: {} - isCorrupted={}", f.getFilename(), f.isCorrupted())
                );
            }

            updateFileCount(files.size());
            highlightDuplicates();
            refreshCurrentView();
            logger.debug("Loaded {} files from: {}", files.size(), directory);
        });

        scanTask.setOnFailed(event -> {
            logger.error("Failed to scan directory: {}", directory, scanTask.getException());
            statusLabel.setText("Error scanning directory");
        });

        // Run in background thread
        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void openFile(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getAbsolutePath() == null) {
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                File file = mediaFile.getAbsolutePath().toFile();
                if (file.exists()) {
                    desktop.open(file);
                    logger.info("Opened file: {}", file);
                } else {
                    showError("File Not Found", "File does not exist: " + file);
                }
            } else {
                showError("Not Supported", "Opening files is not supported on this platform");
            }
        } catch (IOException e) {
            logger.error("Failed to open file: {}", mediaFile.getAbsolutePath(), e);
            showError("Error Opening File", "Failed to open file: " + e.getMessage());
        }
    }

    private void showInFolder(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getAbsolutePath() == null) {
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                File folder = mediaFile.getAbsolutePath().getParent().toFile();
                if (folder.exists()) {
                    desktop.open(folder);
                    logger.info("Opened folder: {}", folder);
                } else {
                    showError("Folder Not Found", "Folder does not exist: " + folder);
                }
            } else {
                showError("Not Supported", "Opening folders is not supported on this platform");
            }
        } catch (IOException e) {
            logger.error("Failed to open folder: {}", mediaFile.getAbsolutePath().getParent(), e);
            showError("Error Opening Folder", "Failed to open folder: " + e.getMessage());
        }
    }

    private void copyPathToClipboard(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getAbsolutePath() == null) {
            return;
        }

        String fullPath = mediaFile.getAbsolutePath().toString();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(fullPath);
        clipboard.setContent(content);

        logger.debug("Copied path to clipboard: {}", fullPath);
        statusLabel.setText("Full path copied to clipboard");
        logger.debug("Copied path to clipboard: {}", mediaFile.getAbsolutePath());
    }

    private void copyHashToClipboard(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getSha1Hash() == null) {
            return;
        }

        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(mediaFile.getSha1Hash());
        clipboard.setContent(content);

        statusLabel.setText("Hash copied to clipboard");
        logger.debug("Copied hash to clipboard: {}", mediaFile.getSha1Hash());
    }

    private void copyFilenameToClipboard(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getFilename() == null) {
            return;
        }

        String filename = mediaFile.getFilename();
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(filename);
        clipboard.setContent(content);

        logger.debug("Copied filename to clipboard: {}", filename);
        statusLabel.setText("Filename copied to clipboard");
    }

    private void setTargetFolder(Path path) {
        settings.setTargetFolder(path);
        configRepository.setOrganizeSettings(settings);
        buildDirectoryTree(); // Rebuild to update colors
        statusLabel.setText("Target folder set: " + path.getFileName());
        logger.info("Set target folder: {}", path);
    }

    private void clearTargetFolder() {
        settings.setTargetFolder(null);
        configRepository.setOrganizeSettings(settings);
        buildDirectoryTree(); // Rebuild to update colors
        statusLabel.setText("Target folder cleared");
        logger.info("Cleared target folder");
    }

    private void openInFileBrowser(Path path) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
                logger.info("Opened in file browser: {}", path);
            }
        } catch (IOException e) {
            logger.error("Failed to open in file browser: {}", path, e);
            showError("Error", "Failed to open folder: " + e.getMessage());
        }
    }

    private void removeAlbumFolder(Path path) {
        baseFolders.remove(path);
        configRepository.setBaseFolders(baseFolders);

        // Remove files from removed folder from the index
        fileIndex.values().forEach(entries ->
            entries.removeIf(entry -> entry.getDirectory().startsWith(path))
        );
        // Remove empty hash entries
        fileIndex.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Clear current display if showing files from removed folder
        if (currentSelectedDirectory != null && currentSelectedDirectory.startsWith(path)) {
            currentDisplayedFiles.clear();
            currentSelectedDirectory = null;
        }

        // Rebuild tree
        buildDirectoryTree();

        // Update UI
        updateFileCount(currentDisplayedFiles.size());
        statusLabel.setText("Removed folder: " + path.getFileName());
        logger.info("Removed album folder: {}", path);
    }

    private void filterTableByDirectory(DirectoryNode node) {
        // This method is no longer used - replaced by onDirectorySelected
        // Keeping for backwards compatibility but does nothing
    }

    @FXML
    private void onSettings() {
        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        SettingsDialog dialog = new SettingsDialog(settings, fontScale);

        dialog.showAndWait().ifPresent(newSettings -> {
            // Update organize settings but preserve targetFolder and fontSizeFactor
            newSettings.setTargetFolder(settings.getTargetFolder());
            newSettings.setFontSizeFactor(settings.getFontSizeFactor());
            settings = newSettings;
            configRepository.setOrganizeSettings(settings);
            statusLabel.setText("Settings saved");
            logger.info("Settings updated: mode={}, createYear={}, createMonth={}, createDay={}, splitLowRes={}, splitMedRes={}",
                newSettings.getMode(), newSettings.isCreateYearFolder(), newSettings.isCreateMonthFolder(),
                newSettings.isCreateDayFolder(), newSettings.isSplitLowRes(), newSettings.isSplitMedRes());
        });
    }

    @FXML
    private void onSelectFolders() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Add Album Folder");

        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            Path newPath = selectedDirectory.toPath();
            addAlbumFolder(newPath, false);
        }
    }

    @FXML
    private void onAddTargetFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Add Target Folder");

        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            Path newPath = selectedDirectory.toPath();
            addAlbumFolder(newPath, true);
        }
    }

    private void addAlbumFolder(Path newPath, boolean setAsTarget) {
        // Check if already added
        if (baseFolders.contains(newPath)) {
            statusLabel.setText("Folder already added: " + newPath.getFileName());
            showWarning("Duplicate Folder", "This folder is already an album folder.");
            return;
        }

        // Check if the new path is inside any existing album folder
        for (Path existingPath : baseFolders) {
            if (newPath.startsWith(existingPath)) {
                statusLabel.setText("Cannot add subfolder of existing album");
                showWarning("Invalid Folder Selection",
                    String.format("Cannot add '%s' because it is inside the existing album folder '%s'.",
                    newPath.getFileName(), existingPath.getFileName()));
                return;
            }
        }

        // Check if the new path contains any existing album folder
        for (Path existingPath : baseFolders) {
            if (existingPath.startsWith(newPath)) {
                statusLabel.setText("Cannot add parent of existing album");
                showWarning("Invalid Folder Selection",
                    String.format("Cannot add '%s' because it contains the existing album folder '%s'.",
                    newPath.getFileName(), existingPath.getFileName()));
                return;
            }
        }

        // Add to base folders
        baseFolders.add(newPath);
        configRepository.setBaseFolders(baseFolders);

        // Set as target folder if requested
        if (setAsTarget) {
            settings.setTargetFolder(newPath);
            configRepository.setOrganizeSettings(settings);
            logger.info("Set new folder as target folder: {}", newPath);
        }

        // Rebuild tree to show new album folder
        buildDirectoryTree();

        String message = setAsTarget ?
            "Added and set as target: " + newPath.getFileName() :
            "Added folder: " + newPath.getFileName();
        statusLabel.setText(message);
        logger.info("Added album folder: {}", newPath);

        // Automatically run full scan with hash on the newly added folder
        fullScanWithHashFolder(newPath);
    }

    private void fullScanWithHashFolder(Path path) {
        // Scan only this specific folder, don't modify baseFolders
        List<Path> foldersToScan = new ArrayList<>();
        foldersToScan.add(path);
        startScan(foldersToScan, true);
    }

    private void quickScanFolder(Path path) {
        // Scan only this specific folder, don't modify baseFolders
        List<Path> foldersToScan = new ArrayList<>();
        foldersToScan.add(path);
        startScan(foldersToScan, false);
    }

    private void startScan(List<Path> foldersToScan, boolean isFullScanWithHash) {
        if (currentScanTask != null && currentScanTask.isRunning()) {
            logger.warn("Cannot start scan - another scan is already in progress");
            showWarning("Scan In Progress", "A scan is already in progress.");
            return;
        }

        logger.info("Starting {} scan of {} folders", isFullScanWithHash ? "full scan with hash" : "quick", foldersToScan.size());

        // Update status bar at start
        statusLabel.setText(String.format("Starting %s scan of %d folder(s)...",
            isFullScanWithHash ? "full scan with hash" : "quick", foldersToScan.size()));

        // Disable View Report button ONLY if this is not an automatic rescan after organize
        // (preservedStatusMessage is set when an organize completes and triggers automatic rescan)
        if (preservedStatusMessage == null) {
            viewReportButton.setDisable(true);
        }

        // Track which folders are being scanned
        currentlyScannedFolders = new ArrayList<>(foldersToScan);

        // Create scan task with current file index
        currentScanTask = new ScanTask(scannerService, foldersToScan, isFullScanWithHash, fileIndex);

        // Show and configure scan panel
        showProgressPanel(isFullScanWithHash);

        // Feed file discovery events into the activity log (suppressed for post-organize rescans)
        if (preservedStatusMessage == null) {
            scannerService.setFileDiscoveryCallback(file -> {
                String action = isFullScanWithHash ? "Hashed" : "Found";
                String dir = file.getAbsolutePath().getParent().toString()
                    .replace(System.getProperty("user.home"), "~");
                logProgress(action + ": " + file.getFilename() + " ; at " + dir);
            });
        } else {
            scannerService.setFileDiscoveryCallback(null);
        }

        // Bind scan panel to task
        bindScanPanelToTask();

        // Handle task completion
        currentScanTask.setOnSucceeded(this::onScanSucceeded);
        currentScanTask.setOnFailed(this::onScanFailed);
        currentScanTask.setOnCancelled(event -> {
            onScanCancelled();
        });

        // Start task in background thread
        Thread thread = new Thread(currentScanTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void showProgressPanel(boolean isDeepScan) {
        stopProgressButton.setDisable(false);
        hideProgressButton.setDisable(false);

        // Only clear the log and reset progress bar when this is a fresh user-initiated operation,
        // not a post-organize automatic rescan (preservedStatusMessage is set in that case)
        if (preservedStatusMessage == null) {
            progressLogList.getItems().clear();
            progressBar.setProgress(0);
            progressBar.setVisible(false);
            progressBar.setManaged(false);
        }

        progressPanel.setVisible(true);
        progressPanel.setManaged(true);
        showProgressPanelMenuItem.setSelected(true);
    }

    /** Appends a line to the activity log, capping at 42 entries and auto-scrolling. */
    private void logProgress(String message) {
        Platform.runLater(() -> {
            ObservableList<String> items = progressLogList.getItems();
            if (items.size() >= 42) {
                if (!items.get(0).equals("...")) {
                    items.add(0, "...");
                }
                items.remove(1);
            }
            items.add(message);
            progressLogList.scrollTo(items.size() - 1);
        });
    }

    private void bindScanPanelToTask() {
        if (currentScanTask == null) {
            return;
        }

        // Unbind any previous bindings

        // Bind status message
    }

    @FXML
    private void onStopProgress() {
        if (currentScanTask != null && currentScanTask.isRunning()) {
            currentScanTask.cancel();
            scannerService.cancelScan();
            logger.info("User requested scan cancellation");
        } else if (currentOrganizeTask != null && currentOrganizeTask.isRunning()) {
            currentOrganizeTask.cancel();
            logger.info("User requested organize cancellation");
        }
    }

    @FXML
    private void onHideProgress() {
        progressPanel.setVisible(false);
        progressPanel.setManaged(false);
        showProgressPanelMenuItem.setSelected(false);
        logger.info("Scan panel hidden");
    }

    @FXML
    private void onViewReport() {
        if (lastOrganizeReportFile != null && Files.exists(lastOrganizeReportFile)) {
            try {
                // Open the report file with the system's default text editor
                java.awt.Desktop.getDesktop().open(lastOrganizeReportFile.toFile());
                logger.info("Opened report file: {}", lastOrganizeReportFile);
            } catch (Exception e) {
                logger.error("Failed to open report file", e);
                showError("Error", "Failed to open report file:\n" + e.getMessage());
            }
        }
    }

    private void onScanCancelled() {
        Platform.runLater(() -> {
            // Unbind first
            // REMOVED: progressBar.progressProperty().unbind();

            // REMOVED: progressTitleLabel.setText("Operation Cancelled");
            stopProgressButton.setDisable(true);
            statusLabel.setText("Operation cancelled");

            // Clear callback and task
            scannerService.setFileDiscoveryCallback(null);
            currentScanTask = null;
        });
    }


    private void onScanSucceeded(WorkerStateEvent event) {
        ScanResult result = (ScanResult) event.getSource().getValue();
        logger.info("Scan completed: {}", result);

        // Update UI
        Platform.runLater(() -> {
            // Unbind first
            // REMOVED: progressBar.progressProperty().unbind();

            // Update scan panel with final statistics
            // REMOVED: progressTitleLabel.setText("Scan Completed Successfully");

            // Build statistics summary
            StringBuilder summary = new StringBuilder();
            summary.append(String.format("Total files found: %d\n", result.getAllFiles().size()));
            summary.append(String.format("Scan duration: %s\n", result.getScanDuration()));
            summary.append(String.format("New files: %d\n", result.getNewFiles().size()));
            summary.append(String.format("Modified files: %d\n", result.getModifiedFiles().size()));
            summary.append(String.format("Deleted files: %d\n", result.getDeletedFiles().size()));
            if (result.getErrors().size() > 0) {
                summary.append(String.format("Errors: %d", result.getErrors().size()));
            }

            stopProgressButton.setDisable(true); // Disable stop button when scan completes

            // Clear callback and task
            scannerService.setFileDiscoveryCallback(null);
            currentScanTask = null;

            // Build a map of existing files to preserve their hashes
            Map<Path, String> existingHashes = new HashMap<>();
            for (Path scannedFolder : currentlyScannedFolders) {
                fileIndex.forEach((hash, entries) -> {
                    for (FileIndexEntry entry : entries) {
                        if (entry.getDirectory().startsWith(scannedFolder)) {
                            Path fullPath = entry.getAbsolutePath();
                            // Don't preserve NO_HASH_KEY entries - they're placeholders
                            if (!hash.equals(NO_HASH_KEY)) {
                                existingHashes.put(fullPath, hash);
                            }
                        }
                    }
                });
            }

            // Remove old index entries from the scanned folders only
            for (Path scannedFolder : currentlyScannedFolders) {
                fileIndex.values().forEach(entries ->
                    entries.removeIf(entry -> entry.getDirectory().startsWith(scannedFolder))
                );
            }
            // Remove empty hash entries
            fileIndex.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            // Add new scan results to index (hash -> list of (directory, filename, lastModified))
            for (MediaFile file : result.getAllFiles()) {
                Path fullPath = file.getAbsolutePath();
                String hash = file.getSha1Hash();

                // If quick scan (no hash) but we have an existing hash for this file, use the existing hash
                if ((hash == null || hash.isEmpty()) && existingHashes.containsKey(fullPath)) {
                    hash = existingHashes.get(fullPath);
                    logger.debug("Preserving existing hash for file: {}", file.getFilename());
                }

                // Use special key for files without hash (from quick scan with no prior hash)
                String indexKey = (hash != null && !hash.isEmpty()) ? hash : NO_HASH_KEY;

                Path directory = file.getAbsolutePath().getParent();
                String filename = file.getFilename();
                Instant lastModified = file.getLastModified(); // File modification date

                fileIndex.computeIfAbsent(indexKey, k -> new ArrayList<>())
                         .add(new FileIndexEntry(directory, filename, lastModified));
            }

            // Debug: Log any corrupted files found
            long corruptedCount = result.getAllFiles().stream()
                .filter(MediaFile::isCorrupted)
                .peek(f -> logger.warn("Found corrupted file: {}", f.getFilename()))
                .count();
            if (corruptedCount > 0) {
                logger.info("Total corrupted files found: {}", corruptedCount);
            }

            // If currently viewing one of the scanned folders, refresh it
            if (currentSelectedDirectory != null) {
                for (Path scannedFolder : currentlyScannedFolders) {
                    if (currentSelectedDirectory.startsWith(scannedFolder)) {
                        onDirectorySelected(currentSelectedDirectory);
                        break;
                    }
                }
            }

            buildDirectoryTree();
            updateErrorCount(result.getErrors().size());

            // Check if we have a preserved status message (e.g., from organize operation)
            if (preservedStatusMessage != null) {
                statusLabel.setText(preservedStatusMessage);
                preservedStatusMessage = null; // Clear after use
            } else {
                statusLabel.setText(String.format("Scan complete: %d files indexed in %s",
                    result.getTotalScanned(), result.getScanDuration()));
            }

            // Save last scan date
            configRepository.setLastScanDate(Instant.now());
        });
    }

    private void onScanFailed(WorkerStateEvent event) {
        Throwable exception = event.getSource().getException();
        logger.error("Scan failed", exception);

        Platform.runLater(() -> {
            // Unbind first
            // REMOVED: progressBar.progressProperty().unbind();

            // Update scan panel
            // REMOVED: progressTitleLabel.setText("Operation Failed");
            stopProgressButton.setDisable(true);

            // Clear callback and task
            scannerService.setFileDiscoveryCallback(null);
            currentScanTask = null;

            statusLabel.setText("Scan failed");
            ErrorDialog.show("Operation Failed",
                           "An error occurred",
                           exception.getMessage(),
                           exception);
        });
    }

    private void highlightDuplicates() {
        // Find duplicate hashes from fileIndex (hashes with multiple files)
        // Exclude NO_HASH_KEY since those files haven't been hashed yet
        int duplicateHashCount = (int) fileIndex.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(NO_HASH_KEY))
            .filter(entry -> entry.getValue().size() > 1)
            .count();

        logger.info("Found {} unique duplicate hashes in global index", duplicateHashCount);

        // Count duplicates in current view
        int duplicateCount = (int) currentDisplayedFiles.stream()
            .filter(f -> {
                String hash = f.getSha1Hash();
                return hash != null && !hash.isEmpty() && !hash.equals(NO_HASH_KEY)
                    && fileIndex.containsKey(hash) && fileIndex.get(hash).size() > 1;
            })
            .count();

        logger.info("Should be highlighting {} duplicate files (out of {} total)", duplicateCount, currentDisplayedFiles.size());

        // Force table to refresh all rows by triggering a layout update
        mediaTable.refresh();
    }

    private void buildDirectoryTree() {
        // Remember currently selected directory path before rebuilding
        Path currentlySelectedPath = null;
        TreeItem<DirectoryNode> currentSelection = directoryTree.getSelectionModel().getSelectedItem();
        if (currentSelection != null && currentSelection.getValue() != null) {
            currentlySelectedPath = currentSelection.getValue().getPath();
        }

        TreeItem<DirectoryNode> root = new TreeItem<>(new DirectoryNode(null, "All Album Folders"));
        root.setExpanded(true);

        TreeItem<DirectoryNode> firstAlbum = null;
        TreeItem<DirectoryNode> itemToSelect = null;

        // First, add album folders (base folders) at the top
        for (Path baseFolder : baseFolders) {
            DirectoryNode albumNode = new DirectoryNode(baseFolder, baseFolder.getFileName() != null ?
                baseFolder.getFileName().toString() : baseFolder.toString(), true);
            TreeItem<DirectoryNode> albumItem = new TreeItem<>(albumNode);
            albumItem.setExpanded(true);

            // Build hierarchical subtree for this album
            buildSubtree(albumItem, baseFolder);

            root.getChildren().add(albumItem);

            // Remember first album
            if (firstAlbum == null) {
                firstAlbum = albumItem;
            }

            // Check if this is the previously selected path
            if (currentlySelectedPath != null && baseFolder.equals(currentlySelectedPath)) {
                itemToSelect = albumItem;
            }
        }

        // Sort root level album folders case-insensitively
        root.getChildren().sort((item1, item2) -> {
            String name1 = item1.getValue().getDisplayName();
            String name2 = item2.getValue().getDisplayName();
            return name1.compareToIgnoreCase(name2);
        });

        directoryTree.setRoot(root);

        // If we didn't find the previously selected path yet, search in subtrees
        if (itemToSelect == null && currentlySelectedPath != null) {
            itemToSelect = findTreeItemByPath(root, currentlySelectedPath);
        }

        // Select the previously selected item, or first album as fallback
        if (itemToSelect != null) {
            directoryTree.getSelectionModel().select(itemToSelect);
        } else if (firstAlbum != null) {
            directoryTree.getSelectionModel().select(firstAlbum);
        }
    }

    /**
     * Recursively searches for a TreeItem with the specified path.
     */
    private TreeItem<DirectoryNode> findTreeItemByPath(TreeItem<DirectoryNode> root, Path targetPath) {
        if (root == null || targetPath == null) {
            return null;
        }

        // Check current item
        if (root.getValue() != null && targetPath.equals(root.getValue().getPath())) {
            return root;
        }

        // Search in children
        for (TreeItem<DirectoryNode> child : root.getChildren()) {
            TreeItem<DirectoryNode> found = findTreeItemByPath(child, targetPath);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Sorts tree items recursively in case-insensitive alphabetical order.
     */
    private void sortTreeItemsRecursively(TreeItem<DirectoryNode> item) {
        if (item == null || item.getChildren().isEmpty()) {
            return;
        }

        // Sort children case-insensitively
        item.getChildren().sort((item1, item2) -> {
            String name1 = item1.getValue().getDisplayName();
            String name2 = item2.getValue().getDisplayName();
            return name1.compareToIgnoreCase(name2);
        });

        // Recursively sort grandchildren
        for (TreeItem<DirectoryNode> child : item.getChildren()) {
            sortTreeItemsRecursively(child);
        }
    }

    private void buildSubtree(TreeItem<DirectoryNode> parentItem, Path basePath) {
        // Get all unique directories from the file index under this base path
        Set<Path> dirsWithFiles = fileIndex.values().stream()
            .flatMap(List::stream)
            .map(FileIndexEntry::getDirectory)
            .filter(dir -> dir.startsWith(basePath))
            .collect(Collectors.toSet());

        // Build complete directory hierarchy including intermediate directories
        Map<Path, TreeItem<DirectoryNode>> pathToItem = new java.util.HashMap<>();
        pathToItem.put(basePath, parentItem);

        // Sort directories by path depth to ensure parents are created before children
        List<Path> sortedDirs = dirsWithFiles.stream()
            .filter(dir -> !dir.equals(basePath))
            .sorted(java.util.Comparator.comparingInt(p -> p.getNameCount()))
            .collect(Collectors.toList());

        for (Path dir : sortedDirs) {
            // Find or create parent
            Path parent = dir.getParent();
            TreeItem<DirectoryNode> parentTreeItem = pathToItem.get(parent);

            // If parent doesn't exist in tree yet, create intermediate nodes
            if (parentTreeItem == null) {
                parentTreeItem = createIntermediatePath(pathToItem, basePath, parent);
            }

            // Create this directory node
            DirectoryNode node = new DirectoryNode(dir);
            long fileCount = fileIndex.values().stream()
                .flatMap(List::stream)
                .filter(entry -> entry.getDirectory().equals(dir))
                .count();
            node.setMediaFileCount((int) fileCount);
            node.setScanned(true);

            TreeItem<DirectoryNode> item = new TreeItem<>(node);
            parentTreeItem.getChildren().add(item);
            pathToItem.put(dir, item);
        }

        // Sort all children recursively (case-insensitive)
        sortTreeItemsRecursively(parentItem);

        // Update file count for album node
        long albumFileCount = fileIndex.values().stream()
            .flatMap(List::stream)
            .filter(entry -> entry.getDirectory().equals(basePath))
            .count();
        parentItem.getValue().setMediaFileCount((int) albumFileCount);
        parentItem.getValue().setScanned(true);

        // Calculate and set recursive counts for all nodes in the tree
        calculateRecursiveCounts(parentItem);
    }

    /**
     * Recursively calculates and sets the recursive file count for each node in the tree.
     * Returns the total count for this subtree.
     */
    private int calculateRecursiveCounts(TreeItem<DirectoryNode> item) {
        DirectoryNode node = item.getValue();
        if (node == null) {
            return 0;
        }

        // Start with direct files in this directory
        int totalCount = node.getMediaFileCount();

        // Add counts from all children
        for (TreeItem<DirectoryNode> child : item.getChildren()) {
            totalCount += calculateRecursiveCounts(child);
        }

        // Set the recursive count for this node
        node.setRecursiveFileCount(totalCount);

        return totalCount;
    }

    private TreeItem<DirectoryNode> createIntermediatePath(Map<Path, TreeItem<DirectoryNode>> pathToItem,
                                                            Path basePath, Path targetPath) {
        // Create all intermediate directories from base to target
        List<Path> pathsToCreate = new ArrayList<>();
        Path current = targetPath;

        while (current != null && !current.equals(basePath) && !pathToItem.containsKey(current)) {
            pathsToCreate.add(current);
            current = current.getParent();
        }

        // Reverse to create from parent to child
        java.util.Collections.reverse(pathsToCreate);

        TreeItem<DirectoryNode> parentItem = pathToItem.get(current != null ? current : basePath);

        for (Path path : pathsToCreate) {
            DirectoryNode node = new DirectoryNode(path);
            node.setScanned(true);
            node.setMediaFileCount(0); // Intermediate directories have 0 direct files
            TreeItem<DirectoryNode> item = new TreeItem<>(node);
            parentItem.getChildren().add(item);
            pathToItem.put(path, item);
            parentItem = item;
        }

        return parentItem;
    }

    private void updateFileCount(int count) {
        fileCountLabel.setText(count + (count == 1 ? " file" : " files"));
    }

    private void updateErrorCount(int count) {
        errorCountLabel.setText(count + (count == 1 ? " error" : " errors"));
    }

    private void loadBaseFolders() {
        baseFolders = configRepository.getBaseFolders();
        if (!baseFolders.isEmpty()) {
            statusLabel.setText("Loaded " + baseFolders.size() + " saved folder(s)");
            logger.info("Loaded {} base folders", baseFolders.size());
        }
        if (settings.getTargetFolder() != null) {
            logger.info("Loaded target folder: {}", settings.getTargetFolder());
        }
    }

    private void loadSnapshot() {
        Map<String, List<FileIndexEntry>> snapshot = snapshotRepository.loadSnapshot();
        if (!snapshot.isEmpty()) {
            fileIndex.putAll(snapshot);
            logger.info("Loaded snapshot with {} hash entries", snapshot.size());

            // Count total files from snapshot
            int totalFiles = snapshot.values().stream()
                .mapToInt(List::size)
                .sum();
            statusLabel.setText(String.format("Loaded snapshot: %d files indexed", totalFiles));
        }
    }

    private void saveSnapshot() {
        snapshotRepository.saveSnapshot(fileIndex);
        logger.info("Saved snapshot with {} hash entries", fileIndex.size());
    }

    /**
     * Public method to save snapshot on application shutdown.
     * Called by AlbumOrganizerApp when window is closed.
     */
    public void saveSnapshotOnShutdown() {
        logger.info("saveSnapshotOnShutdown called");
        saveSnapshot();
        logger.info("saveSnapshotOnShutdown completed");
    }

    /**
     * Runs a quick scan on startup for all album folders.
     */
    private void startStartupQuickScan() {
        if (baseFolders.isEmpty()) {
            return;
        }

        logger.info("Starting startup quick scan of {} album folders", baseFolders.size());
        statusLabel.setText("Running startup quick scan...");

        // Track which folders are being scanned
        currentlyScannedFolders = new ArrayList<>(baseFolders);

        // Create scan task with current file index
        currentScanTask = new ScanTask(scannerService, baseFolders, false, fileIndex); // false = quick scan

        // Show and configure scan panel
        showProgressPanel(false); // false = quick scan

        // Feed file discovery events into the activity log
        scannerService.setFileDiscoveryCallback(file -> {
            String dir = file.getAbsolutePath().getParent().toString()
                .replace(System.getProperty("user.home"), "~");
            logProgress("Found: " + file.getFilename() + " ; at " + dir);
        });

        // Bind scan panel to task
        bindScanPanelToTask();

        // Handle task completion
        currentScanTask.setOnSucceeded(event -> {
            ScanResult result = (ScanResult) event.getSource().getValue();
            logger.info("Startup quick scan completed: {}", result);

            Platform.runLater(() -> {
                // Unbind first
                // REMOVED: progressBar.progressProperty().unbind();

                // Update scan panel with summary
                // REMOVED: progressTitleLabel.setText("Startup Scan Complete");
                StringBuilder summary = new StringBuilder();
                summary.append(String.format("Total files found: %d\n", result.getAllFiles().size()));
                summary.append(String.format("Scan duration: %s\n", result.getScanDuration()));
                stopProgressButton.setDisable(true);

                // Clear callback and task
                scannerService.setFileDiscoveryCallback(null);
                currentScanTask = null;

                // Build a map of existing files to preserve their hashes
                Map<Path, String> existingHashes = new HashMap<>();
                for (Path scannedFolder : currentlyScannedFolders) {
                    fileIndex.forEach((hash, entries) -> {
                        for (FileIndexEntry entry : entries) {
                            if (entry.getDirectory().startsWith(scannedFolder)) {
                                Path fullPath = entry.getAbsolutePath();
                                // Don't preserve NO_HASH_KEY entries - they're placeholders
                                if (!hash.equals(NO_HASH_KEY)) {
                                    existingHashes.put(fullPath, hash);
                                }
                            }
                        }
                    });
                }

                // Remove old index entries from scanned folders
                for (Path scannedFolder : currentlyScannedFolders) {
                    fileIndex.values().forEach(entries ->
                        entries.removeIf(entry -> entry.getDirectory().startsWith(scannedFolder))
                    );
                }
                // Remove empty hash entries
                fileIndex.entrySet().removeIf(entry -> entry.getValue().isEmpty());

                // Add new scan results to index
                for (MediaFile file : result.getAllFiles()) {
                    Path fullPath = file.getAbsolutePath();
                    String hash = file.getSha1Hash();

                    // If quick scan (no hash) but we have an existing hash for this file, use the existing hash
                    if ((hash == null || hash.isEmpty()) && existingHashes.containsKey(fullPath)) {
                        hash = existingHashes.get(fullPath);
                        logger.debug("Preserving existing hash for file: {}", file.getFilename());
                    }

                    // Use special key for files without hash (from quick scan with no prior hash)
                    String indexKey = (hash != null && !hash.isEmpty()) ? hash : NO_HASH_KEY;

                    Path directory = file.getAbsolutePath().getParent();
                    String filename = file.getFilename();
                    Instant lastModified = file.getLastModified();

                    fileIndex.computeIfAbsent(indexKey, k -> new ArrayList<>())
                             .add(new FileIndexEntry(directory, filename, lastModified));
                }

                buildDirectoryTree();
                updateFileCount(0); // No files displayed yet until user selects a directory
                updateErrorCount(result.getErrors().size());

                statusLabel.setText(String.format("Startup scan complete: %d files indexed", result.getTotalScanned()));
                logger.info("Startup quick scan finished successfully");
            });
        });

        currentScanTask.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            logger.error("Startup quick scan failed", exception);
            Platform.runLater(() -> {
                // Unbind first
                // REMOVED: progressBar.progressProperty().unbind();

                // REMOVED: progressTitleLabel.setText("Startup Scan Failed");
                stopProgressButton.setDisable(true);

                // Clear callback and task
                scannerService.setFileDiscoveryCallback(null);
                currentScanTask = null;

                statusLabel.setText("Startup scan failed");
            });
        });

        currentScanTask.setOnCancelled(event -> {
            onScanCancelled();
        });

        // Start task in background thread
        Thread thread = new Thread(currentScanTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onExit() {
        logger.info("Application exit requested from menu");

        // Save snapshot before shutting down
        logger.info("Saving snapshot before exit");
        saveSnapshot();
        logger.info("Snapshot save completed");

        // Shutdown executor
        if (thumbnailLoadExecutor != null) {
            thumbnailLoadExecutor.shutdown();
        }

        // Close the window
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Album Organizer");
        alert.setHeaderText("Album Organizer v1.0.0");
        alert.setContentText("A cross-platform desktop application for scanning and organizing image and video files.\n\n" +
                           "VibeCoded by Noam Singer");

        // Apply font scale to dialog
        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        alert.getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));

        alert.showAndWait();
    }

    @FXML
    private void onListView() {
        thumbnailViewActive = false;
        mediaTable.setVisible(true);
        thumbnailScrollPane.setVisible(false);
        mediaTable.setItems(currentDisplayedFiles);
        statusLabel.setText("Switched to List View");
        logger.info("Switched to List View");
    }

    @FXML
    private void onThumbnailView() {
        thumbnailViewActive = true;
        mediaTable.setVisible(false);
        thumbnailScrollPane.setVisible(true);
        loadThumbnails();
        statusLabel.setText("Switched to Thumbnail View");
        logger.info("Switched to Thumbnail View");
    }

    @FXML
    private void onToggleProgressPanel() {
        boolean show = showProgressPanelMenuItem.isSelected();
        progressPanel.setVisible(show);
        progressPanel.setManaged(show);
        logger.info("Scan panel {}", show ? "shown" : "hidden");
    }

    @FXML
    private void onIncreaseFontSize() {
        settings.setFontSizeFactor(settings.getFontSizeFactor() + 1);
        applyFontSize();
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Increased font size");
        logger.info("Increased font size factor to: {}", settings.getFontSizeFactor());
    }

    @FXML
    private void onDecreaseFontSize() {
        settings.setFontSizeFactor(settings.getFontSizeFactor() - 1);
        applyFontSize();
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Decreased font size");
        logger.info("Decreased font size factor to: {}", settings.getFontSizeFactor());
    }

    @FXML
    private void onResetFontSize() {
        settings.setFontSizeFactor(0);
        applyFontSize();
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Reset font size to 100%");
        logger.info("Reset font size factor to 0");
    }

    private void applyFontSize() {
        // Calculate scale: 100% * 2^(factor/4)
        double scale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        int percentage = (int) Math.round(scale * 100);

        // Apply to root pane - this scales the entire UI
        String style = String.format("-fx-font-size: %.2fem;", scale);
        rootPane.setStyle(style);

        logger.debug("Applied font scale: {}% (factor: {}, scale: {})", percentage, settings.getFontSizeFactor(), scale);
    }

    private void refreshCurrentView() {
        if (thumbnailViewActive) {
            loadThumbnails();
        } else {
            mediaTable.setItems(currentDisplayedFiles);
        }
    }

    private void loadThumbnails() {
        thumbnailPane.getChildren().clear();

        if (currentDisplayedFiles.isEmpty()) {
            Label emptyLabel = new Label("No files to display");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");
            thumbnailPane.getChildren().add(emptyLabel);
            return;
        }

        for (MediaFile mediaFile : currentDisplayedFiles) {
            VBox thumbnailBox = createThumbnailCard(mediaFile);
            thumbnailPane.getChildren().add(thumbnailBox);
        }
    }

    private VBox createThumbnailCard(MediaFile mediaFile) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
        card.setPrefSize(180, 220);

        // Placeholder image view
        ImageView imageView = new ImageView();
        imageView.setFitWidth(160);
        imageView.setFitHeight(160);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // Filename label
        Label filenameLabel = new Label(mediaFile.getFilename());
        filenameLabel.setWrapText(true);
        filenameLabel.setMaxWidth(160);

        // Style based on corrupted status
        if (mediaFile.isCorrupted()) {
            filenameLabel.setStyle("-fx-font-size: 11px; -fx-text-alignment: center; -fx-text-fill: darkred;");
        } else {
            filenameLabel.setStyle("-fx-font-size: 11px; -fx-text-alignment: center;");
        }

        card.getChildren().addAll(imageView, filenameLabel);

        // Load thumbnail asynchronously
        loadThumbnailAsync(mediaFile, imageView);

        // Context menu
        ContextMenu contextMenu = new ContextMenu();

        MenuItem openItem = new MenuItem("Open File");
        openItem.setOnAction(e -> openFile(mediaFile));

        MenuItem showInFolderItem = new MenuItem("Show in Folder");
        showInFolderItem.setOnAction(e -> showInFolder(mediaFile));

        MenuItem copyPathItem = new MenuItem("Copy Full Path");
        copyPathItem.setOnAction(e -> copyPathToClipboard(mediaFile));

        MenuItem copyFilenameItem = new MenuItem("Copy Filename");
        copyFilenameItem.setOnAction(e -> copyFilenameToClipboard(mediaFile));

        MenuItem removeOtherDuplicatesItem = new MenuItem("Remove Other Duplicates");
        removeOtherDuplicatesItem.setOnAction(e -> removeOtherDuplicates(mediaFile));

        // Enable/disable "Remove Other Duplicates" based on whether file has duplicates
        contextMenu.setOnShowing(event -> {
            boolean hasDupes = hasDuplicates(mediaFile);
            removeOtherDuplicatesItem.setDisable(!hasDupes);
        });

        contextMenu.getItems().addAll(
            openItem,
            showInFolderItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyFilenameItem,
            new SeparatorMenuItem(),
            removeOtherDuplicatesItem
        );

        // Mouse event handlers
        card.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 2) {
                // Double-click to open
                openFile(mediaFile);
            } else if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                // Right-click to show context menu
                contextMenu.show(card, event.getScreenX(), event.getScreenY());
            }
        });

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0; -fx-border-color: #0078d7; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;"));

        return card;
    }

    private void loadThumbnailAsync(MediaFile mediaFile, ImageView imageView) {
        Task<Image> loadTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                try {
                    String fileUri = mediaFile.getAbsolutePath().toUri().toString();
                    return new Image(fileUri, 160, 160, true, true, true);
                } catch (Exception e) {
                    logger.warn("Failed to load thumbnail for: {}", mediaFile.getFilename(), e);
                    return null;
                }
            }
        };

        loadTask.setOnSucceeded(event -> {
            Image image = loadTask.getValue();
            if (image != null) {
                Platform.runLater(() -> imageView.setImage(image));
            } else {
                // Show placeholder for failed loads
                Platform.runLater(() -> {
                    Label errorLabel = new Label("?");
                    errorLabel.setStyle("-fx-font-size: 48px; -fx-text-fill: #999;");
                    imageView.setImage(null);
                });
            }
        });

        thumbnailLoadExecutor.submit(loadTask);
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Make dialog resizable
        alert.setResizable(true);

        // Apply font scale
        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        alert.getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));
        alert.getDialogPane().setMinWidth(400 * fontScale);

        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Make dialog resizable
        alert.setResizable(true);

        // Apply font scale
        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        alert.getDialogPane().setStyle(String.format("-fx-font-size: %.2fem;", fontScale));
        alert.getDialogPane().setMinWidth(400 * fontScale);

        alert.showAndWait();
    }

    /**
     * Checks if a MediaFile has duplicates (other files with same hash) in the global index.
     */
    private boolean hasDuplicates(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getSha1Hash() == null) {
            return false;
        }

        List<FileIndexEntry> entries = fileIndex.get(mediaFile.getSha1Hash());
        return entries != null && entries.size() > 1;
    }

    /**
     * Removes all other duplicate files (keeps the selected one, moves others to trash).
     */
    private void removeOtherDuplicates(MediaFile keepFile) {
        if (keepFile == null || keepFile.getSha1Hash() == null) {
            return;
        }

        // Find all duplicates from the index
        List<FileIndexEntry> entries = fileIndex.get(keepFile.getSha1Hash());
        if (entries == null || entries.size() <= 1) {
            showWarning("No Duplicates", "This file has no duplicates to remove.");
            return;
        }

        // Get paths of all duplicates except the one to keep
        Path keepPath = keepFile.getAbsolutePath();
        List<Path> duplicatePaths = entries.stream()
            .map(FileIndexEntry::getAbsolutePath)
            .filter(path -> !path.equals(keepPath))
            .collect(Collectors.toList());

        if (duplicatePaths.isEmpty()) {
            showWarning("No Duplicates", "This file has no duplicates to remove.");
            return;
        }

        // Create custom confirmation dialog
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Remove Duplicates");
        confirmAlert.setHeaderText("Move Other duplicates to the recycle-bin");

        // Make dialog resizable
        confirmAlert.setResizable(true);

        // Calculate font scale
        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);

        // Build content with styled text
        VBox content = new VBox(10);
        content.setStyle(String.format("-fx-padding: 10; -fx-font-size: %.2fem;", fontScale));

        // Label explaining the action
        Label explanation = new Label("The following file will be KEPT:");
        explanation.setStyle("-fx-font-weight: bold;");
        content.getChildren().add(explanation);

        // Keep file (dark green and bold)
        Label keepLabel = new Label("✓ " + keepFile.getAbsolutePath().toString());
        keepLabel.setStyle("-fx-text-fill: #006400; -fx-font-weight: bold; -fx-padding: 5;");
        keepLabel.setWrapText(true);
        keepLabel.setMaxWidth(500 * fontScale);
        content.getChildren().add(keepLabel);

        // Separator
        content.getChildren().add(new Separator());

        // Label for files to delete
        Label deleteLabel = new Label("Select files to MOVE TO TRASH (uncheck to keep):");
        deleteLabel.setStyle("-fx-font-weight: bold;");
        content.getChildren().add(deleteLabel);

        // List of checkboxes for files to delete
        VBox deleteList = new VBox(5);
        java.util.Map<Path, CheckBox> checkBoxMap = new java.util.LinkedHashMap<>();

        for (Path duplicatePath : duplicatePaths) {
            CheckBox checkBox = new CheckBox(duplicatePath.toString());
            checkBox.setSelected(true); // Default: all checked
            checkBox.setStyle("-fx-text-fill: #8B0000; -fx-padding: 5;");
            checkBox.setWrapText(true);
            checkBox.setMaxWidth(500 * fontScale);
            checkBoxMap.put(duplicatePath, checkBox);
            deleteList.getChildren().add(checkBox);
        }

        // Wrap in ScrollPane if many files
        if (duplicatePaths.size() > 10) {
            ScrollPane scrollPane = new ScrollPane(deleteList);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(300 * fontScale);
            scrollPane.setStyle("-fx-background: white; -fx-border-color: #cccccc;");
            content.getChildren().add(scrollPane);
        } else {
            content.getChildren().add(deleteList);
        }

        confirmAlert.getDialogPane().setContent(content);

        // Set minimum size for dialog based on font scale
        confirmAlert.getDialogPane().setMinWidth(600 * fontScale);
        confirmAlert.getDialogPane().setPrefWidth(600 * fontScale);

        // Custom buttons: Cancel (default) and Delete
        ButtonType deleteButtonType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmAlert.getButtonTypes().setAll(cancelButtonType, deleteButtonType);

        // Set Cancel as default button
        Button cancelButton = (Button) confirmAlert.getDialogPane().lookupButton(cancelButtonType);
        cancelButton.setDefaultButton(true);
        Button deleteButton = (Button) confirmAlert.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.setDefaultButton(false);

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != deleteButtonType) {
            return;
        }

        // Get only the checked files to delete
        List<Path> filesToDelete = checkBoxMap.entrySet().stream()
            .filter(entry -> entry.getValue().isSelected())
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());

        if (filesToDelete.isEmpty()) {
            showWarning("No Files Selected", "No files were selected for deletion.");
            return;
        }

        // Move selected duplicates to trash
        int successCount = 0;
        int failCount = 0;
        List<String> failedFiles = new ArrayList<>();
        String hash = keepFile.getSha1Hash();

        for (Path duplicatePath : filesToDelete) {
            boolean success = FileTrashUtil.moveToTrash(duplicatePath);
            if (success) {
                successCount++;
                // Remove from fileIndex
                List<FileIndexEntry> indexEntries = fileIndex.get(hash);
                if (indexEntries != null) {
                    indexEntries.removeIf(entry -> entry.getAbsolutePath().equals(duplicatePath));
                    if (indexEntries.isEmpty()) {
                        fileIndex.remove(hash);
                    }
                }
                // Remove from currentDisplayedFiles if present
                currentDisplayedFiles.removeIf(file -> file.getAbsolutePath().equals(duplicatePath));
            } else {
                failCount++;
                failedFiles.add(duplicatePath.getFileName().toString());
            }
        }

        // Update UI
        refreshCurrentView();
        updateFileCount(currentDisplayedFiles.size());
        highlightDuplicates();
        buildDirectoryTree();

        // Show result
        if (failCount == 0) {
            statusLabel.setText("Removed " + successCount + " duplicate file(s) to trash");
            logger.info("Successfully removed {} duplicates of: {}", successCount, keepFile.getFilename());
        } else {
            String message = String.format(
                "Removed %d file(s) to trash.\n\nFailed to remove %d file(s):\n%s",
                successCount,
                failCount,
                String.join("\n", failedFiles.stream().limit(10).collect(Collectors.toList()))
            );
            showWarning("Partial Success", message);
            logger.warn("Removed {} duplicates, failed to remove {}", successCount, failCount);
        }
    }

    /**
     * Organizes a single file to the target folder according to settings.
     */
    private void organizeFile(MediaFile mediaFile) {
        if (mediaFile == null) {
            return;
        }

        if (settings.getTargetFolder() == null) {
            showWarning("No Target Folder", "Please set a target folder in Settings before organizing files.");
            return;
        }

        // Directly perform organize without confirmation
        performOrganize(mediaFile);
    }

    /**
     * Performs the actual organize operation.
     */
    private void performOrganize(MediaFile mediaFile) {
        OrganizeReportWriter reportWriter = null;
        try {
            // Create report writer
            try {
                reportWriter = new OrganizeReportWriter(mediaFile.getAbsolutePath().getParent());
                reportWriter.logDirectoryStart(mediaFile.getAbsolutePath().getParent());
            } catch (IOException e) {
                logger.error("Failed to create report writer for single file organize", e);
            }

            FileOrganizeService organizeService = new FileOrganizeService();
            FileOrganizeService.OrganizeResult result = organizeService.organizeFile(mediaFile, settings);
            Path sourcePath = mediaFile.getAbsolutePath();

            if (result.isSuccess()) {
                statusLabel.setText("File organized successfully to: " + result.getTargetPath().getFileName());
                logger.info("Organized file {} to {}", mediaFile.getFilename(), result.getTargetPath());

                if (reportWriter != null) {
                    reportWriter.logSuccess(mediaFile, sourcePath, result.getTargetPath());
                    reportWriter.writeSummary(1, 1, 0, 0);
                    reportWriter.close();
                    lastOrganizeReportFile = reportWriter.getReportFile();
                    viewReportButton.setDisable(false);
                    logger.info("Single file organize report created: {}", lastOrganizeReportFile);
                }

                // If moved, remove from current view
                if (settings.getMode() == AlbumOrganizerSettings.OrganizeMode.MOVE) {
                    currentDisplayedFiles.remove(mediaFile);
                    mediaTable.refresh();
                    updateFileCount(currentDisplayedFiles.size());
                }

                // Rescan the target folder to update the file index and show new file
                Path targetFolder = settings.getTargetFolder();
                if (targetFolder != null && baseFolders.contains(targetFolder)) {
                    logger.info("Rescanning target folder to update index: {}", targetFolder);
                    fullScanWithHashFolder(targetFolder);
                } else if (targetFolder != null) {
                    logger.info("Target folder is not in base folders, adding it: {}", targetFolder);
                    // Target folder is not an album folder - add it temporarily for scanning
                    baseFolders.add(targetFolder);
                    fullScanWithHashFolder(targetFolder);
                }

                // Don't show info dialog - just status message
            } else if (result.isSkipped()) {
                statusLabel.setText("File already exists at target location");
                logger.info("Skipped organizing file {}: {}", mediaFile.getFilename(), result.getErrorMessage());

                if (reportWriter != null) {
                    reportWriter.logSkipped(mediaFile, sourcePath, result.getErrorMessage());
                    reportWriter.writeSummary(1, 0, 1, 0);
                    reportWriter.close();
                    lastOrganizeReportFile = reportWriter.getReportFile();
                    viewReportButton.setDisable(false);
                }
            } else {
                statusLabel.setText("Failed to organize file");
                logger.error("Failed to organize file {}: {}", mediaFile.getFilename(), result.getErrorMessage());
                showError("Error", "Failed to organize file:\n" + result.getErrorMessage());

                if (reportWriter != null) {
                    reportWriter.logFailure(mediaFile, sourcePath, result.getErrorMessage());
                    reportWriter.writeSummary(1, 0, 0, 1);
                    reportWriter.close();
                    lastOrganizeReportFile = reportWriter.getReportFile();
                    viewReportButton.setDisable(false);
                }
            }
        } catch (Exception e) {
            logger.error("Error organizing file: {}", mediaFile.getFilename(), e);
            showError("Error", "Error organizing file:\n" + e.getMessage());

            if (reportWriter != null) {
                try {
                    reportWriter.logFailure(mediaFile, mediaFile.getAbsolutePath(), e.getMessage());
                    reportWriter.writeSummary(1, 0, 0, 1);
                    reportWriter.close();
                    lastOrganizeReportFile = reportWriter.getReportFile();
                    viewReportButton.setDisable(false);
                } catch (IOException ex) {
                    logger.error("Failed to write error to report", ex);
                }
            }
        }
    }

    /**
     * Organizes all files recursively in a folder.
     */
    private void organizeFolderRecursively(DirectoryNode folderNode) {
        if (folderNode == null || folderNode.getPath() == null) {
            return;
        }

        if (settings.getTargetFolder() == null) {
            showWarning("No Target Folder", "Please set a target folder in Settings before organizing files.");
            return;
        }

        int fileCount = folderNode.getRecursiveFileCount();
        if (fileCount == 0) {
            showWarning("No Files", "This folder contains no media files to organize.");
            return;
        }

        // Ask for confirmation
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Organize Folder Recursively");
        confirmDialog.setHeaderText("About to organize " + fileCount + " file(s) recursively");
        confirmDialog.setContentText("All media files in '" + folderNode.getDisplayName() +
            "' and its subdirectories will be organized to the target folder.\n\nContinue?");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        // Start the organize task
        startOrganizeRecursively(folderNode.getPath(), fileCount);
    }

    /**
     * Starts the recursive organize operation in a background task.
     */
    private void startOrganizeRecursively(Path rootPath, int estimatedFileCount) {
        if ((currentScanTask != null && currentScanTask.isRunning()) ||
            (currentOrganizeTask != null && currentOrganizeTask.isRunning())) {
            logger.warn("Cannot start organize - a scan or organize is already in progress");
            showWarning("Operation In Progress", "Please wait for the current operation to complete.");
            return;
        }

        logger.info("Starting recursive organize for: {}", rootPath);

        // Create organize task
        Task<OrganizeRecursiveResult> organizeTask = new Task<>() {
            private OrganizeReportWriter reportWriter;

            @Override
            protected OrganizeRecursiveResult call() throws Exception {
                int processed = 0;
                int succeeded = 0;
                int skipped = 0;
                int failed = 0;
                List<String> errors = new ArrayList<>();

                // Create report writer
                try {
                    reportWriter = new OrganizeReportWriter(rootPath);
                } catch (IOException e) {
                    logger.error("Failed to create report writer", e);
                    return new OrganizeRecursiveResult(0, 0, 0, 0,
                        List.of("Failed to create report: " + e.getMessage()));
                }

                // Collect files to organize from fileIndex (no disk walk needed)
                List<MediaFile> filesToOrganize = new ArrayList<>();
                try {
                    updateMessage("Collecting files from index...");

                    // Extract all unique directories under rootPath from the fileIndex
                    Set<Path> indexedDirs = fileIndex.values().stream()
                        .flatMap(List::stream)
                        .map(FileIndexEntry::getDirectory)
                        .filter(dir -> dir.startsWith(rootPath))
                        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

                    for (Path dir : indexedDirs) {
                        if (isCancelled()) {
                            reportWriter.close();
                            return new OrganizeRecursiveResult(0, 0, 0, 0, errors);
                        }

                        reportWriter.logDirectoryStart(dir);

                        // Build hash lookup for this directory from fileIndex
                        Map<Path, String> dirKnownHashes = new HashMap<>();
                        fileIndex.forEach((hash, entries) -> {
                            if (!hash.equals(NO_HASH_KEY)) {
                                for (FileIndexEntry entry : entries) {
                                    if (entry.getDirectory().equals(dir)) {
                                        dirKnownHashes.put(entry.getAbsolutePath(), hash);
                                    }
                                }
                            }
                        });

                        List<MediaFile> filesInDir = directoryScanService.scanDirectory(dir, dirKnownHashes);
                        filesToOrganize.addAll(filesInDir);
                    }
                } catch (Exception e) {
                    logger.error("Error collecting files from index", e);
                    if (reportWriter != null) {
                        reportWriter.close();
                    }
                    return new OrganizeRecursiveResult(0, 0, 0, 0,
                        List.of("Failed to collect files: " + e.getMessage()));
                }

                int totalFiles = filesToOrganize.size();
                updateMessage("Found " + totalFiles + " file(s) to organize");

                // Organize each file
                FileOrganizeService organizeService = new FileOrganizeService();
                for (MediaFile mediaFile : filesToOrganize) {
                    if (isCancelled()) {
                        updateMessage("Organize cancelled by user");
                        reportWriter.writeSummary(processed, succeeded, skipped, failed);
                        reportWriter.close();
                        break;
                    }

                    processed++;
                    updateProgress(processed, totalFiles);
                    updateMessage("Organizing (" + processed + "/" + totalFiles + "): " + mediaFile.getFilename());

                    Path sourcePath = mediaFile.getAbsolutePath();

                    try {
                        FileOrganizeService.OrganizeResult result = organizeService.organizeFile(mediaFile, settings);
                        String dir = sourcePath.getParent().toString()
                            .replace(System.getProperty("user.home"), "~");

                        if (result.isSuccess()) {
                            succeeded++;
                            logProgress("Moved: " + mediaFile.getFilename() + " ; at " + dir);
                            reportWriter.logSuccess(mediaFile, sourcePath, result.getTargetPath());
                        } else if (result.isSkipped()) {
                            skipped++;
                            logProgress("Skipped: " + mediaFile.getFilename() + " ; at " + dir + " — " + result.getErrorMessage());
                            reportWriter.logSkipped(mediaFile, sourcePath, result.getErrorMessage());
                        } else {
                            failed++;
                            String error = mediaFile.getFilename() + ": " + result.getErrorMessage();
                            logProgress("Failed: " + mediaFile.getFilename() + " ; at " + dir + " — " + result.getErrorMessage());
                            errors.add(error);
                            reportWriter.logFailure(mediaFile, sourcePath, result.getErrorMessage());
                        }
                    } catch (Exception e) {
                        failed++;
                        String error = mediaFile.getFilename() + ": " + e.getMessage();
                        String dir = sourcePath.getParent().toString()
                            .replace(System.getProperty("user.home"), "~");
                        logProgress("Error: " + mediaFile.getFilename() + " ; at " + dir + " — " + e.getMessage());
                        errors.add(error);
                        reportWriter.logFailure(mediaFile, sourcePath, e.getMessage());
                        logger.error("Error organizing file: {}", mediaFile.getFilename(), e);
                    }
                }

                // Write summary and close report
                reportWriter.writeSummary(processed, succeeded, skipped, failed);
                reportWriter.close();

                // Store report path for viewing
                lastOrganizeReportFile = reportWriter.getReportFile();

                return new OrganizeRecursiveResult(processed, succeeded, skipped, failed, errors);
            }
        };

        // Track as current organize task
        currentOrganizeTask = organizeTask;

        // Update status bar at start
        statusLabel.setText("Starting organization of files...");

        // Show scan panel for progress, with progress bar visible
        showProgressPanel(false);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.progressProperty().bind(organizeTask.progressProperty());;

        // Handle completion
        organizeTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                OrganizeRecursiveResult result = organizeTask.getValue();
                progressBar.progressProperty().unbind();
                progressBar.setVisible(false);
                progressBar.setManaged(false);

                String summary = String.format("Organize Complete: %d processed, %d succeeded, %d skipped, %d failed",
                    result.processed, result.succeeded, result.skipped, result.failed);

                statusLabel.setText(summary);
                logger.info(summary);

                // Enable View Report button
                viewReportButton.setDisable(false);
                logger.info("View Report button enabled, report file: {}", lastOrganizeReportFile);

                // Preserve this summary for after the automatic rescan
                preservedStatusMessage = summary;

                if (!result.errors.isEmpty()) {
                    // Show errors
                    StringBuilder errorMsg = new StringBuilder("Errors occurred during organize:\n\n");
                    result.errors.stream().limit(10).forEach(err -> errorMsg.append(err).append("\n"));
                    if (result.errors.size() > 10) {
                        errorMsg.append("\n... and ").append(result.errors.size() - 10).append(" more errors");
                    }
                    showError("Organize Errors", errorMsg.toString());
                }

                // Rescan target folder to update tree
                Path targetFolder = settings.getTargetFolder();
                if (targetFolder != null && baseFolders.contains(targetFolder)) {
                    fullScanWithHashFolder(targetFolder);
                } else {
                    // No automatic rescan, clear preserved message
                    preservedStatusMessage = null;
                }

                currentOrganizeTask = null;
            });
        });

        organizeTask.setOnFailed(event -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            statusLabel.setText("Organize failed");

            Throwable exception = organizeTask.getException();
            logger.error("Organize task failed", exception);
            showError("Error", "Organize failed:\n" + exception.getMessage());

            currentOrganizeTask = null;
        });

        organizeTask.setOnCancelled(event -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            statusLabel.setText("Organize cancelled");
            currentOrganizeTask = null;
        });

        // Start task in background
        Thread thread = new Thread(organizeTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Result holder for recursive organize operation.
     */
    private static class OrganizeRecursiveResult {
        final int processed;
        final int succeeded;
        final int skipped;
        final int failed;
        final List<String> errors;

        OrganizeRecursiveResult(int processed, int succeeded, int skipped, int failed, List<String> errors) {
            this.processed = processed;
            this.succeeded = succeeded;
            this.skipped = skipped;
            this.failed = failed;
            this.errors = errors;
        }
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
