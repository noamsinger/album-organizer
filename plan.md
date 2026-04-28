# Album Organizer - Implementation Plan

## Context

This project aims to create a cross-platform desktop application for scanning, organizing, and managing image and video files on a local machine. The user needs a tool to maintain an inventory of their media files across multiple directories, with the ability to detect duplicates, track file changes, and efficiently rescan only modified content. The application must work on Windows, macOS, and Linux.

The project directory is currently empty - this is a complete greenfield implementation.

## Technology Stack

- **Java 17+** (LTS requirement for modern JavaFX)
- **JavaFX 21.0.1** (cross-platform UI framework)
- **Maven** (build system - better JavaFX plugin support than Gradle)
- **metadata-extractor** (image/video metadata extraction)
- **commons-io, commons-codec, guava** (utilities)
- **No INI library needed** - simple CSV-like format parsed with standard Java
- **JUnit 5 + TestFX + Mockito** (testing)

## Architecture Overview

**Package Structure:**
```
com.albumorganizer/
├── AlbumOrganizerApp.java       # Main entry point
├── controller/                   # UI controllers (FXML-based)
├── model/                        # Domain objects (MediaFile, CacheEntry, etc.)
├── service/                      # Business logic (scanning, hashing, metadata)
├── repository/                   # Data persistence (INI files, config)
├── task/                         # Background JavaFX tasks
└── util/                         # Utilities (file type detection, formatters)
```

**Core Data Flow:**
1. User selects base folders → stored in ConfigRepository
2. Deep/Quick scan initiated → ScannerService delegates to strategy
3. Strategy walks directories, identifies image/video files
4. For each file: extract metadata → calculate SHA-1 hash → create MediaFile
5. Read existing `album-organizer.ini` from each folder
6. Compare current state vs cached state → detect new/modified/deleted
7. Update INI files → return ScanResult
8. UI updates: DirectoryTree (left panel) + MediaTable (right panel)

## Key Features & Implementation

### 1. Dual-Panel UI Layout
- **Left Panel**: TreeView displaying directory hierarchy of scanned folders
- **Right Panel**: TableView with columns: Filename, Date, Type, Size, Resolution, Location, Hash (SHA-1)
- Layout: BorderPane with MenuBar (top), SplitPane (center), StatusBar (bottom)
- FXML-based design for separation of UI and logic

### 2. Menu System
- **File > Folders**: Directory chooser to select base scan directories
- **File > Deep-Scan**: Full recursive scan of all files
- **File > Quick-Scan**: Incremental scan checking only for changes

### 3. File Interaction
- **Double-click file in table**: Opens file with system default application
- **Context menu**: Additional options (Show in folder, Copy path)

### 4. Scanning Strategies

**Deep Scan:**
- Recursively walk all directories under selected base folders
- Identify all image/video files by extension (jpg, png, gif, mp4, mov, avi, etc.)
- Extract metadata (resolution, creation date) via metadata-extractor
- Calculate SHA-1 hash for each file
- Create/update `album-organizer.ini` in each folder with: filename, lastModified, hash
- Return list of all media files found

**Quick Scan:**
- Only process folders with existing `album-organizer.ini` files
- For each cached file: check if exists + compare lastModified timestamp
- If unchanged: skip hash calculation (use cached hash)
- If changed: recalculate hash + update metadata
- Detect new files not in cache, detect deleted files in cache but missing
- Update INI files with changes only
- Much faster for subsequent scans (only processes changes)

**Cache File Format** (`album-organizer.ini` per directory):
```
# Album Organizer Cache v1.0
# Format: filename,lastModified,hash
photo1.jpg,20230515-143022.000Z,a94a8fe5ccb19ba61c4c0873d391e987982fbbd3
video1.mp4,20230520-184510.000Z,b94a8fe5ccb19ba61c4c0873d391e987982fbbd4
```
- Each line is one file in CSV format: `filename,lastModified,hash`
- Date format: `YYYYMMDD-HHmmSS.sssZ` (compact ISO-8601 with milliseconds in UTC)
- Lines starting with `#` are comments (header line with version)

### 5. Performance Optimizations
- **Parallel hash calculation**: Use ForkJoinPool for CPU-bound hashing
- **Batch processing**: Process files in batches (100 at a time) to manage memory
- **Skip unchanged files**: Quick scan only hashes modified files (compare lastModified)
- **Virtual scrolling**: TableView handles 100,000+ rows efficiently
- **Background tasks**: All scanning runs off UI thread (JavaFX Task)
- **Progress reporting**: Real-time updates via Task.updateProgress/updateMessage

### 6. Cross-Platform Compatibility
- Use `java.nio.file.Path` exclusively (never `java.io.File`)
- Never hardcode path separators - use Path.resolve()
- Store paths with forward slashes in INI, normalize on read
- Handle platform-specific permission errors gracefully
- Package with `jpackage`: MSI/EXE (Windows), DMG/PKG (macOS), DEB/RPM (Linux)

## Implementation Steps

### Phase 1: Project Foundation (Critical Path)
1. Create `pom.xml` with JavaFX, metadata-extractor, commons-io dependencies (no ini4j needed)
2. Establish package structure under `src/main/java/com/albumorganizer/`
3. Create core models:
   - `MediaFile.java` (filename, path, date, type, size, resolution, hash)
   - `CacheEntry.java` (filename, lastModified as `YYYYMMDD-HHmmSS.sssZ` string, hash)
   - `ScanResult.java` (new/modified/deleted files, errors)
   - `DirectoryNode.java` (tree structure for left panel)
4. Create `Constants.java` with:
   - Supported image/video extensions
   - Date format pattern: `DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSSX")` for YYYYMMDD-HHmmSS.sssZ
   - CSV delimiter and comment character
5. Create `DateUtils.java` utility class:
   - `formatToCache(Instant): String` - converts timestamp to YYYYMMDD-HHmmSS.sssZ
   - `parseFromCache(String): Instant` - parses YYYYMMDD-HHmmSS.sssZ to timestamp
6. Add `.gitignore` for Maven/Java/IntelliJ

### Phase 2: Service Layer - Core Logic
7. Implement `DateUtils.java`:
   - Format: `YYYYMMDD-HHmmSS.sssZ` (compact ISO-8601 with milliseconds UTC)
   - `formatToCache(Instant): String` using custom DateTimeFormatter
   - `parseFromCache(String): Instant` with error handling
8. Implement `HashService.java`:
   - `calculateHash(Path): String` using SHA-1 with 8KB buffer
   - Parallel batch processing with ForkJoinPool
9. Implement `FileTypeDetector.java`:
   - Check extension against IMAGE_EXTENSIONS / VIDEO_EXTENSIONS sets
10. Implement `MetadataService.java`:
    - Wrap metadata-extractor library
    - Extract resolution (width x height), creation date
    - Handle corrupted files gracefully (catch exceptions)
11. Implement `CacheService.java`:
    - Read/write `album-organizer.ini` using simple CSV parsing (split on comma)
    - Parse date format using `DateUtils.parseFromCache()`
    - Write date format using `DateUtils.formatToCache()`
    - `readCache(Path dir): Map<String, CacheEntry>`
    - `writeCache(Path dir, Map<String, CacheEntry>)`
    - Handle comment lines (starting with `#`), write header: `# Album Organizer Cache v1.0`
    - Escape commas in filenames if present (use quotes or replacement strategy)
12. Write unit tests for all services (JUnit 5)

### Phase 3: Scanning Implementation
13. Implement `DeepScanStrategy.java`:
    - Walk directories with `Files.walk()`
    - Filter by file type → extract metadata → calculate hash
    - Read existing cache file, compare, update
14. Implement `QuickScanStrategy.java`:
    - Only process folders with existing cache files
    - Compare lastModified, skip unchanged files
    - Detect new/deleted files
15. Implement `ScannerService.java`:
    - Orchestrates scan operations
    - Delegates to DeepScanStrategy or QuickScanStrategy
    - Manages thread pools for parallel processing
16. Create `ScanTask.java` (extends JavaFX Task):
    - Wraps ScannerService in background thread
    - Reports progress: `updateProgress(current, total)`
    - Cancellable: checks `isCancelled()` periodically
17. Integration tests with sample directory structure (max 42 files)

### Phase 4: Basic UI Structure
18. Create `MainView.fxml`:
    - BorderPane with MenuBar, SplitPane, StatusBar
    - Menu items: File > Folders, Deep-Scan, Quick-Scan
19. Implement `MainController.java`:
    - Handle menu actions: onSelectFolders(), onDeepScan(), onQuickScan()
    - Show progress dialog during scans
    - Coordinate between tree and table controllers
20. Create `DirectoryTreeView.fxml` with TreeView component
21. Implement `DirectoryTreeController.java`:
    - Build tree from base folders: `buildTree(List<Path>)`
    - Lazy load children on expand
    - Selection triggers table filtering
22. Test tree rendering with sample folders

### Phase 5: Table View Implementation
23. Create `MediaTableView.fxml` with TableView<MediaFile>
24. Implement `MediaTableController.java`:
    - Define columns: filename, date, type, size, resolution, location, hash
    - Custom cell factories for formatting (size as MB/GB, date as locale-specific)
    - Sortable columns
    - Filter by selected directory from tree
    - **Add double-click handler**: Opens file with `Desktop.getDesktop().open(file)`
    - Context menu: Show in folder, Copy path
25. Wire MainController to tree + table controllers
26. Test with large dataset (10,000+ rows for performance validation)

### Phase 6: Integration & Polish
27. Implement `ConfigRepository.java`:
    - Save/load base folders using Java Preferences API
    - Persist window size/position, last scan date
28. Add progress dialog with cancel button for scans
29. Implement status bar updates (file count, scan duration)
30. Add context menus:
    - Tree: Rescan folder, Open in file browser
    - Table: Show in folder, Copy path (double-click opens file directly)
31. Add duplicate detection: highlight rows with same SHA-1 hash
32. Create `styles.css` for custom styling
33. Add application icon

### Phase 7: Error Handling & Logging
34. Configure logback.xml for file logging (logs to `~/.album-organizer/logs/`)
35. Add comprehensive error handling in scanning (try-catch around file ops)
36. Report permission errors in ScanResult.errors Map
37. Show error dialog for scan failures with details
38. Add retry logic for transient I/O failures

### Phase 8: Testing & Packaging
39. Write integration tests with TestFX (UI automation)
40. Performance test with 100,000 files (measure scan time, memory usage)
41. Cross-platform testing: Windows, macOS, Linux
42. Configure `jpackage` in pom.xml for native installers
43. Build and test installers on all platforms
44. **Documentation alignment**: Ensure README.md, design.md, and plan.md are consistent and cross-reference each other

## Critical Files (in dependency order)

1. **pom.xml** - Maven configuration, all dependencies
2. **src/main/java/com/albumorganizer/model/MediaFile.java** - Core domain model
3. **src/main/java/com/albumorganizer/util/DateUtils.java** - Date formatting utilities
4. **src/main/java/com/albumorganizer/service/HashService.java** - SHA-1 calculation (foundation for caching)
5. **src/main/java/com/albumorganizer/service/CacheService.java** - Cache file read/write (critical for quick scan)
6. **src/main/java/com/albumorganizer/service/ScannerService.java** - Main orchestration
7. **src/main/java/com/albumorganizer/task/ScanTask.java** - Background processing
8. **src/main/java/com/albumorganizer/AlbumOrganizerApp.java** - Application entry point
9. **src/main/resources/com/albumorganizer/view/MainView.fxml** - Root UI layout
10. **src/main/java/com/albumorganizer/controller/MainController.java** - Main UI controller

## Verification Strategy

### Unit Tests
- Test HashService with known file hashes (compare against expected SHA-1)
- Test CacheService with sample cache files in CSV format (read/write round-trip)
- Test date parsing/formatting for `YYYYMMDD-HHmmSS.sssZ` format
- Test MetadataService with sample image/video files (known resolutions)
- Mock file system for isolated tests (using Mockito)
- Limit test files to maximum 42 files per test case

### Integration Tests
- Create test directory with known structure (max 42 files: mix of ~25 images, ~15 videos, ~2 other files to ignore)
- Run deep scan → verify ScanResult contains exactly the image/video files (not the "other" files)
- Modify 3 files (update lastModified)
- Run quick scan → verify exactly 3 files detected as changed
- Verify cache files created with correct CSV format and YYYYMMDD-HHmmSS.sssZ date format

### End-to-End Manual Testing
1. Launch application
2. File > Folders → select test directories (e.g., `~/Pictures`, `~/Videos`)
3. File > Deep-Scan → observe progress dialog, verify completion
4. Check left panel shows directory tree
5. Select folder in tree → verify right panel shows filtered files
6. Verify table columns: filename, date, type, size, resolution, location, hash populated
7. Check cache files created in each scanned folder (CSV format with YYYYMMDD-HHmmSS.sssZ dates)
8. Modify a file externally (touch to update timestamp)
9. File > Quick-Scan → verify modified file detected
10. Double-click a file in the table → verify file opens in default app
11. Right-click file → Show in folder → verify file browser opens to correct location
12. Identify duplicate files (same hash) → verify highlighted in table

### Performance Validation
- Create test directory with 100,000 small image files
- Deep scan should complete in < 10 minutes
- Quick scan (no changes) should complete in < 1 minute
- Memory usage should stay < 1GB
- UI should remain responsive (table scrolling smooth)

### Cross-Platform Validation
- Test on Windows 10/11, macOS (Intel & ARM), Ubuntu 22.04
- Verify file paths work correctly on all platforms
- Verify cache files use forward slashes for paths and YYYYMMDD-HHmmSS.sssZ format for dates
- Test native installers on each platform
- Verify `Desktop.getDesktop().open()` works on all platforms for double-click file opening

## Potential Challenges & Mitigations

**Challenge: Performance with very large directories (millions of files)**
- Mitigation: Parallel processing, skip unchanged files, batch UI updates, virtual scrolling

**Challenge: Hash calculation time (CPU-intensive)**
- Mitigation: Only hash new/modified files in quick scan, parallel processing with ForkJoinPool

**Challenge: Corrupted or permission-denied files**
- Mitigation: Robust error handling, log errors without stopping scan, report in ScanResult

**Challenge: Concurrent access to cache files (multiple scans)**
- Mitigation: File locking, detect external changes, retry logic

**Challenge: Date format parsing edge cases (timezones, milliseconds)**
- Mitigation: Always store UTC times with milliseconds (`.sss`), use DateTimeFormatter with custom pattern

**Challenge: Double-click file opening across platforms**
- Mitigation: Use `Desktop.getDesktop().open()` with platform checks, fallback error messages

**Challenge: JavaFX packaging complexity**
- Mitigation: Use jpackage (built into Java 17+), test early, fallback to runnable JAR

## Success Criteria

- Application launches on Windows, macOS, Linux without modification
- Deep scan correctly identifies all image/video files in selected directories
- Cache files (album-organizer.ini) created with CSV format and YYYYMMDD-HHmmSS.sssZ dates
- Quick scan detects only changed files (significantly faster than deep scan)
- Table displays all required columns with correct formatting
- Double-click file in table opens file with system default application
- UI remains responsive during scans (background threading)
- Duplicate files (same SHA-1 hash) can be identified
- Native installers produced for all three platforms
- Three aligned documentation files: README.md (user guide), design.md (architecture), plan.md (implementation)

## See Also

- [README.md](README.md) - User guide, installation, and troubleshooting
- [design.md](design.md) - Architecture, data models, and technical design
