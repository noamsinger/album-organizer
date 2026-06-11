package com.albumorganizer.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.albumorganizer.model.DirectoryNode;
import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.MediaType;
import com.albumorganizer.model.FileIndexEntry;
import javafx.collections.FXCollections;
import com.albumorganizer.model.AlbumOrganizerSettings;
import com.albumorganizer.model.ScanResult;
import com.albumorganizer.repository.ConfigRepository;
import com.albumorganizer.repository.RecycleBinRepository;
import com.albumorganizer.repository.SnapshotRepository;
import com.albumorganizer.service.ScannerService;
import com.albumorganizer.service.DirectoryScanService;
import com.albumorganizer.service.FileOrganizeService;
import com.albumorganizer.service.OrganizeReportWriter;
import com.albumorganizer.service.ThumbnailService;
import com.albumorganizer.service.ArchiveScanService;
import com.albumorganizer.service.enhancement.EnhancementConfig;
import com.albumorganizer.service.enhancement.EnhancementProvider;
import com.albumorganizer.service.enhancement.EnhancementRequest;
import com.albumorganizer.service.enhancement.EnhancementResult;
import com.albumorganizer.service.enhancement.ImageEnhancementService;
import com.albumorganizer.service.enhancement.NamedPrompt;
import com.albumorganizer.task.ScanTask;
import com.albumorganizer.controller.EnhancementDialog;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main controller for the application window.
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private static final String NO_HASH_KEY = "__NO_HASH__"; // Special key for files without hashes (quick scan)
    // Sentinel Image returned by loadThumbnailAsync to signal an audio-only file (no video stream)
    private static final javafx.scene.image.WritableImage AUDIO_ONLY_SENTINEL =
            new javafx.scene.image.WritableImage(1, 1);

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
    @FXML private CheckMenuItem showArchivesMenuItem;
    @FXML private RadioMenuItem logLevelDebugMenuItem;
    @FXML private RadioMenuItem logLevelInfoMenuItem;
    @FXML private RadioMenuItem logLevelWarnMenuItem;
    @FXML private RadioMenuItem sortByNameMenuItem;
    @FXML private RadioMenuItem sortByDateTakenMenuItem;
    @FXML private RadioMenuItem sortByDateMenuItem;
    @FXML private RadioMenuItem sortByTypeMenuItem;
    @FXML private RadioMenuItem sortByDurationMenuItem;
    @FXML private RadioMenuItem sortByResolutionMenuItem;
    @FXML private RadioMenuItem sortAscendingMenuItem;
    @FXML private RadioMenuItem sortDescendingMenuItem;
    @FXML private MenuItem toggleFullScreenMenuItem;
    @FXML private Button viewReportButton;
    @FXML private Button stopProgressButton;
    @FXML private Button hideProgressButton;
    @FXML private MenuItem menuEnhanceFromClipboard;

    private final ScannerService scannerService;
    private final DirectoryScanService directoryScanService;
    private final ConfigRepository configRepository;
    private final SnapshotRepository snapshotRepository;
    private final ThumbnailService thumbnailService;
    private final ArchiveScanService archiveScanService;
    private ImageEnhancementService imageEnhancementService;
    private final Map<String, List<FileIndexEntry>> fileIndex; // Hash -> List of (directory, filename) pairs
    private List<Path> baseFolders;
    private List<Path> currentlyScannedFolders; // Track which folders are being scanned
    private ScanTask currentScanTask;
    private Task<?> currentOrganizeTask; // Track organize task for stop button
    private String preservedStatusMessage = null; // Preserve status message across automatic rescans
    private byte[] lastOrganizeReportCompressed = null;
    private String lastOrganizeReportTimestamp = null;
    private boolean thumbnailViewActive = false;
    private ObservableList<MediaFile> currentDisplayedFiles;
    private ExecutorService thumbnailLoadExecutor;
    private AlbumOrganizerSettings settings; // All settings including targetFolder and fontSizeFactor
    private Path currentSelectedDirectory; // Currently selected directory for on-demand scanning
    private com.albumorganizer.service.enhancement.EnhancementProvider lastUsedProvider = null;
    private MediaFile selectedThumbnailFile = null;
    private String lastUsedPromptTitle = null; // kept for back-compat, unused
    private String lastAdditionalPrompt = "";
    private List<String> lastCheckedPromptTitles = new ArrayList<>();
    private final RecycleBinRepository recycleBinRepository = new RecycleBinRepository();

    public MainController() {
        this.scannerService = new ScannerService();
        this.directoryScanService = new DirectoryScanService();
        this.configRepository = new ConfigRepository();
        this.snapshotRepository = new SnapshotRepository();
        this.thumbnailService = new ThumbnailService();
        this.archiveScanService = new ArchiveScanService();
        this.imageEnhancementService = new ImageEnhancementService(configRepository);
        this.fileIndex = new HashMap<>();
        this.baseFolders = new ArrayList<>();
        this.currentlyScannedFolders = new ArrayList<>();
        this.currentDisplayedFiles = FXCollections.observableArrayList();
        this.thumbnailLoadExecutor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    @FXML
    public void initialize() {
        logger.debug("Initializing MainController");

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
        applyDefaultSpecialFolders();
        applyFontSize();

        // Load saved folders (needs settings to be initialized)
        loadBaseFolders();

        // Load snapshot from compressed cache
        loadSnapshot();

        // Build initial tree with album folders (even if not scanned)
        buildDirectoryTree();

        // Initialize with empty displayed files (will scan on selection)
        currentDisplayedFiles.clear();

        // Run quick scan on startup for all album and special folders
        if (!baseFolders.isEmpty()) {
            logger.info("Running startup quick scan on {} album folders", baseFolders.size());
            Platform.runLater(() -> startStartupQuickScan());
        } else {
            Platform.runLater(() -> showFirstRunDialog());
        }

        // Restore view mode from settings
        if (settings.isThumbnailView()) {
            thumbnailViewActive = true;
            mediaTable.setVisible(false);
            thumbnailScrollPane.setVisible(true);
            thumbnailViewMenuItem.setSelected(true);
        }

        // Restore show-archives setting
        showArchivesMenuItem.setSelected(settings.isShowArchivesInTree());

        syncLogLevelMenu();

        logger.debug("MainController initialized");
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
        resolutionColumn.setComparator((a, b) -> {
            long pixelsA = parsePixelCount(a);
            long pixelsB = parsePixelCount(b);
            return Long.compare(pixelsA, pixelsB);
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
                            logger.debug("Styling CORRUPTED: {}", item.getFilename());
                        }
                        // Duplicate files - add CSS class only
                        else if (isDuplicate) {
                            getStyleClass().add("duplicate");
                        }
                        // Normal files have no special class
                    }

                    // Tooltip: show original path for files in the recycle bin
                    if (!empty && item != null) {
                        Path origPath = recycleBinRepository.getOriginalPath(item.getAbsolutePath());
                        if (origPath != null) {
                            setTooltip(new Tooltip("Original: " + origPath));
                        } else {
                            setTooltip(null);
                        }
                    } else {
                        setTooltip(null);
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

        MenuItem copyImageItem = new MenuItem("Copy Image");
        copyImageItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyImageToClipboard(selected);
            }
        });

        MenuItem removeOtherDuplicatesItem = new MenuItem("Remove Other Duplicates");
        removeOtherDuplicatesItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                removeOtherDuplicates(selected);
            }
        });

        MenuItem deleteFileItem = new MenuItem("Delete File");
        deleteFileItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteFile(selected);
            }
        });

        MenuItem restoreFromBinItem = new MenuItem("Restore to Original Location");
        restoreFromBinItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                restoreFromBin(selected);
            }
        });

        MenuItem organizeFileItem = new MenuItem("Organize File");
        organizeFileItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                organizeFile(selected);
            }
        });

        MenuItem enhanceWithAiItem = new MenuItem("Enhance with AI...");
        enhanceWithAiItem.setOnAction(e -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openEnhancementDialog(selected);
            }
        });

        // Enable/disable menu items based on context
        contextMenu.setOnShowing(event -> {
            MediaFile selected = mediaTable.getSelectionModel().getSelectedItem();
            boolean hasDuplicates = selected != null && hasDuplicates(selected);
            removeOtherDuplicatesItem.setDisable(!hasDuplicates);

            boolean hasBin = settings != null && settings.getRecycleBinFolder() != null;
            boolean isInBin = selected != null && hasBin
                && selected.getAbsolutePath().startsWith(settings.getRecycleBinFolder());
            deleteFileItem.setDisable(selected == null || !hasBin || isInBin);

            restoreFromBinItem.setVisible(isInBin);
            restoreFromBinItem.setDisable(selected == null || !recycleBinRepository.isInBin(selected.getAbsolutePath()));

            boolean hasTargetFolder = settings != null && settings.getTargetFolder() != null;
            boolean isInTarget = selected != null && hasTargetFolder
                && selected.getAbsolutePath().startsWith(settings.getTargetFolder());
            organizeFileItem.setDisable(!hasTargetFolder || isInTarget);

            boolean hasProviders = !imageEnhancementService.getConfiguredProviders().isEmpty();
            boolean selectedIsImage = selected != null && selected.getType() == MediaType.IMAGE;
            boolean selectedIsVideo = selected != null && selected.getType() == MediaType.VIDEO;
            boolean hasVideoProviders = imageEnhancementService.getConfiguredProviders().stream()
                .anyMatch(com.albumorganizer.service.enhancement.EnhancementProvider::supportsVideo);
            enhanceWithAiItem.setDisable(selected == null || !hasProviders
                || (!selectedIsImage && !(selectedIsVideo && hasVideoProviders)));
            copyImageItem.setDisable(selected == null || selected.getType() != MediaType.IMAGE);
        });

        contextMenu.getItems().addAll(
            openItem,
            showInFolderItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyFilenameItem,
            copyImageItem,
            new SeparatorMenuItem(),
            removeOtherDuplicatesItem,
            deleteFileItem,
            restoreFromBinItem,
            new SeparatorMenuItem(),
            organizeFileItem,
            new SeparatorMenuItem(),
            enhanceWithAiItem
        );

        mediaTable.setContextMenu(contextMenu);
    }

    private void setupTreeContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem scanArchiveItem = new MenuItem("Scan Archive");
        scanArchiveItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().isArchiveNode()) {
                scanArchiveNode(selected);
            }
        });

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

        MenuItem clearRecycleBinItem = new MenuItem("Unset as Recycle-Bin Folder");
        clearRecycleBinItem.setOnAction(e -> {
            settings.setRecycleBinFolder(null);
            configRepository.setOrganizeSettings(settings);
            buildDirectoryTree();
            statusLabel.setText("Recycle-bin folder cleared");
        });

        MenuItem organizeFolderRecursivelyItem = new MenuItem("Organize Folder Recursively");
        organizeFolderRecursivelyItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().getPath() != null) {
                organizeFolderRecursively(selected.getValue());
            }
        });

        MenuItem removeDuplicatesInFolderItem = new MenuItem("Remove Other Duplicates");
        removeDuplicatesInFolderItem.setOnAction(e -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue().getPath() != null) {
                removeOtherDuplicatesInSubtree(selected.getValue().getPath());
            }
        });

        // Enable/disable menu items based on selection
        contextMenu.setOnShowing(event -> {
            TreeItem<DirectoryNode> selected = directoryTree.getSelectionModel().getSelectedItem();
            boolean isAlbum = selected != null && selected.getValue().isAlbum();
            boolean hasPath = selected != null && selected.getValue().getPath() != null;
            boolean isCurrentTarget = selected != null && selected.getValue().getPath() != null
                && selected.getValue().getPath().equals(settings.getTargetFolder());
            boolean isRecycleBin = selected != null && selected.getValue().isRecycleBin();
            boolean canOrganizeRecursively = selected != null && selected.getValue().getRecursiveFileCount() > 0
                && settings.getTargetFolder() != null
                && hasPath && !selected.getValue().getPath().startsWith(settings.getTargetFolder());
            boolean isArchive = selected != null && selected.getValue().isArchiveNode();

            // "Remove Other Duplicates": need bin configured and at least one external duplicate in subtree
            boolean hasBin = settings.getRecycleBinFolder() != null;
            boolean hasExternalDuplicates = hasPath && !isRecycleBin && !isArchive
                && subtreeHasExternalDuplicates(selected.getValue().getPath());
            removeDuplicatesInFolderItem.setDisable(!hasBin || !hasExternalDuplicates);

            scanArchiveItem.setVisible(isArchive);
            fullScanWithHashItem.setDisable(!hasPath || isArchive);
            quickScanItem.setDisable(!hasPath || isArchive);
            removeAlbumItem.setDisable(!isAlbum || isRecycleBin);
            makeTargetItem.setDisable(!isAlbum || isCurrentTarget || isRecycleBin);
            clearTargetItem.setDisable(!isCurrentTarget);
            clearRecycleBinItem.setVisible(isRecycleBin);
            organizeFolderRecursivelyItem.setDisable(!canOrganizeRecursively);
        });

        Menu doMagicMenu = new Menu("Do Magic");
        doMagicMenu.getItems().addAll(organizeFolderRecursivelyItem, removeDuplicatesInFolderItem);

        contextMenu.getItems().addAll(
            scanArchiveItem,
            new SeparatorMenuItem(),
            fullScanWithHashItem,
            quickScanItem,
            new SeparatorMenuItem(),
            openInBrowserItem,
            new SeparatorMenuItem(),
            doMagicMenu,
            new SeparatorMenuItem(),
            makeTargetItem,
            clearTargetItem,
            clearRecycleBinItem,
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
        // Tree selection listener - triggers on-demand scan of selected directory or archive
        directoryTree.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue == null || newValue.getValue() == null) return;
                DirectoryNode val = newValue.getValue();
                if (val.isArchiveNode()) {
                    scanArchiveNode(newValue);
                } else if (val.getPath() != null) {
                    onDirectorySelected(val.getPath());
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
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    setText(item.toString());
                    // Archive icon
                    if (item.isArchiveNode()) {
                        Label icon = new Label("🗄"); // 🗄 file-cabinet emoji as archive icon
                        icon.setStyle("-fx-font-size: 0.85em;");
                        setGraphic(icon);
                    } else {
                        setGraphic(null);
                    }
                    // Tooltip with tilde-shortened full path
                    if (item.getArchivePath() != null) {
                        String fullPath = item.getArchivePath().toString()
                            .replace(System.getProperty("user.home"), "~");
                        setTooltip(new Tooltip(fullPath + " [" + item.getArchiveType() + "]"));
                    } else if (item.getPath() != null) {
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
                    // AI-Generated folder: blue and bold
                    else if (item.isAiGenerated()) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
                    }
                    // Recycle-bin folder: dark red and bold
                    else if (item.isRecycleBin()) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: darkred;");
                    }
                    // Archive node: italic
                    else if (item.isArchiveNode()) {
                        setStyle("-fx-font-style: italic;");
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
        settings.setLastSelectedFolder(directory.toString());
        configRepository.setOrganizeSettings(settings);
        logger.debug("Directory selected: {}", directory);

        // Show loading indicator
        int estimatedCount = directoryScanService.getMediaFileCount(directory);
        statusLabel.setText(String.format("Loading %d files...", estimatedCount));

        // Show progress bar if no background scan is running
        boolean showProgressBar = (currentScanTask == null || !currentScanTask.isRunning());
        if (showProgressBar) {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            progressBar.setVisible(true);
            progressBar.setManaged(true);
        }

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
        final int totalFiles = estimatedCount;
        final boolean usingProgressBar = showProgressBar;
        Task<List<MediaFile>> scanTask = new Task<>() {
            @Override
            protected List<MediaFile> call() {
                return directoryScanService.scanDirectory(directory, knownHashes, (processed, total) -> {
                    int actualTotal = total > 0 ? total : totalFiles;
                    Platform.runLater(() -> {
                        if (usingProgressBar) {
                            progressBar.setProgress(processed / (double) actualTotal);
                        }
                        statusLabel.setText(String.format("Reading files... %d/%d", processed, actualTotal));
                    });
                });
            }
        };

        scanTask.setOnSucceeded(event -> {
            List<MediaFile> files = scanTask.getValue();
            currentDisplayedFiles.clear();
            currentDisplayedFiles.addAll(files);

            // Log corrupted files
            long corruptedCount = files.stream().filter(MediaFile::isCorrupted).count();
            if (corruptedCount > 0) {
                logger.debug("Loaded {} corrupted files from directory", corruptedCount);
                files.stream().filter(MediaFile::isCorrupted).limit(3).forEach(f ->
                    logger.debug("Corrupted file in view: {} - isCorrupted={}", f.getFilename(), f.isCorrupted())
                );
            }

            updateFileCount(files.size());
            highlightDuplicates();
            // Re-apply current table sort order; fall back to name ascending if none set
            if (mediaTable.getSortOrder().isEmpty()) {
                filenameColumn.setSortType(TableColumn.SortType.ASCENDING);
                mediaTable.getSortOrder().add(filenameColumn);
            }
            mediaTable.sort();

            if (usingProgressBar) {
                progressBar.setProgress(0);
                statusLabel.setText(String.format("Loading thumbnails... 0/%d", files.size()));
            }

            refreshCurrentView();

            // If not in thumbnail mode or no files, hide progress bar now
            if (!thumbnailViewActive || files.isEmpty()) {
                if (usingProgressBar) {
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                }
                statusLabel.setText(String.format("Loaded %d files", files.size()));
            }
            logger.debug("Loaded {} files from: {}", files.size(), directory);
        });

        scanTask.setOnFailed(event -> {
            if (usingProgressBar) {
                progressBar.setVisible(false);
                progressBar.setManaged(false);
            }
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
            if (!Desktop.isDesktopSupported()) {
                showError("Not Supported", "Opening files is not supported on this platform");
                return;
            }

            // Archive entry: extract to a temp file first
            if (com.albumorganizer.service.ArchiveScanService.isArchiveEntry(mediaFile.getAbsolutePath())) {
                String[] parts = com.albumorganizer.service.ArchiveScanService
                    .splitArchivePath(mediaFile.getAbsolutePath());
                if (parts == null) {
                    showError("Error", "Could not parse archive path");
                    return;
                }
                byte[] bytes = archiveScanService.extractEntry(
                    java.nio.file.Paths.get(parts[0]), parts[1]);
                if (bytes == null) {
                    showError("Not Found", "Entry not found in archive: " + parts[1]);
                    return;
                }
                String suffix = mediaFile.getFilename().contains(".")
                    ? mediaFile.getFilename().substring(mediaFile.getFilename().lastIndexOf('.'))
                    : "";
                Path tempFile = Files.createTempFile("album_arch_", suffix);
                Files.write(tempFile, bytes);
                tempFile.toFile().deleteOnExit();
                openWithPreviewIfImage(tempFile.toFile(), mediaFile.getType());
                logger.debug("Opened archive entry via temp file: {}", tempFile);
                return;
            }

            File file = mediaFile.getAbsolutePath().toFile();
            if (file.exists()) {
                openWithPreviewIfImage(file, mediaFile.getType());
                logger.debug("Opened file: {}", file);
            } else {
                showError("File Not Found", "File does not exist: " + file);
            }
        } catch (IOException e) {
            logger.error("Failed to open file: {}", mediaFile.getAbsolutePath(), e);
            showError("Error Opening File", "Failed to open file: " + e.getMessage());
        }
    }

    private void openWithPreviewIfImage(File file, MediaType type) throws IOException {
        Desktop.getDesktop().open(file);
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
                    logger.debug("Opened folder: {}", folder);
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

    private void copyImageToClipboard(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getAbsolutePath() == null) {
            return;
        }
        try {
            javafx.scene.image.Image image = new javafx.scene.image.Image(
                mediaFile.getAbsolutePath().toUri().toString());
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putImage(image);
            clipboard.setContent(content);
            logger.debug("Copied image to clipboard: {}", mediaFile.getAbsolutePath());
            statusLabel.setText("Image copied to clipboard");
        } catch (Exception e) {
            logger.error("Failed to copy image to clipboard: {}", mediaFile.getAbsolutePath(), e);
            statusLabel.setText("Failed to copy image to clipboard");
        }
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
                logger.debug("Opened in file browser: {}", path);
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
        SettingsDialog dialog = new SettingsDialog(settings, configRepository.getEnhancementConfig(), fontScale);
        dialog.initOwner(rootPane.getScene().getWindow());

        dialog.showAndWait().ifPresent(result -> {
            AlbumOrganizerSettings newSettings = result.settings();
            newSettings.setTargetFolder(settings.getTargetFolder());
            newSettings.setAiGeneratedFolder(settings.getAiGeneratedFolder());
            newSettings.setRecycleBinFolder(settings.getRecycleBinFolder());
            newSettings.setFontSizeFactor(settings.getFontSizeFactor());
            newSettings.setShowArchivesInTree(settings.isShowArchivesInTree());
            settings = newSettings;
            configRepository.setOrganizeSettings(settings);
            configRepository.setEnhancementConfig(result.enhancementConfig());
            imageEnhancementService.reloadProviders();
            statusLabel.setText("Settings saved");
            logger.info("Settings updated: mode={}, createYear={}, createMonth={}, createDay={}, splitLowRes={}, splitMedRes={}",
                newSettings.getMode(), newSettings.isCreateYearFolder(), newSettings.isCreateMonthFolder(),
                newSettings.isCreateDayFolder(), newSettings.isSplitLowRes(), newSettings.isSplitMedRes());
        });
    }

    private void openEnhancementDialog(MediaFile mediaFile) {
        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        EnhancementConfig enhancementConfig = configRepository.getEnhancementConfig();
        List<NamedPrompt> savedPrompts = ImageEnhancementService.seedPrompts(enhancementConfig.savedPrompts());
        // Load persisted checked titles if we haven't overridden them yet this session
        if (lastCheckedPromptTitles.isEmpty() && !enhancementConfig.checkedPromptTitles().isEmpty()) {
            lastCheckedPromptTitles = new ArrayList<>(enhancementConfig.checkedPromptTitles());
        }

        // Always output to the AI-Generated folder
        Path outputDir = settings.getAiGeneratedFolder();

        boolean isVideo = mediaFile.getType() == MediaType.VIDEO;
        List<com.albumorganizer.service.enhancement.EnhancementProvider> eligibleProviders =
            imageEnhancementService.getConfiguredProviders().stream()
                .filter(p -> !isVideo || p.supportsVideo())
                .toList();

        EnhancementDialog dialog = new EnhancementDialog(
            mediaFile,
            eligibleProviders,
            savedPrompts,
            fontScale,
            lastUsedProvider,
            lastCheckedPromptTitles,
            lastAdditionalPrompt,
            enhancementConfig.comfyUiCheckpoint(),
            enhancementConfig.comfyUiCheckpoints(),
            enhancementConfig.comfyUiUrl(),
            new EnhancementDialog.ProviderParams(enhancementConfig.geminiTemperature(), enhancementConfig.grokModel()),
            enhancementConfig.debugProtocol(),
            updatedPrompts -> {
                EnhancementConfig current = configRepository.getEnhancementConfig();
                EnhancementConfig updated = new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), current.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), current.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    new ArrayList<>(updatedPrompts),
                    lastCheckedPromptTitles,
                    current.debugProtocol()
                );
                configRepository.setEnhancementConfig(updated);
            },
            provider -> lastUsedProvider = provider,
            checkedTitles -> {
                lastCheckedPromptTitles = new ArrayList<>(checkedTitles);
                EnhancementConfig current = configRepository.getEnhancementConfig();
                EnhancementConfig updated = new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), current.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), current.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    current.savedPrompts(),
                    lastCheckedPromptTitles,
                    current.debugProtocol()
                );
                configRepository.setEnhancementConfig(updated);
            },
            additionalPrompt -> lastAdditionalPrompt = additionalPrompt,
            state -> {
                EnhancementConfig current = configRepository.getEnhancementConfig();
                configRepository.setEnhancementConfig(new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), current.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), current.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), state.selected(), state.checkpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    current.savedPrompts(), current.checkedPromptTitles(),
                    current.debugProtocol()
                ));
                imageEnhancementService.reloadProviders();
            },
            pp -> {
                EnhancementConfig current = configRepository.getEnhancementConfig();
                configRepository.setEnhancementConfig(new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), pp.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), pp.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    current.savedPrompts(), current.checkedPromptTitles(),
                    current.debugProtocol()
                ));
            },
            outputDir);
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.showAndWait().ifPresent(result -> {
            if (result.success()) {
                Path enhancedFile = result.outputPath();
                Path aiGeneratedFolder = settings.getAiGeneratedFolder();
                if (aiGeneratedFolder != null && !enhancedFile.getParent().equals(aiGeneratedFolder)) {
                    Path dest = aiGeneratedFolder.resolve(enhancedFile.getFileName());
                    try {
                        java.nio.file.Files.createDirectories(aiGeneratedFolder);
                        java.nio.file.Files.move(enhancedFile, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        statusLabel.setText("Enhanced image saved to AI-Generated: " + dest.getFileName());
                        logger.info("Moved enhanced image {} -> {}", enhancedFile, dest);
                        quickScanFolder(aiGeneratedFolder);
                    } catch (java.io.IOException e) {
                        logger.error("Failed to move enhanced image to AI-Generated folder", e);
                        statusLabel.setText("Enhanced image saved: " + enhancedFile.getFileName());
                        quickScanFolder(enhancedFile.getParent());
                    }
                } else {
                    statusLabel.setText("Enhanced image saved: " + enhancedFile.getFileName());
                    quickScanFolder(enhancedFile.getParent());
                }
            } else {
                ErrorDialog.show("Enhancement Failed", "Enhancement failed", result.errorMessage());
            }
        });
        EnhancementDialog.DebugData dbgData = dialog.getDebugData();
        if (dbgData != null) {
            DebugProtocolDialog dbg = new DebugProtocolDialog(
                dbgData.request(), dbgData.result(),
                dbgData.rawRequestBody(), dbgData.rawResponseBody(),
                dbgData.fontScale());
            dbg.initOwner(rootPane.getScene().getWindow());
            dbg.showAndWait();
        }
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
    private void onFileMenuShowing() {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        boolean hasImage = cb.hasImage();
        boolean hasProviders = !imageEnhancementService.getConfiguredProviders().isEmpty();
        menuEnhanceFromClipboard.setDisable(!hasImage || !hasProviders);
    }

    @FXML
    private void onEnhanceFromClipboard() {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.image.Image fxImage = cb.getImage();
        if (fxImage == null) return;

        // Write clipboard image to a temp file in the AI-Generated folder
        Path aiDir = settings.getAiGeneratedFolder();
        long epoch = System.currentTimeMillis() / 1000;
        Path tempInput;
        try {
            java.nio.file.Files.createDirectories(aiDir);
            tempInput = aiDir.resolve("Clipboard-" + epoch + "-input.jpg");
            java.awt.image.BufferedImage awtImage = javafx.embed.swing.SwingFXUtils.fromFXImage(fxImage, null);
            if (awtImage.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB) {
                java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(
                    awtImage.getWidth(), awtImage.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = rgb.createGraphics();
                g.drawImage(awtImage, 0, 0, null);
                g.dispose();
                awtImage = rgb;
            }
            javax.imageio.ImageIO.write(awtImage, "jpg", tempInput.toFile());
        } catch (java.io.IOException e) {
            logger.error("Failed to save clipboard image to temp file", e);
            showError("Clipboard Error", "Could not save clipboard image: " + e.getMessage());
            return;
        }

        final Path tempInputFinal = tempInput;
        final long epochFinal = epoch;

        // Build a synthetic MediaFile for the clipboard image
        com.albumorganizer.model.MediaFile clipMediaFile = new com.albumorganizer.model.MediaFile(
            tempInput.getFileName().toString(),
            tempInput,
            java.time.Instant.now(),
            com.albumorganizer.model.MediaType.IMAGE,
            tempInput.toFile().length()
        );

        double fontScale = Math.pow(2.0, settings.getFontSizeFactor() / 4.0);
        EnhancementConfig enhancementConfig = configRepository.getEnhancementConfig();
        List<NamedPrompt> savedPrompts = ImageEnhancementService.seedPrompts(enhancementConfig.savedPrompts());
        if (lastCheckedPromptTitles.isEmpty() && !enhancementConfig.checkedPromptTitles().isEmpty()) {
            lastCheckedPromptTitles = new ArrayList<>(enhancementConfig.checkedPromptTitles());
        }

        EnhancementDialog dialog = new EnhancementDialog(
            clipMediaFile,
            imageEnhancementService.getConfiguredProviders(),
            savedPrompts,
            fontScale,
            lastUsedProvider,
            lastCheckedPromptTitles,
            lastAdditionalPrompt,
            enhancementConfig.comfyUiCheckpoint(),
            enhancementConfig.comfyUiCheckpoints(),
            enhancementConfig.comfyUiUrl(),
            new EnhancementDialog.ProviderParams(enhancementConfig.geminiTemperature(), enhancementConfig.grokModel()),
            enhancementConfig.debugProtocol(),
            updatedPrompts -> {
                EnhancementConfig current = configRepository.getEnhancementConfig();
                configRepository.setEnhancementConfig(new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), current.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), current.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    new ArrayList<>(updatedPrompts), lastCheckedPromptTitles,
                    current.debugProtocol()));
            },
            provider -> lastUsedProvider = provider,
            checkedTitles -> {
                lastCheckedPromptTitles = new ArrayList<>(checkedTitles);
                EnhancementConfig current = configRepository.getEnhancementConfig();
                configRepository.setEnhancementConfig(new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), current.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), current.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    current.savedPrompts(), lastCheckedPromptTitles,
                    current.debugProtocol()));
            },
            additionalPrompt -> lastAdditionalPrompt = additionalPrompt,
            state -> {
                EnhancementConfig current = configRepository.getEnhancementConfig();
                configRepository.setEnhancementConfig(new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), current.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), current.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), state.selected(), state.checkpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    current.savedPrompts(), current.checkedPromptTitles(),
                    current.debugProtocol()
                ));
                imageEnhancementService.reloadProviders();
            },
            pp -> {
                EnhancementConfig current = configRepository.getEnhancementConfig();
                configRepository.setEnhancementConfig(new EnhancementConfig(
                    current.stabilityAiEnabled(), current.stabilityAiKey(),
                    current.openAiEnabled(), current.openAiKey(),
                    current.geminiEnabled(), current.geminiKey(), pp.geminiTemperature(),
                    current.grokEnabled(), current.grokKey(), pp.grokModel(),
                    current.sdLocalEnabled(), current.sdLocalUrl(),
                    current.realEsrganEnabled(), current.realEsrganModelPath(),
                    current.comfyUiEnabled(), current.comfyUiUrl(), current.comfyUiCheckpoint(), current.comfyUiCheckpoints(),
                    current.invokeAiEnabled(), current.invokeAiUrl(),
                    current.savedPrompts(), current.checkedPromptTitles(),
                    current.debugProtocol()
                ));
            },
            aiDir);
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.showAndWait().ifPresent(result -> {
            // Clean up the temp input file
            try { java.nio.file.Files.deleteIfExists(tempInputFinal); } catch (java.io.IOException ignored) {}

            if (result.success()) {
                Path enhancedFile = result.outputPath();
                // Extract provider slug from the output filename (pattern: base_AI-{slug}-{epoch}.jpg)
                String outName = enhancedFile.getFileName().toString();
                String providerSlug = "AI";
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("_AI-([^-]+(?:-[^-]+)*)-\\d+\\.jpg$")
                    .matcher(outName);
                if (m.find()) providerSlug = m.group(1);

                String finalName = "Clipboard-" + providerSlug + "-" + epochFinal + ".jpg";
                // Rename in-place within aiDir (file is already there from the dialog)
                Path dest = enhancedFile.getParent().resolve(finalName);
                try {
                    java.nio.file.Files.move(enhancedFile, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    statusLabel.setText("Clipboard enhanced: " + finalName);
                    logger.info("Clipboard enhancement saved as {}", dest);
                    quickScanFolder(dest.getParent());
                } catch (java.io.IOException e) {
                    logger.error("Failed to rename clipboard enhanced image", e);
                    statusLabel.setText("Enhanced clipboard image saved: " + enhancedFile.getFileName());
                    quickScanFolder(enhancedFile.getParent());
                }
            } else {
                try { java.nio.file.Files.deleteIfExists(tempInputFinal); } catch (java.io.IOException ignored) {}
                ErrorDialog.show("Enhancement Failed", "Enhancement failed", result.errorMessage());
            }
        });
        EnhancementDialog.DebugData dbgData2 = dialog.getDebugData();
        if (dbgData2 != null) {
            DebugProtocolDialog dbg = new DebugProtocolDialog(
                dbgData2.request(), dbgData2.result(),
                dbgData2.rawRequestBody(), dbgData2.rawResponseBody(),
                dbgData2.fontScale());
            dbg.initOwner(rootPane.getScene().getWindow());
            dbg.showAndWait();
        }
    }

    @FXML
    private void onAddTargetFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Set Target Folder");

        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            Path newPath = selectedDirectory.toPath();
            addAlbumFolder(newPath, true);
        }
    }

    @FXML
    private void onSetAiGeneratedFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Set AI-Generated Folder");

        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            Path newPath = selectedDirectory.toPath();
            settings.setAiGeneratedFolder(newPath);
            configRepository.setOrganizeSettings(settings);
            buildDirectoryTree();
            statusLabel.setText("AI-Generated folder set: " + newPath.getFileName());
            logger.info("Set AI-Generated folder: {}", newPath);
        }
    }

    @FXML
    private void onAddRecycleBinFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Recycle-Bin Folder");

        Stage stage = (Stage) rootPane.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            Path newPath = selectedDirectory.toPath();
            settings.setRecycleBinFolder(newPath);
            configRepository.setOrganizeSettings(settings);
            buildDirectoryTree();
            statusLabel.setText("Recycle-bin folder set: " + newPath.getFileName());
            logger.info("Set recycle-bin folder: {}", newPath);
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
            progressBar.progressProperty().unbind();
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
        logger.debug("Scan panel hidden");
    }

    @FXML
    private void onViewReport() {
        if (lastOrganizeReportCompressed == null) {
            return;
        }
        try {
            String content = decompressReport(lastOrganizeReportCompressed);
            Path reportsDir = com.albumorganizer.util.AppDirs.reportsDir();
            Files.createDirectories(reportsDir);
            String filename = "album-organizer." + lastOrganizeReportTimestamp + ".txt";
            Path reportFile = reportsDir.resolve(filename);
            Files.writeString(reportFile, content);
            java.awt.Desktop.getDesktop().open(reportFile.toFile());
            logger.info("Opened report file: {}", reportFile);
        } catch (Exception e) {
            logger.error("Failed to open report file", e);
            showError("Error", "Failed to open report file:\n" + e.getMessage());
        }
    }

    private void storeReport(OrganizeReportWriter reportWriter) {
        try {
            String content = reportWriter.getContent();
            lastOrganizeReportCompressed = compressReport(content);
            lastOrganizeReportTimestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                    .format(reportWriter.getStartTime().atZone(java.time.ZoneId.systemDefault()));
            viewReportButton.setDisable(false);
        } catch (IOException e) {
            logger.error("Failed to compress report", e);
        }
    }

    private byte[] compressReport(String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private String decompressReport(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
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

        logger.debug("Found {} unique duplicate hashes in global index", duplicateHashCount);

        // Count duplicates in current view
        int duplicateCount = (int) currentDisplayedFiles.stream()
            .filter(f -> {
                String hash = f.getSha1Hash();
                return hash != null && !hash.isEmpty() && !hash.equals(NO_HASH_KEY)
                    && fileIndex.containsKey(hash) && fileIndex.get(hash).size() > 1;
            })
            .count();

        logger.debug("Should be highlighting {} duplicate files (out of {} total)", duplicateCount, currentDisplayedFiles.size());

        // Force table to refresh all rows by triggering a layout update
        mediaTable.refresh();

        // Refresh thumbnail card borders to reflect duplicate status
        if (thumbnailViewActive) {
            for (var node : thumbnailPane.getChildren()) {
                if (!(node instanceof javafx.scene.layout.VBox card)) continue;
                if (!(card.getUserData() instanceof MediaFile mf)) continue;
                applyThumbnailCardStyle(card, mf, mf == selectedThumbnailFile, false);
            }
        }
    }

    private void buildDirectoryTree() {
        // Remember currently selected directory path before rebuilding
        Path currentlySelectedPath = null;
        TreeItem<DirectoryNode> currentSelection = directoryTree.getSelectionModel().getSelectedItem();
        if (currentSelection != null && currentSelection.getValue() != null) {
            currentlySelectedPath = currentSelection.getValue().getPath();
        }
        // Fall back to saved last selected folder
        if (currentlySelectedPath == null && settings.getLastSelectedFolder() != null) {
            currentlySelectedPath = java.nio.file.Paths.get(settings.getLastSelectedFolder());
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
            albumItem.setExpanded(false);

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

        // Sort root level album folders case-insensitively (before adding special folders)
        root.getChildren().sort((item1, item2) -> {
            String name1 = item1.getValue().getDisplayName();
            String name2 = item2.getValue().getDisplayName();
            return name1.compareToIgnoreCase(name2);
        });

        // Add target folder as a special node at the bottom if configured
        if (settings.getTargetFolder() != null) {
            Path targetPath = settings.getTargetFolder();
            // Only add if not already present as an album folder
            boolean alreadyInAlbums = baseFolders.contains(targetPath);
            if (!alreadyInAlbums) {
                String targetName = targetPath.getFileName() != null ? targetPath.getFileName().toString() : targetPath.toString();
                DirectoryNode targetNode = new DirectoryNode(targetPath, targetName, true);
                TreeItem<DirectoryNode> targetItem = new TreeItem<>(targetNode);
                targetItem.setExpanded(false);
                buildSubtree(targetItem, targetPath);
                root.getChildren().add(targetItem);
                if (currentlySelectedPath != null && targetPath.equals(currentlySelectedPath)) {
                    itemToSelect = targetItem;
                }
                if (firstAlbum == null) firstAlbum = targetItem;
            }
        }

        // Add AI-Generated folder at the bottom if configured (always after album folders)
        if (settings.getAiGeneratedFolder() != null) {
            Path aiPath = settings.getAiGeneratedFolder();
            String aiName = aiPath.getFileName() != null ? aiPath.getFileName().toString() : aiPath.toString();
            DirectoryNode aiNode = new DirectoryNode(aiPath, aiName, true);
            aiNode.setAiGenerated(true);
            TreeItem<DirectoryNode> aiItem = new TreeItem<>(aiNode);
            aiItem.setExpanded(false);
            buildSubtree(aiItem, aiPath);
            root.getChildren().add(aiItem);
            if (currentlySelectedPath != null && aiPath.equals(currentlySelectedPath)) {
                itemToSelect = aiItem;
            }
        }

        // Add recycle-bin folder at the bottom if configured
        if (settings.getRecycleBinFolder() != null) {
            Path binPath = settings.getRecycleBinFolder();
            String binName = binPath.getFileName() != null ? binPath.getFileName().toString() : binPath.toString();
            DirectoryNode binNode = new DirectoryNode(binPath, binName, true);
            binNode.setRecycleBin(true);
            TreeItem<DirectoryNode> binItem = new TreeItem<>(binNode);
            binItem.setExpanded(false);
            buildSubtree(binItem, binPath);
            root.getChildren().add(binItem);
            if (currentlySelectedPath != null && binPath.equals(currentlySelectedPath)) {
                itemToSelect = binItem;
            }
        }

        directoryTree.setRoot(root);
        directoryTree.setShowRoot(false);

        // If we didn't find the previously selected path yet, search in subtrees
        if (itemToSelect == null && currentlySelectedPath != null) {
            itemToSelect = findTreeItemByPath(root, currentlySelectedPath);
        }

        // Expand the tree to show the previously selected item, but don't select it
        // (selecting would trigger onDirectorySelected and interrupt the startup scan)
        if (itemToSelect != null) {
            expandPathToItem(itemToSelect);
            int row = directoryTree.getRow(itemToSelect);
            if (row >= 0) {
                directoryTree.scrollTo(row);
            }
        } else if (firstAlbum != null) {
            expandPathToItem(firstAlbum);
        }
    }

    private void expandPathToItem(TreeItem<DirectoryNode> item) {
        TreeItem<DirectoryNode> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
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

        // Add archive nodes for any .zip/.rar files found in all directories of this tree
        if (settings != null && settings.isShowArchivesInTree()) {
            Set<Path> allDirs = new java.util.HashSet<>(pathToItem.keySet());
            for (Path dir : allDirs) {
                addArchiveChildNodes(pathToItem.get(dir), dir);
            }
        }

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

    /** Scans a directory for archive files and adds them as child nodes of the given tree item. */
    private void addArchiveChildNodes(TreeItem<DirectoryNode> dirItem, Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream
                .filter(p -> com.albumorganizer.util.FileTypeDetector.isArchive(p))
                .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                .forEach(archivePath -> {
                    String ext = archivePath.getFileName().toString()
                        .toLowerCase().endsWith(".zip") ? "ZIP" : "RAR";
                    DirectoryNode archiveNode = new DirectoryNode(
                        archivePath, // path is the archive file itself (used for display/navigation)
                        archivePath.getFileName().toString()
                    );
                    archiveNode.setArchiveType(ext);
                    archiveNode.setArchivePath(archivePath);
                    TreeItem<DirectoryNode> archiveItem = new TreeItem<>(archiveNode);
                    dirItem.getChildren().add(archiveItem);
                });
        } catch (IOException e) {
            logger.warn("Could not list directory for archive detection: {}", dir, e);
        }
    }

    /** Scans an archive node and populates the file view with its contents. */
    private void scanArchiveNode(TreeItem<DirectoryNode> archiveItem) {
        DirectoryNode archiveNode = archiveItem.getValue();
        Path archivePath = archiveNode.getArchivePath();

        statusLabel.setText("Scanning archive: " + archiveNode.getDisplayName() + "...");
        progressBar.progressProperty().unbind();
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setVisible(true);
        progressBar.setManaged(true);

        Task<List<MediaFile>> task = new Task<>() {
            @Override
            protected List<MediaFile> call() throws Exception {
                return archiveScanService.scanArchive(archivePath);
            }
        };

        task.setOnSucceeded(ev -> {
            List<MediaFile> files = task.getValue();
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            archiveNode.setMediaFileCount(files.size());
            archiveNode.setScanned(true);
            directoryTree.refresh();

            currentDisplayedFiles.setAll(files);
            updateFileCount(files.size());
            if (mediaTable.getSortOrder().isEmpty()) {
                filenameColumn.setSortType(TableColumn.SortType.ASCENDING);
                mediaTable.getSortOrder().add(filenameColumn);
            }
            mediaTable.sort();
            refreshCurrentView();
            statusLabel.setText(String.format("Archive: %s — %d media files",
                archiveNode.getDisplayName(), files.size()));
            logger.info("Archive scan complete: {} files in {}", files.size(), archivePath);
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            logger.error("Archive scan failed", ex);
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            statusLabel.setText("Archive scan failed: " + (ex != null ? ex.getMessage() : "unknown error"));
        });

        new Thread(task, "archive-scan-thread").start();
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

    private void showFirstRunDialog() {
        // Detect which well-known photo folders exist on this machine
        Path home = java.nio.file.Paths.get(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase();

        List<Path> candidates = new ArrayList<>();

        // macOS
        candidates.add(home.resolve("Pictures"));
        // Windows
        candidates.add(home.resolve("Pictures"));  // same name, different OS
        // Linux
        candidates.add(home.resolve("Pictures"));

        // Cloud storage — common locations across platforms
        // Dropbox
        candidates.add(home.resolve("Dropbox"));
        candidates.add(home.resolve("Dropbox (Personal)"));
        // Google Drive (macOS)
        candidates.add(home.resolve("Library/CloudStorage").resolve("GoogleDrive-" + System.getProperty("user.name")));
        // Google Drive (generic mount points)
        candidates.add(java.nio.file.Paths.get("/Volumes/GoogleDrive"));
        // Google Drive via google-drive-ocamlfuse (Linux)
        candidates.add(home.resolve("GoogleDrive"));
        candidates.add(home.resolve("Google Drive"));
        // OneDrive
        candidates.add(home.resolve("OneDrive"));
        candidates.add(home.resolve("OneDrive - Personal"));
        // iCloud Drive (macOS)
        candidates.add(home.resolve("Library/Mobile Documents/com~apple~CloudDocs"));

        // Scan Google Drive with wildcard account prefix (macOS)
        try {
            Path cloudStorage = home.resolve("Library/CloudStorage");
            if (java.nio.file.Files.isDirectory(cloudStorage)) {
                try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(cloudStorage)) {
                    entries.filter(p -> p.getFileName().toString().startsWith("GoogleDrive"))
                           .forEach(candidates::add);
                }
            }
        } catch (java.io.IOException ignored) {}

        // Deduplicate and keep only existing directories
        java.util.LinkedHashSet<Path> seen = new java.util.LinkedHashSet<>();
        List<Path> found = new ArrayList<>();
        for (Path p : candidates) {
            Path normalized = p.normalize();
            if (seen.add(normalized) && java.nio.file.Files.isDirectory(normalized)) {
                found.add(normalized);
            }
        }

        if (found.isEmpty()) {
            return; // Nothing to suggest
        }

        // Build dialog
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle("Welcome to Album Organizer");
        dialog.setHeaderText("No albums configured yet.\nWould you like to add your photo folders?");

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8);
        content.setPadding(new javafx.geometry.Insets(10));
        content.getChildren().add(new javafx.scene.control.Label("Detected folders:"));

        List<javafx.scene.control.CheckBox> boxes = new ArrayList<>();
        for (Path p : found) {
            String label = p.toString().replace(home.toString(), "~");
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(label);
            cb.setSelected(true);
            cb.setUserData(p);
            boxes.add(cb);
            content.getChildren().add(cb);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
            javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == javafx.scene.control.ButtonType.OK);

        dialog.showAndWait().ifPresent(ok -> {
            if (!ok) return;
            for (javafx.scene.control.CheckBox cb : boxes) {
                if (cb.isSelected()) {
                    Path folder = (Path) cb.getUserData();
                    if (!baseFolders.contains(folder)) {
                        baseFolders.add(folder);
                    }
                }
            }
            if (!baseFolders.isEmpty()) {
                configRepository.setBaseFolders(baseFolders);
                buildDirectoryTree();
                Platform.runLater(() -> startScan(new ArrayList<>(baseFolders), true));
            }
        });
    }

    private void applyDefaultSpecialFolders() {
        Path home = java.nio.file.Paths.get(System.getProperty("user.home"));
        boolean changed = false;
        if (settings.getTargetFolder() == null) {
            settings.setTargetFolder(home.resolve("AlbumTarget"));
            changed = true;
        }
        if (settings.getAiGeneratedFolder() == null) {
            settings.setAiGeneratedFolder(home.resolve("AlbumAiGenerated"));
            changed = true;
        }
        if (settings.getRecycleBinFolder() == null) {
            settings.setRecycleBinFolder(home.resolve("AlbumRecycleBin"));
            changed = true;
        }
        if (changed) {
            configRepository.setOrganizeSettings(settings);
        }
        for (Path p : new Path[]{settings.getTargetFolder(), settings.getAiGeneratedFolder(), settings.getRecycleBinFolder()}) {
            try {
                java.nio.file.Files.createDirectories(p);
            } catch (java.io.IOException e) {
                logger.warn("Could not create special folder: {}", p, e);
            }
        }
    }

    private void loadBaseFolders() {
        baseFolders = configRepository.getBaseFolders();
        if (!baseFolders.isEmpty()) {
            statusLabel.setText("Loaded " + baseFolders.size() + " saved folder(s)");
            logger.debug("Loaded {} base folders", baseFolders.size());
        }
        if (settings.getTargetFolder() != null) {
            logger.debug("Loaded target folder: {}", settings.getTargetFolder());
        }
    }

    private void loadSnapshot() {
        Map<String, List<FileIndexEntry>> snapshot = snapshotRepository.loadSnapshot();
        if (!snapshot.isEmpty()) {
            fileIndex.putAll(snapshot);
            logger.debug("Loaded snapshot with {} hash entries", snapshot.size());

            // Count total files from snapshot
            int totalFiles = snapshot.values().stream()
                .mapToInt(List::size)
                .sum();
            statusLabel.setText(String.format("Loaded snapshot: %d files indexed", totalFiles));
        }
    }

    private void saveSnapshot() {
        snapshotRepository.saveSnapshot(fileIndex);
        logger.debug("Saved snapshot with {} hash entries", fileIndex.size());
    }

    /**
     * Public method to save snapshot on application shutdown.
     * Called by AlbumOrganizerApp when window is closed.
     */
    public void saveSnapshotOnShutdown() {
        logger.debug("saveSnapshotOnShutdown called");
        saveSnapshot();
        thumbnailService.clearCache();
        logger.debug("saveSnapshotOnShutdown completed");
    }

    /**
     * Runs a quick scan on startup for all album folders and special folders.
     */
    private void startStartupQuickScan() {
        List<Path> foldersToScan = new ArrayList<>(baseFolders);
        for (Path p : new Path[]{settings.getTargetFolder(), settings.getAiGeneratedFolder(), settings.getRecycleBinFolder()}) {
            if (p != null && java.nio.file.Files.isDirectory(p) && !foldersToScan.contains(p)) {
                foldersToScan.add(p);
            }
        }
        if (foldersToScan.isEmpty()) {
            return;
        }

        logger.info("Starting startup quick scan of {} folders", foldersToScan.size());
        statusLabel.setText("Running startup quick scan...");

        // Track which folders are being scanned
        currentlyScannedFolders = new ArrayList<>(foldersToScan);

        // Create scan task with current file index
        currentScanTask = new ScanTask(scannerService, foldersToScan, false, fileIndex); // false = quick scan

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
            logger.debug("Startup quick scan completed: {}", result);

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
                logger.debug("Startup quick scan finished successfully");
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
        logger.debug("Saving snapshot before exit");
        saveSnapshot();
        logger.debug("Snapshot save completed");

        // Shutdown executor
        if (thumbnailLoadExecutor != null) {
            thumbnailLoadExecutor.shutdown();
        }

        // Clear thumbnail cache on exit
        thumbnailService.clearCache();

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
    private void onSetLogLevelDebug() {
        setAppLogLevel(Level.DEBUG);
        statusLabel.setText("Log level set to DEBUG");
    }

    @FXML
    private void onSetLogLevelInfo() {
        setAppLogLevel(Level.INFO);
        statusLabel.setText("Log level set to INFO");
    }

    @FXML
    private void onSetLogLevelWarn() {
        setAppLogLevel(Level.WARN);
        statusLabel.setText("Log level set to WARN");
    }

    private void setAppLogLevel(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("com.albumorganizer").setLevel(level);
        logger.info("Log level changed to: {}", level);
    }

    private void syncLogLevelMenu() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Level currentLevel = loggerContext.getLogger("com.albumorganizer").getLevel();
        if (currentLevel != null) {
            if (currentLevel.isGreaterOrEqual(Level.WARN)) {
                logLevelWarnMenuItem.setSelected(true);
            } else if (currentLevel.isGreaterOrEqual(Level.INFO)) {
                logLevelInfoMenuItem.setSelected(true);
            } else {
                logLevelDebugMenuItem.setSelected(true);
            }
        }
    }

    @FXML
    private void onListView() {
        MediaFile selected = getSelectedFile();
        thumbnailViewActive = false;
        mediaTable.setVisible(true);
        thumbnailScrollPane.setVisible(false);
        mediaTable.setItems(currentDisplayedFiles);
        if (selected != null) {
            mediaTable.getSelectionModel().select(selected);
            mediaTable.scrollTo(selected);
        }
        settings.setThumbnailView(false);
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Switched to List View");
        logger.debug("Switched to List View");
    }

    @FXML
    private void onThumbnailView() {
        MediaFile selected = getSelectedFile();
        thumbnailViewActive = true;
        mediaTable.setVisible(false);
        thumbnailScrollPane.setVisible(true);
        loadThumbnails(selected);
        settings.setThumbnailView(true);
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Switched to Thumbnail View");
        logger.debug("Switched to Thumbnail View");
    }

    @FXML
    private void onToggleShowArchives() {
        boolean show = showArchivesMenuItem.isSelected();
        settings.setShowArchivesInTree(show);
        configRepository.setOrganizeSettings(settings);
        buildDirectoryTree();
    }

    @FXML
    private void onToggleProgressPanel() {
        boolean show = showProgressPanelMenuItem.isSelected();
        progressPanel.setVisible(show);
        progressPanel.setManaged(show);
        logger.debug("Scan panel {}", show ? "shown" : "hidden");
    }

    @FXML
    private void onIncreaseFontSize() {
        settings.setFontSizeFactor(settings.getFontSizeFactor() + 1);
        applyFontSize();
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Increased font size");
        logger.debug("Increased font size factor to: {}", settings.getFontSizeFactor());
    }

    @FXML
    private void onDecreaseFontSize() {
        settings.setFontSizeFactor(settings.getFontSizeFactor() - 1);
        applyFontSize();
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Decreased font size");
        logger.debug("Decreased font size factor to: {}", settings.getFontSizeFactor());
    }

    @FXML
    private void onResetFontSize() {
        settings.setFontSizeFactor(0);
        applyFontSize();
        configRepository.setOrganizeSettings(settings);
        statusLabel.setText("Reset font size to 100%");
        logger.debug("Reset font size factor to 0");
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

    @FXML
    private void onToggleFullScreen() {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }

    @FXML
    private void onSortByName() {
        applySortOrder(filenameColumn);
    }

    @FXML
    private void onSortByDateTaken() {
        applySortOrder(dateTakenColumn);
    }

    @FXML
    private void onSortByDate() {
        applySortOrder(dateColumn);
    }

    @FXML
    private void onSortByType() {
        applySortOrder(typeColumn);
    }

    @FXML
    private void onSortByDuration() {
        applySortOrder(durationColumn);
    }

    @FXML
    private void onSortByResolution() {
        applySortOrder(resolutionColumn);
    }

    @FXML
    private void onSortAscending() {
        reapplyCurrentSort();
    }

    @FXML
    private void onSortDescending() {
        reapplyCurrentSort();
    }

    private void reapplyCurrentSort() {
        if (!mediaTable.getSortOrder().isEmpty()) {
            applySortOrder(mediaTable.getSortOrder().get(0));
        }
    }

    private void applySortOrder(TableColumn<MediaFile, ?> column) {
        TableColumn.SortType direction = (sortAscendingMenuItem != null && sortAscendingMenuItem.isSelected())
            ? TableColumn.SortType.ASCENDING
            : TableColumn.SortType.DESCENDING;
        mediaTable.getSortOrder().clear();
        column.setSortType(direction);
        mediaTable.getSortOrder().add(column);
        mediaTable.sort();
        if (thumbnailViewActive) {
            loadThumbnails();
        }
    }

    private MediaFile getSelectedFile() {
        if (thumbnailViewActive) {
            // In thumbnail view, selection is tracked via the field
            return selectedThumbnailFile;
        }
        return mediaTable.getSelectionModel().getSelectedItem();
    }

    private void scrollThumbnailToFile(MediaFile target) {
        if (target == null) return;
        for (var node : thumbnailPane.getChildren()) {
            if (!(node instanceof javafx.scene.layout.VBox card)) continue;
            if (card.getUserData() == target) {
                thumbnailScrollPane.layout();
                double y = card.getBoundsInParent().getMinY();
                double contentH = thumbnailPane.getBoundsInLocal().getHeight();
                double vpH = thumbnailScrollPane.getViewportBounds().getHeight();
                if (contentH > vpH) {
                    thumbnailScrollPane.setVvalue(Math.min(1.0, y / (contentH - vpH)));
                }
                break;
            }
        }
    }

    private void selectThumbnailCard(MediaFile target) {
        if (target == null) return;
        selectedThumbnailFile = target;
        for (var node : thumbnailPane.getChildren()) {
            if (!(node instanceof javafx.scene.layout.VBox card)) continue;
            if (!(card.getUserData() instanceof MediaFile mf)) continue;
            applyThumbnailCardStyle(card, mf, mf == target, false);
        }
    }

    private void applyThumbnailCardStyle(javafx.scene.layout.VBox card, MediaFile mf,
                                          boolean selected, boolean hovered) {
        boolean isDuplicate = mf != null && mf.getSha1Hash() != null
            && !mf.getSha1Hash().isEmpty() && !mf.getSha1Hash().equals(NO_HASH_KEY)
            && fileIndex.containsKey(mf.getSha1Hash())
            && fileIndex.get(mf.getSha1Hash()).size() > 1;

        if (selected) {
            card.setStyle("-fx-padding: 10; -fx-background-color: #cce5ff;"
                + " -fx-border-color: #0078d7; -fx-border-width: 2;"
                + " -fx-border-radius: 5; -fx-background-radius: 5;");
        } else if (hovered) {
            card.setStyle("-fx-padding: 10; -fx-background-color: #f0f0f0;"
                + " -fx-border-color: #0078d7; -fx-border-width: 2;"
                + " -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");
        } else if (isDuplicate) {
            card.setStyle("-fx-padding: 10; -fx-background-color: white;"
                + " -fx-border-color: #e6b800; -fx-border-width: 2;"
                + " -fx-border-radius: 5; -fx-background-radius: 5;");
        } else {
            card.setStyle("-fx-padding: 10; -fx-background-color: white;"
                + " -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
        }
    }

    private void refreshCurrentView() {
        if (thumbnailViewActive) {
            loadThumbnails();
        } else {
            mediaTable.refresh();
        }
    }

    private void loadThumbnails() {
        loadThumbnails(selectedThumbnailFile);
    }

    private void loadThumbnails(MediaFile preserveSelection) {
        thumbnailPane.getChildren().clear();
        selectedThumbnailFile = null;

        if (currentDisplayedFiles.isEmpty()) {
            Label emptyLabel = new Label("No files to display");
            emptyLabel.setStyle("-fx-font-size: 1em; -fx-text-fill: gray;");
            thumbnailPane.getChildren().add(emptyLabel);
            return;
        }

        int totalFiles = currentDisplayedFiles.size();
        AtomicInteger thumbnailsCompleted = new AtomicInteger(0);

        for (MediaFile mediaFile : currentDisplayedFiles) {
            VBox thumbnailBox = createThumbnailCard(mediaFile, totalFiles, thumbnailsCompleted);
            thumbnailPane.getChildren().add(thumbnailBox);
        }

        if (preserveSelection != null) {
            selectThumbnailCard(preserveSelection);
        }
    }

    private VBox createThumbnailCard(MediaFile mediaFile, int totalFiles, AtomicInteger thumbnailsCompleted) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(180, 220);
        applyThumbnailCardStyle(card, mediaFile, false, false);

        // Placeholder image view
        ImageView imageView = new ImageView();
        imageView.setFitWidth(160);
        imageView.setFitHeight(160);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // Loading spinner shown until thumbnail is ready
        ProgressIndicator loadingSpinner = new ProgressIndicator();
        loadingSpinner.setPrefSize(40, 40);
        loadingSpinner.setMaxSize(40, 40);

        // Wrap ImageView in a fixed-size container to prevent rotation overflow
        StackPane imageContainer = new StackPane(loadingSpinner, imageView);
        imageContainer.setPrefSize(160, 160);
        imageContainer.setMinSize(160, 160);
        imageContainer.setMaxSize(160, 160);

        // Filename label
        Label filenameLabel = new Label(mediaFile.getFilename());
        filenameLabel.setWrapText(true);
        filenameLabel.setMaxWidth(160);

        // Style based on corrupted status
        if (mediaFile.isCorrupted()) {
            filenameLabel.setStyle("-fx-font-size: 0.7em; -fx-text-alignment: center; -fx-text-fill: darkred;");
        } else {
            filenameLabel.setStyle("-fx-font-size: 0.7em; -fx-text-alignment: center;");
        }

        card.getChildren().addAll(imageContainer, filenameLabel);

        // Tag card so scrollThumbnailToFile can locate it
        card.setUserData(mediaFile);

        // Load thumbnail asynchronously
        loadThumbnailAsync(mediaFile, imageView, loadingSpinner, totalFiles, thumbnailsCompleted);

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

        MenuItem copyImageItem = new MenuItem("Copy Image");
        copyImageItem.setOnAction(e -> copyImageToClipboard(mediaFile));

        MenuItem removeOtherDuplicatesItem = new MenuItem("Remove Other Duplicates");
        removeOtherDuplicatesItem.setOnAction(e -> removeOtherDuplicates(mediaFile));

        MenuItem deleteFileThumbItem = new MenuItem("Delete File");
        deleteFileThumbItem.setOnAction(e -> deleteFile(mediaFile));

        MenuItem restoreFromBinThumbItem = new MenuItem("Restore to Original Location");
        restoreFromBinThumbItem.setOnAction(e -> restoreFromBin(mediaFile));

        MenuItem enhanceWithAiItem = new MenuItem("Enhance with AI...");
        enhanceWithAiItem.setOnAction(e -> openEnhancementDialog(mediaFile));

        // Enable/disable based on context
        contextMenu.setOnShowing(event -> {
            boolean hasDupes = hasDuplicates(mediaFile);
            removeOtherDuplicatesItem.setDisable(!hasDupes);
            enhanceWithAiItem.setDisable(imageEnhancementService.getConfiguredProviders().isEmpty()
                || (mediaFile.getType() != MediaType.IMAGE
                    && !(mediaFile.getType() == MediaType.VIDEO
                        && imageEnhancementService.getConfiguredProviders().stream()
                            .anyMatch(com.albumorganizer.service.enhancement.EnhancementProvider::supportsVideo))));
            copyImageItem.setDisable(mediaFile.getType() != MediaType.IMAGE);
            boolean hasBin = settings != null && settings.getRecycleBinFolder() != null;
            boolean isInBin = hasBin && mediaFile.getAbsolutePath().startsWith(settings.getRecycleBinFolder());
            deleteFileThumbItem.setDisable(!hasBin || isInBin);
            restoreFromBinThumbItem.setVisible(isInBin);
            restoreFromBinThumbItem.setDisable(!recycleBinRepository.isInBin(mediaFile.getAbsolutePath()));
        });

        contextMenu.getItems().addAll(
            openItem,
            showInFolderItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyFilenameItem,
            copyImageItem,
            new SeparatorMenuItem(),
            removeOtherDuplicatesItem,
            deleteFileThumbItem,
            restoreFromBinThumbItem,
            new SeparatorMenuItem(),
            enhanceWithAiItem
        );

        // Mouse event handlers
        card.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                selectThumbnailCard(mediaFile);
                if (event.getClickCount() == 2) {
                    openFile(mediaFile);
                }
            } else if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                selectThumbnailCard(mediaFile);
                contextMenu.show(card, event.getScreenX(), event.getScreenY());
            }
        });

        // Hover effect + tooltip
        Tooltip thumbTooltip = buildThumbnailTooltip(mediaFile);
        Tooltip.install(card, thumbTooltip);
        card.setOnMouseEntered(e -> {
            if (selectedThumbnailFile != mediaFile)
                applyThumbnailCardStyle(card, mediaFile, false, true);
        });
        card.setOnMouseExited(e -> {
            if (selectedThumbnailFile != mediaFile)
                applyThumbnailCardStyle(card, mediaFile, false, false);
        });

        return card;
    }

    private Tooltip buildThumbnailTooltip(MediaFile mediaFile) {
        StringBuilder sb = new StringBuilder();
        sb.append(mediaFile.getFilename());
        if (mediaFile.getResolution() != null) {
            sb.append("\n").append(mediaFile.getResolution().width)
              .append(" × ").append(mediaFile.getResolution().height);
        }
        if (mediaFile.getDateTaken() != null) {
            sb.append("\n").append(com.albumorganizer.util.FormatUtils.formatDateTaken(
                mediaFile.getDateTaken(), mediaFile.isDateEstimated()));
        }
        sb.append("\n").append(com.albumorganizer.util.FormatUtils.formatSize(mediaFile.getSizeBytes()));
        // Show original path if this file is in the recycle bin
        Path originalPath = recycleBinRepository.getOriginalPath(mediaFile.getAbsolutePath());
        if (originalPath != null) {
            sb.append("\nOriginal: ").append(originalPath);
        }
        Tooltip tt = new Tooltip(sb.toString());
        tt.setShowDelay(javafx.util.Duration.millis(400));
        return tt;
    }

    private void loadThumbnailAsync(MediaFile mediaFile, ImageView imageView, ProgressIndicator loadingSpinner,
                                     int totalFiles, AtomicInteger thumbnailsCompleted) {
        Task<Image> loadTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                try {
                    // Archive entry: extract bytes and generate thumbnail in-memory
                    if (com.albumorganizer.service.ArchiveScanService.isArchiveEntry(mediaFile.getAbsolutePath())) {
                        String[] parts = com.albumorganizer.service.ArchiveScanService
                            .splitArchivePath(mediaFile.getAbsolutePath());
                        if (parts != null) {
                            byte[] bytes = archiveScanService.extractEntry(
                                java.nio.file.Paths.get(parts[0]), parts[1]);
                            if (bytes != null) {
                                String cacheKey = com.albumorganizer.service.ThumbnailService
                                    .archiveEntryCacheKey(parts[0], parts[1]);
                                return thumbnailService.generateThumbnailFromBytes(bytes, cacheKey);
                            }
                        }
                        return null;
                    }
                    if (thumbnailService.needsSpecialHandling(mediaFile.getAbsolutePath(), mediaFile.getType())) {
                        Image thumbnail = thumbnailService.generateThumbnail(
                                mediaFile.getAbsolutePath(), mediaFile.getType());
                        if (thumbnail != null) {
                            return thumbnail;
                        }
                        // Video returned null — check if audio-only to show correct placeholder
                        if (mediaFile.getType() == MediaType.VIDEO
                                && thumbnailService.isAudioOnly(mediaFile.getAbsolutePath())) {
                            return AUDIO_ONLY_SENTINEL;
                        }
                        return null;
                    }
                    // Try loading directly via JavaFX; fall back if it fails (e.g. wrong extension)
                    String fileUri = mediaFile.getAbsolutePath().toUri().toString();
                    Image direct = new Image(fileUri, 160, 160, true, true, false);
                    if (!direct.isError() && direct.getWidth() > 0) {
                        return direct;
                    }
                    // Fallback: generate thumbnail using cache/ImageIO (handles wrong-extension files)
                    return thumbnailService.generateThumbnail(
                            mediaFile.getAbsolutePath(), mediaFile.getType());
                } catch (Exception e) {
                    logger.warn("Failed to load thumbnail for: {}", mediaFile.getFilename(), e);
                    return null;
                }
            }
        };

        loadTask.setOnSucceeded(event -> {
            Image image = loadTask.getValue();
            Platform.runLater(() -> {
                loadingSpinner.setVisible(false);
                if (image == AUDIO_ONLY_SENTINEL) {
                    Label audioLabel = new Label("🎵");
                    audioLabel.setStyle("-fx-font-size: 3em;");
                    imageView.setImage(null);
                    StackPane parent = (StackPane) imageView.getParent();
                    if (parent != null && !parent.getChildren().contains(audioLabel)) {
                        parent.getChildren().add(audioLabel);
                    }
                } else if (image != null) {
                    imageView.setImage(image);
                    applyOrientationRotation(imageView, mediaFile.getOrientation());
                } else {
                    Label errorLabel = new Label("?");
                    errorLabel.setStyle("-fx-font-size: 3em; -fx-text-fill: #999;");
                    imageView.setImage(null);
                }
                onThumbnailCompleted(totalFiles, thumbnailsCompleted);
            });
        });

        thumbnailLoadExecutor.submit(loadTask);
    }

    private void onThumbnailCompleted(int totalFiles, AtomicInteger thumbnailsCompleted) {
        int done = thumbnailsCompleted.incrementAndGet();
        if (progressBar.isVisible() && progressBar.isManaged()) {
            progressBar.setProgress(done / (double) totalFiles);
            statusLabel.setText(String.format("Loading thumbnails... %d/%d", done, totalFiles));
            if (done >= totalFiles) {
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                statusLabel.setText(String.format("Loaded %d files", totalFiles));
            }
        }
    }

    private void applyOrientationRotation(ImageView imageView, int orientation) {
        switch (orientation) {
            case 2:                                     // EXIF mirror horizontal (no rotation for display)
                break;
            case 3:                                     // EXIF upside down
            case 4:                                     // EXIF mirror + 180° — display as 180°
                imageView.setRotate(180);
                break;
            case 5:                                     // EXIF transposed (mirror + 90 CW)
            case 6:                                     // EXIF rotated 90 CW
                imageView.setRotate(90);
                imageView.setFitWidth(120);
                imageView.setFitHeight(120);
                break;
            case 7:                                     // EXIF transverse (mirror + 270 CW)
            case 8:                                     // EXIF rotated 270 CW
                imageView.setRotate(270);
                imageView.setFitWidth(120);
                imageView.setFitHeight(120);
                break;
            // Video rotation (90, 180, 270) is handled by ThumbnailService during frame extraction
        }
    }

    private long parsePixelCount(String resolution) {
        if (resolution == null || resolution.equals("-")) return 0;
        String[] parts = resolution.split("[x×]");
        if (parts.length == 2) {
            try {
                return Long.parseLong(parts[0].trim()) * Long.parseLong(parts[1].trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
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
     * Removes all other duplicate files (keeps the selected one, moves others to recycle bin).
     */
    private void removeOtherDuplicates(MediaFile keepFile) {
        if (keepFile == null || keepFile.getSha1Hash() == null) {
            return;
        }

        if (settings.getRecycleBinFolder() == null) {
            showWarning("Recycle-Bin Not Configured",
                "Please configure a Recycle-Bin folder first via File > Add Recycle-Bin Folder.");
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
        Label deleteLabel = new Label("Select files to MOVE TO RECYCLE-BIN (uncheck to keep):");
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
        ButtonType deleteButtonType = new ButtonType("Move to Recycle-Bin", ButtonBar.ButtonData.OK_DONE);
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

        // Move selected duplicates to recycle bin
        int successCount = 0;
        int failCount = 0;
        List<String> failedFiles = new ArrayList<>();
        String hash = keepFile.getSha1Hash();
        Path binFolder = settings.getRecycleBinFolder();

        for (Path duplicatePath : filesToDelete) {
            boolean success = moveToBin(duplicatePath, binFolder);
            if (success) {
                successCount++;
                // Remove from fileIndex exhaustively and save snapshot
                removeFileFromIndex(duplicatePath);

                // Remove from currentDisplayedFiles using case-insensitive normalized comparison
                currentDisplayedFiles.removeIf(file -> {
                    Path fileAbs = file.getAbsolutePath().normalize().toAbsolutePath();
                    Path dupAbs = duplicatePath.normalize().toAbsolutePath();
                    return fileAbs.toString().equalsIgnoreCase(dupAbs.toString());
                });
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
            statusLabel.setText("Moved " + successCount + " duplicate file(s) to recycle bin");
            logger.info("Successfully moved {} duplicates of: {} to bin", successCount, keepFile.getFilename());
        } else {
            String message = String.format(
                "Moved %d file(s) to recycle bin.\n\nFailed to move %d file(s):\n%s",
                successCount,
                failCount,
                String.join("\n", failedFiles.stream().limit(10).collect(Collectors.toList()))
            );
            showWarning("Partial Success", message);
            logger.warn("Moved {} duplicates, failed to move {}", successCount, failCount);
        }
    }

    /** Returns true if the subtree rooted at folderPath contains any file that has duplicates outside the subtree. */
    private boolean subtreeHasExternalDuplicates(Path folderPath) {
        for (Map.Entry<String, List<FileIndexEntry>> entry : fileIndex.entrySet()) {
            if (entry.getKey().equals(NO_HASH_KEY)) continue;
            List<FileIndexEntry> entries = entry.getValue();
            if (entries.size() <= 1) continue;
            boolean hasInside  = entries.stream().anyMatch(e -> e.getDirectory().startsWith(folderPath));
            boolean hasOutside = entries.stream().anyMatch(e -> !e.getDirectory().startsWith(folderPath));
            if (hasInside && hasOutside) return true;
        }
        return false;
    }

    private void removeOtherDuplicatesInSubtree(Path folderPath) {
        Path binFolder = settings.getRecycleBinFolder();
        if (binFolder == null) {
            showWarning("Recycle-Bin Not Configured",
                "Please configure a Recycle-Bin folder first via File > Add Recycle-Bin Folder.");
            return;
        }

        // Collect: for each hash that has copies both inside and outside, gather the outside ones
        Map<String, List<Path>> toRemoveByHash = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<FileIndexEntry>> entry : fileIndex.entrySet()) {
            if (entry.getKey().equals(NO_HASH_KEY)) continue;
            List<FileIndexEntry> entries = entry.getValue();
            if (entries.size() <= 1) continue;
            boolean hasInside = entries.stream().anyMatch(e -> e.getDirectory().startsWith(folderPath));
            if (!hasInside) continue;
            List<Path> outside = entries.stream()
                .filter(e -> !e.getDirectory().startsWith(folderPath))
                .map(FileIndexEntry::getAbsolutePath)
                .collect(Collectors.toList());
            if (!outside.isEmpty()) toRemoveByHash.put(entry.getKey(), outside);
        }

        int totalToRemove = toRemoveByHash.values().stream().mapToInt(List::size).sum();
        if (totalToRemove == 0) {
            showWarning("No External Duplicates",
                "No duplicates outside this folder were found.");
            return;
        }

        String folderName = folderPath.getFileName() != null ? folderPath.getFileName().toString() : folderPath.toString();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Other Duplicates");
        confirm.setHeaderText("Move external duplicates to Recycle-Bin?");
        confirm.setContentText(String.format(
            "Found %d file(s) with duplicates inside \"%s\".\n"
            + "%d duplicate(s) outside the folder will be moved to the recycle bin.\n\n"
            + "Files inside the folder are kept. Only external copies are removed.",
            toRemoveByHash.size(), folderName, totalToRemove));
        ButtonType goBtn = new ButtonType("Move to Recycle-Bin", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(cancelBtn, goBtn);
        ((Button) confirm.getDialogPane().lookupButton(cancelBtn)).setDefaultButton(true);
        ((Button) confirm.getDialogPane().lookupButton(goBtn)).setDefaultButton(false);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != goBtn) return;

        // Run the operation in the background
        progressLogList.getItems().clear();
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressPanel.setVisible(true);
        progressPanel.setManaged(true);
        showProgressPanelMenuItem.setSelected(true);
        stopProgressButton.setDisable(true);

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() {
                int moved = 0, failed = 0, skipped = 0;
                int total = totalToRemove;
                int processed = 0;
                for (Map.Entry<String, List<Path>> entry : toRemoveByHash.entrySet()) {
                    for (Path p : entry.getValue()) {
                        if (isCancelled()) break;
                        if (!Files.exists(p)) {
                            skipped++;
                        } else if (moveToBin(p, binFolder)) {
                            moved++;
                            final String name = p.getFileName().toString();
                            final int m = moved;
                            Platform.runLater(() -> {
                                logProgress("✓ Moved: " + name + " (" + m + " so far)");
                                final Path removed = p;
                                removeFileFromIndex(removed);
                                currentDisplayedFiles.removeIf(f -> {
                                    Path fAbs = f.getAbsolutePath().normalize().toAbsolutePath();
                                    Path remAbs = removed.normalize().toAbsolutePath();
                                    return fAbs.toString().equalsIgnoreCase(remAbs.toString());
                                });
                            });
                        } else {
                            failed++;
                            final String name = p.getFileName().toString();
                            Platform.runLater(() -> logProgress("✗ Failed: " + name));
                        }
                        processed++;
                        updateProgress(processed, total);
                    }
                    if (isCancelled()) break;
                }
                return new int[]{moved, failed, skipped};
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(ev -> {
            int[] stats = task.getValue();
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            stopProgressButton.setDisable(true);

            // Refresh UI
            refreshCurrentView();
            updateFileCount(currentDisplayedFiles.size());
            highlightDuplicates();
            buildDirectoryTree();

            String summary = String.format(
                "Done — moved %d, failed %d, skipped %d (already gone)",
                stats[0], stats[1], stats[2]);
            logProgress(summary);
            statusLabel.setText(summary);
            logger.info("removeOtherDuplicatesInSubtree({}): {}", folderPath, summary);
        });

        task.setOnFailed(ev -> {
            progressBar.progressProperty().unbind();
            stopProgressButton.setDisable(true);
            logProgress("Operation failed: " + task.getException().getMessage());
            logger.error("removeOtherDuplicatesInSubtree failed", task.getException());
        });

        currentOrganizeTask = task;
        stopProgressButton.setDisable(false);
        logProgress("Starting: remove external duplicates for \"" + folderName + "\" (" + totalToRemove + " files)...");
        new Thread(task, "remove-duplicates-subtree").start();
    }

    private void removeFileFromIndex(Path filePath) {
        if (filePath == null) return;
        Path normalizedPath = filePath.normalize().toAbsolutePath();
        String pathStr = normalizedPath.toString();

        fileIndex.values().forEach(entries ->
            entries.removeIf(entry -> {
                Path entryPath = entry.getAbsolutePath().normalize().toAbsolutePath();
                return entryPath.toString().equalsIgnoreCase(pathStr);
            })
        );
        fileIndex.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        snapshotRepository.saveSnapshot(fileIndex);
    }

    private void deleteFile(MediaFile mediaFile) {
        if (mediaFile == null) return;

        Path binFolder = settings.getRecycleBinFolder();
        if (binFolder == null) {
            showWarning("Recycle-Bin Not Configured",
                "Please configure a Recycle-Bin folder first via File > Add Recycle-Bin Folder.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete File");
        confirm.setHeaderText("Move file to Recycle-Bin?");
        confirm.setContentText(mediaFile.getAbsolutePath().toString());
        ButtonType moveBtn = new ButtonType("Move to Recycle-Bin", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(cancelBtn, moveBtn);
        Button cancelButton = (Button) confirm.getDialogPane().lookupButton(cancelBtn);
        cancelButton.setDefaultButton(true);
        ((Button) confirm.getDialogPane().lookupButton(moveBtn)).setDefaultButton(false);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != moveBtn) return;

        Path filePath = mediaFile.getAbsolutePath();
        if (moveToBin(filePath, binFolder)) {
            // Remove from file index exhaustively and save snapshot
            removeFileFromIndex(filePath);
            
            // Remove from currentDisplayedFiles using case-insensitive normalized comparison
            currentDisplayedFiles.removeIf(file -> {
                Path fileAbs = file.getAbsolutePath().normalize().toAbsolutePath();
                Path filePathAbs = filePath.normalize().toAbsolutePath();
                return fileAbs.toString().equalsIgnoreCase(filePathAbs.toString());
            });

            refreshCurrentView();
            updateFileCount(currentDisplayedFiles.size());
            highlightDuplicates();
            buildDirectoryTree();
            // Scan the bin folder so the new file appears in the tree and index
            quickScanFolder(binFolder);
            statusLabel.setText("Moved to recycle bin: " + filePath.getFileName());
        } else {
            showError("Move Failed", "Could not move file to recycle bin: " + filePath.getFileName());
        }
    }

    private void restoreFromBin(MediaFile mediaFile) {
        if (mediaFile == null) return;
        Path binPath = mediaFile.getAbsolutePath();
        Path binFolder = binPath.getParent();
        Path originalPath = recycleBinRepository.getOriginalPath(binPath);
        if (originalPath == null) {
            showWarning("No Record", "No original path recorded for this file.");
            return;
        }
        try {
            Files.createDirectories(originalPath.getParent());
            if (Files.exists(originalPath)) {
                showWarning("File Exists", "A file already exists at the original location:\n" + originalPath);
                return;
            }
            Files.move(binPath, originalPath);
            recycleBinRepository.remove(binPath);
            currentDisplayedFiles.remove(mediaFile);
            refreshCurrentView();
            updateFileCount(currentDisplayedFiles.size());
            highlightDuplicates();
            // Scan both the bin folder (to reflect removal) and the original folder (to reflect addition)
            quickScanFolder(binFolder);
            quickScanFolder(originalPath.getParent());
            statusLabel.setText("Restored: " + originalPath.getFileName());
            logger.info("Restored {} to {}", binPath, originalPath);
        } catch (IOException e) {
            logger.error("Failed to restore file from bin", e);
            showError("Restore Failed", "Could not restore file: " + e.getMessage());
        }
    }

    private boolean moveToBin(Path sourcePath, Path binFolder) {
        try {
            Files.createDirectories(binFolder);
            String filename = sourcePath.getFileName().toString();
            Path target = binFolder.resolve(filename);
            // If a file with that name already exists in the bin, add a numeric suffix
            if (Files.exists(target)) {
                int dot = filename.lastIndexOf('.');
                String base = dot >= 0 ? filename.substring(0, dot) : filename;
                String ext  = dot >= 0 ? filename.substring(dot) : "";
                int counter = 1;
                do {
                    target = binFolder.resolve(base + "_" + counter + ext);
                    counter++;
                } while (Files.exists(target));
            }
            Files.move(sourcePath, target);
            recycleBinRepository.add(target, sourcePath);
            logger.info("Moved {} to bin as {}", sourcePath, target);
            return true;
        } catch (IOException e) {
            logger.error("Failed to move {} to bin", sourcePath, e);
            return false;
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
                logger.debug("Organized file {} to {}", mediaFile.getFilename(), result.getTargetPath());

                if (reportWriter != null) {
                    reportWriter.logSuccess(mediaFile, sourcePath, result.getTargetPath());
                    reportWriter.writeSummary(1, 1, 0, 0);
                    reportWriter.close();
                    storeReport(reportWriter);
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
                    logger.debug("Rescanning target folder to update index: {}", targetFolder);
                    fullScanWithHashFolder(targetFolder);
                } else if (targetFolder != null) {
                    logger.debug("Target folder is not in base folders, adding it: {}", targetFolder);
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
                    storeReport(reportWriter);
                }
            } else {
                statusLabel.setText("Failed to organize file");
                logger.error("Failed to organize file {}: {}", mediaFile.getFilename(), result.getErrorMessage());
                showError("Error", "Failed to organize file:\n" + result.getErrorMessage());

                if (reportWriter != null) {
                    reportWriter.logFailure(mediaFile, sourcePath, result.getErrorMessage());
                    reportWriter.writeSummary(1, 0, 0, 1);
                    reportWriter.close();
                    storeReport(reportWriter);
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
                    storeReport(reportWriter);
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
                List<Path> organizedPaths = new ArrayList<>();

                // Create report writer
                try {
                    reportWriter = new OrganizeReportWriter(rootPath);
                } catch (IOException e) {
                    logger.error("Failed to create report writer", e);
                    return new OrganizeRecursiveResult(0, 0, 0, 0,
                        List.of("Failed to create report: " + e.getMessage()), List.of());
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
                            return new OrganizeRecursiveResult(0, 0, 0, 0, errors, List.of());
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
                        List.of("Failed to collect files: " + e.getMessage()), List.of());
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
                            organizedPaths.add(sourcePath);
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

                // Store report content compressed for on-demand viewing
                try {
                    lastOrganizeReportCompressed = compressReport(reportWriter.getContent());
                    lastOrganizeReportTimestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                            .format(reportWriter.getStartTime().atZone(java.time.ZoneId.systemDefault()));
                } catch (IOException e) {
                    logger.error("Failed to compress report", e);
                }

                return new OrganizeRecursiveResult(processed, succeeded, skipped, failed, errors, organizedPaths);
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

                // Remove successfully organized files from fileIndex and currentDisplayedFiles
                if (result.organizedPaths != null) {
                    for (Path organizedPath : result.organizedPaths) {
                        removeFileFromIndex(organizedPath);
                        currentDisplayedFiles.removeIf(f -> {
                            Path fAbs = f.getAbsolutePath().normalize().toAbsolutePath();
                            Path orgAbs = organizedPath.normalize().toAbsolutePath();
                            return fAbs.toString().equalsIgnoreCase(orgAbs.toString());
                        });
                    }
                }

                refreshCurrentView();
                updateFileCount(currentDisplayedFiles.size());
                highlightDuplicates();
                buildDirectoryTree();

                String summary = String.format("Organize Complete: %d processed, %d succeeded, %d skipped, %d failed",
                    result.processed, result.succeeded, result.skipped, result.failed);

                statusLabel.setText(summary);
                logger.info(summary);

                // Enable View Report button
                viewReportButton.setDisable(false);

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
        final List<Path> organizedPaths;

        OrganizeRecursiveResult(int processed, int succeeded, int skipped, int failed, List<String> errors, List<Path> organizedPaths) {
            this.processed = processed;
            this.succeeded = succeeded;
            this.skipped = skipped;
            this.failed = failed;
            this.errors = errors;
            this.organizedPaths = organizedPaths;
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
