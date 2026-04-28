# Album Organizer - Design Document

## Overview

Album Organizer is a cross-platform desktop application built with JavaFX for scanning, organizing, and managing image and video files. It maintains an inventory of media files across multiple directories with duplicate detection, change tracking, and efficient incremental rescanning.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer (JavaFX)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │MainController│  │DirectoryTree │  │MediaTable        │  │
│  │              │  │Controller    │  │Controller        │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────────┘  │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
┌─────────┼──────────────────┼──────────────────┼─────────────┐
│         │         Service Layer                │             │
│  ┌──────▼─────────┐  ┌──────────────┐  ┌─────▼──────────┐  │
│  │ScannerService  │  │HashService   │  │MetadataService │  │
│  │                │  │              │  │                │  │
│  └────────┬───────┘  └──────────────┘  └────────────────┘  │
│           │                                                  │
│  ┌────────▼───────────────────────────┐                     │
│  │  DeepScanStrategy  QuickScanStrategy│                     │
│  └────────────────────────────────────┘                     │
└─────────┼──────────────────────────────────────────────────┘
          │
┌─────────▼──────────────────────────────────────────────────┐
│              Repository Layer                               │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │CacheService      │         │ConfigRepository  │         │
│  │(album-org.ini)   │         │(Java Preferences)│         │
│  └──────────────────┘         └──────────────────┘         │
└────────────────────────────────────────────────────────────┘
```

### Package Structure

```
com.albumorganizer/
├── AlbumOrganizerApp.java          # JavaFX Application entry point
│
├── controller/                      # UI Controllers (FXML)
│   ├── MainController.java         # Orchestrates UI components, menu handlers
│   ├── DirectoryTreeController.java# Manages left panel tree view
│   └── MediaTableController.java   # Manages right panel table view
│
├── model/                          # Domain Models
│   ├── MediaFile.java              # Represents image/video file with metadata
│   ├── CacheEntry.java             # (Deprecated - no longer used)
│   ├── FileIndexEntry.java         # Index entry with directory, filename, lastModified
│   ├── ScanResult.java             # Result object from scan operations
│   ├── DirectoryNode.java          # Tree structure for directory hierarchy
│   └── MediaType.java              # Enum: IMAGE, VIDEO
│
├── service/                        # Business Logic
│   ├── ScannerService.java         # Orchestrates scanning operations
│   ├── DeepScanStrategy.java       # Full recursive scan implementation
│   ├── QuickScanStrategy.java      # Incremental change detection
│   ├── HashService.java            # SHA-1 hash calculation (parallel)
│   ├── MetadataService.java        # Extracts image/video metadata
│   ├── CacheService.java           # (Deprecated - no longer used)
│   ├── DirectoryScanService.java   # On-demand directory scanning
│   └── FileOrganizeService.java    # File organization operations
│
├── repository/                     # Data Persistence
│   ├── SnapshotRepository.java     # Global snapshot cache (cache.json.gz)
│   └── ConfigRepository.java       # Application configuration (Preferences API)
│
├── task/                          # Background Tasks
│   ├── ScanTask.java              # JavaFX Task wrapper for scanning
│   └── HashCalculationTask.java   # Parallel hash calculation task
│
└── util/                          # Utilities
    ├── FileTypeDetector.java      # Checks file extensions
    ├── DateUtils.java             # Date parsing/formatting (YYYYMMDD-HHmmSS.sssZ)
    ├── FormatUtils.java           # Size/date formatting for UI
    └── Constants.java             # Application constants
```

## Data Models

### MediaFile
Core domain object representing a scanned image or video file.

```java
public class MediaFile {
    private String filename;              // e.g., "photo1.jpg"
    private Path absolutePath;            // Full path to file
    private Path relativePath;            // Relative to base scan folder
    private Instant lastModified;         // File modification timestamp
    private MediaType type;               // IMAGE or VIDEO
    private long sizeBytes;               // File size in bytes
    private Dimension resolution;         // width x height (null if unavailable)
    private String sha1Hash;              // 40-character hex string
    private String location;              // Directory path (for table display)
    private Map<String, Object> metadata; // Extended metadata (EXIF, etc.)
    private Long durationSeconds;         // Video duration in seconds
    private Instant dateTaken;            // Original date/time photo was taken (from EXIF)
    private boolean corrupted;            // True if file has invalid/corrupted content
}
```

### FileIndexEntry
Lightweight entry in the global duplicate detection index with memory optimization.

```java
public class FileIndexEntry {
    private final Path directory;      // Shared reference for all files in same directory
    private final String filename;
    private final Instant lastModified; // File modification timestamp
    
    public Path getAbsolutePath() {
        return directory.resolve(filename);  // Computed on-demand
    }
    
    public Instant getLastModified() {
        return lastModified;
    }
}
```

**Memory Optimization**: Directory Path objects are shared among all files in the same directory, significantly reducing memory usage for large collections (22% savings for 1M files).

### ScanResult
Returned by scanning operations to report what changed.

```java
public class ScanResult {
    private List<MediaFile> newFiles;        // Files not in cache
    private List<MediaFile> modifiedFiles;   // Files with changed lastModified
    private List<String> deletedFiles;       // Cached files not found on disk
    private int totalScanned;                // Total files processed
    private Duration scanDuration;           // Time taken
    private Map<Path, Exception> errors;     // Errors encountered (path -> exception)
}
```

### DirectoryNode
Tree structure for the left panel directory tree.

```java
public class DirectoryNode {
    private Path path;                    // Directory path
    private String displayName;           // Display name (folder name)
    private List<DirectoryNode> children; // Child directories
    private boolean scanned;              // Has this folder been scanned?
    private int mediaFileCount;           // Number of media files in this dir
}
```

## Cache Architecture

### Global Snapshot Cache

**File**: `~/.album-organizer/cache.json.gz`

The application uses a single global snapshot cache that stores all file metadata in compressed JSON format.

**Structure**:
```json
{
  "timestamp": "2026-04-24T08:30:00Z",
  "fileIndex": {
    "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3": [
      {
        "directory": "/Users/username/Pictures/2023",
        "filename": "photo1.jpg",
        "lastModified": "2023-05-15T14:30:22.000Z"
      }
    ]
  }
}
```

**FileIndexEntry** stores:
1. **directory**: Path object (shared reference for memory optimization)
2. **filename**: File name only (not full path)
3. **lastModified**: File modification timestamp (Instant)

**Features**:
- Compressed with GZIP for efficient storage
- Custom Gson TypeAdapters for Path and Instant serialization
- Loaded on startup for instant UI population
- Automatically verified against actual files
- Memory-optimized: shared directory Path objects reduce memory usage
- Single source of truth - no per-directory cache files

**Memory Optimization**:
- Directory Path is shared among all FileIndexEntry objects in the same directory
- Absolute path computed on-demand via `directory.resolve(filename)`
- For 1M files: ~310 MB memory (22% reduction vs previous implementation)

## Scanning Algorithms

### Deep Scan Strategy

**Purpose**: Complete scan of all files, populating the global file index.

**Algorithm**:
```
For each base folder:
  1. Recursively walk directory tree (Files.walk)
  2. For each file:
     a. Check extension against whitelist (IMAGE_EXTENSIONS, VIDEO_EXTENSIONS)
     b. If match:
        - Extract metadata (resolution, dates) via metadata-extractor
        - Calculate SHA-1 hash (parallel via ForkJoinPool)
        - Create MediaFile object with lastModified timestamp
  3. Return ScanResult with all discovered files
```

**Performance Optimizations**:
- Parallel hash calculation (ForkJoinPool with N cores)
- Batch processing (100 files at a time)
- Stream-based directory walking (avoid loading all paths in memory)

### Quick Scan Strategy

**Purpose**: Detect changes since last scan.

**Current Implementation**:
```
For each base folder:
  1. Recursively walk directory tree (Files.walk)
  2. For each file:
     - Extract metadata and calculate hash
     - Create MediaFile object
  3. Return ScanResult
```

**Note**: Quick Scan currently performs a full scan similar to Deep Scan. Future optimization will use the lastModified field from FileIndexEntry in the global snapshot cache to skip re-hashing unchanged files.

**Future Optimization Plan**:
- Compare file's current lastModified against cached lastModified
- If timestamps match: reuse cached hash (skip hashing)
- If timestamps differ: re-hash file
- Significantly faster for large collections with few changes

### Comparison

| Aspect | Deep Scan | Quick Scan (Current) | Quick Scan (Future) |
|--------|-----------|---------------------|---------------------|
| **Speed** | Slow (hashes every file) | Slow (hashes every file) | Fast (only changed files) |
| **Use Case** | First scan, major changes | Currently same as Deep | Regular re-scans |
| **Requires Cache** | No | No | Yes (uses snapshot) |
| **Scans New Directories** | Yes | Yes | Yes |
| **Detects New Files** | Yes | Yes | Yes |
| **Hash Calculation** | All files | All files | Only new/modified |
| **Cache Storage** | Global snapshot | Global snapshot | Global snapshot |

## UI Component Design

### Main Window Layout

```
┌─────────────────────────────────────────────────────────┐
│  File   Edit   Help                                     │ MenuBar
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┬──────────────────────────────────────┐│
│  │              │  Filename | Date | Type | Size | ... ││
│  │  📁 Pictures │  photo1.jpg | ... | IMAGE | 2.3 MB  ││
│  │    📁 2023   │  photo2.jpg | ... | IMAGE | 1.8 MB  ││
│  │    📁 2024   │  video1.mp4 | ... | VIDEO | 45.2 MB ││ SplitPane
│  │  📁 Videos   │  ...                                  ││
│  │    📁 Trips  │                                       ││
│  │              │                                       ││
│  │              │                                       ││
│  │  Directory   │         File Table                   ││
│  │  Tree        │                                       ││
│  └──────────────┴──────────────────────────────────────┘│
│                                                          │
│  Scanned 1,234 files | 2 duplicates | 0 errors          │ StatusBar
└─────────────────────────────────────────────────────────┘
```

### Directory Tree (Left Panel)

**Component**: `TreeView<DirectoryNode>`

**Features**:
- Lazy loading: children loaded on expand
- Icons: folder (scanned vs unscanned)
- Selection triggers table filtering
- Context menu: Rescan folder, Open in file browser

**Controller**: `DirectoryTreeController.java`

### File Table (Right Panel)

**Component**: `TableView<MediaFile>`

**Columns**:
1. **Filename** - String, left-aligned
2. **Date** - Instant, formatted as locale-specific date
3. **Type** - IMAGE or VIDEO
4. **Size** - Long, formatted as KB/MB/GB
5. **Resolution** - Dimension, formatted as "1920x1080"
6. **Location** - Path, full directory path
7. **Hash** - String, SHA-1 hash (first 12 chars + ellipsis for display)

**Features**:
- Sortable columns (click header)
- Custom cell factories for formatting
- Virtual scrolling (handles 100,000+ rows)
- Double-click row: Opens file with `Desktop.getDesktop().open(file)`
- Context menu: Show in folder, Copy path
- Duplicate highlighting (same hash = same color)

**Controller**: `MediaTableController.java`

### Menu Bar

**File Menu**:
- **Folders...** - Opens DirectoryChooser to select base folders
- **Deep-Scan** - Launches full scan (ScanTask)
- **Quick-Scan** - Launches incremental scan (ScanTask)
- **Exit** - Closes application

## Technology Stack

### Core Technologies

- **Java 17+**: LTS version with modern features
- **JavaFX 21.0.1**: Cross-platform UI framework
- **Maven**: Build system and dependency management

### Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| metadata-extractor | 2.19.0 | Extract EXIF/metadata from images/videos |
| commons-io | 2.15.1 | File utilities, directory walking |
| commons-codec | 1.16.0 | Hex encoding for hash strings |
| guava | 32.1.3 | Collections, caching utilities |
| slf4j-api | 2.0.9 | Logging facade |
| logback-classic | 1.4.11 | Logging implementation |
| junit-jupiter | 5.10.1 | Unit testing |
| testfx | 4.0.18 | JavaFX UI testing |
| mockito | 5.7.0 | Mocking framework |

### Why Maven over Gradle?

- **Better JavaFX Support**: Mature JavaFX Maven Plugin
- **Simpler Configuration**: Declarative XML for standard projects
- **Cross-Platform Packaging**: Proven plugins for native installers
- **Stability**: Convention-over-configuration works well here

## Threading Model

### UI Thread (JavaFX Application Thread)
- All UI updates must run on this thread
- Menu handlers, event listeners
- Table/tree updates
- Progress dialog display

### Background Threads (ScanTask)
- File system operations (walking directories)
- Hash calculation
- Metadata extraction
- Cache file I/O

**Communication**: JavaFX `Task` class
- `updateProgress(current, total)` - updates progress bar
- `updateMessage(String)` - updates status text
- `Platform.runLater()` - posts UI updates to JavaFX thread

### Parallel Processing (ForkJoinPool)
- Hash calculation (CPU-bound)
- Parallelism = `Runtime.getRuntime().availableProcessors()`
- Batched by directory (process 100 files per batch)

## Cross-Platform Considerations

### File Paths
- **Use**: `java.nio.file.Path` exclusively
- **Avoid**: `java.io.File` (deprecated pattern)
- **Never hardcode**: `/` or `\` separators
- **Use**: `Path.resolve()` for path joining

### Cache File Paths
- Store paths with forward slashes: `folder/subfolder/file.jpg`
- Convert to platform-specific on read: `Path.of(storedPath)`

### File Operations
- Wrap in try-catch for `AccessDeniedException`
- Report permission errors in `ScanResult.errors`
- Continue scanning other files on error

### Desktop Integration
- `Desktop.getDesktop().open(file)` - opens file with default app
- Check `Desktop.isDesktopSupported()` before use
- Handle `UnsupportedOperationException` gracefully

### Native Packaging (jpackage)
- **Windows**: MSI/EXE installers
- **macOS**: DMG/PKG with app bundle
- **Linux**: DEB/RPM packages

## Performance Characteristics

### Expected Performance

| Collection Size | Deep Scan | Quick Scan (no changes) |
|-----------------|-----------|-------------------------|
| 1,000 files | < 30 sec | < 5 sec |
| 10,000 files | < 5 min | < 30 sec |
| 100,000 files | < 10 min | < 1 min |

**Assumptions**:
- SSD storage
- Quad-core CPU
- Average file size 2-5 MB

### Memory Usage

- **Base**: ~100 MB (JavaFX + app overhead)
- **Per 1,000,000 files**: ~310 MB (optimized HashMap index with shared directory paths)
- **Memory Optimization**: 22% reduction vs list-based approach through directory path sharing

**Memory-Optimized Index Structure**:
```java
// Global duplicate index: HashMap<SHA-1 hash, List<FileIndexEntry>>
private Map<String, List<FileIndexEntry>> fileIndex;

// FileIndexEntry uses shared directory Path objects
public class FileIndexEntry {
    private final Path directory;  // Shared reference for all files in same directory
    private final String filename;
    
    public Path getAbsolutePath() {
        return directory.resolve(filename);  // Computed on-demand
    }
}
```

**Benefits**:
- O(1) duplicate lookup via HashMap key
- Directory paths shared across all files in same directory
- Reduces memory duplication for large collections
- Expected memory: ~310 MB for 1M files (vs ~398 MB with list-based approach)

**Memory Management**:
- Virtual scrolling (TableView loads visible rows only)
- Batch processing (avoids loading all files in memory)
- Shared Path objects to reduce memory duplication

## Error Handling Strategy

### File Access Errors
- **Catch**: `IOException`, `AccessDeniedException`, `NoSuchFileException`
- **Action**: Log error, add to `ScanResult.errors`, continue scanning
- **UI**: Show error count in status bar, provide error details dialog

### Corrupted Files
- **Catch**: Metadata extraction failures, hash calculation errors
- **Action**: Log warning, skip metadata, continue with basic info
- **UI**: Mark in table with warning icon (optional enhancement)

### Hash Collisions
- **Probability**: SHA-1 collision extremely rare for user data
- **Handling**: Accept as duplicate (no special handling needed)

### Out of Memory
- **Prevention**: Batch processing, virtual scrolling
- **Detection**: Catch `OutOfMemoryError` (last resort)
- **Action**: Show error dialog, suggest reducing scope

## Recent Feature Additions

### Album Folder Management (v1.1)
- **Renamed from "Anchor Folders"**: More intuitive terminology
- **Multiple Album Folders**: Users can add multiple base folders to scan
- **Target Folder**: One album folder can be designated as the "target" (shown in purple)
- **Folder Validation**: Prevents adding overlapping folders (parent/child conflicts)
- **Visual Indicators**:
  - Album folders: Bold text in directory tree
  - Target folder: Purple text + bold in directory tree
- **Context Menu Actions**:
  - Make Target Folder
  - Unset as Target Folder
  - Remove from Album Folders

### Enhanced UI Features (v1.1)

#### Thumbnail View
- **View Modes**: List View (table) and Thumbnail View (grid)
- **Switch via**: View menu or keyboard shortcuts
- **Thumbnails**: Visual cards with file preview and metadata
- **Lazy Loading**: Thumbnails loaded asynchronously for performance

#### Font Size Control
- **Adjustable Font Size**: Scales entire UI including all dialogs
- **Formula**: `100% * 2^(factor/4)`
- **Keyboard Shortcuts**:
  - Cmd + (or Cmd =): Increase font size
  - Cmd -: Decrease font size
  - Cmd 0: Reset to 100%
- **Persistence**: Font size factor stored in INI file
- **Scope**: Applies to all dialogs (Settings, About, Warnings, Errors, Confirmations)
- **Dialog Enhancements**:
  - All dialogs are resizable (`setResizable(true)`)
  - Font size calculated and applied: `fontScale = Math.pow(2.0, factor / 4.0)`
  - Minimum widths scaled proportionally
  - Content widths and heights scaled with fontScale multiplier

#### Settings Dialog
- **Access**: File > Settings...
- **Organize Mode**: Copy or Move (disabled for now)
- **Folder Structure Options**:
  - Create Year Folder (cascades to Month)
  - Create Month Folder (cascades to Day)
  - Create Day Folder
- **Resolution Splitting**:
  - Combined checkbox for lowres/medres/hires splitting
  - Customizable pixel thresholds (default: 300,000 and 1,000,000 pixels)
  - Fields disabled when splitting is off
- **Font Scaling**: Dialog respects current font size setting

### Enhanced Metadata Extraction (v1.1)

#### Video Metadata
- **Date Taken**: Extracts EXIF date from photos/videos
- **Duration**: Shows video length for MP4/MOV/AVI files
- **Resolution**: Enhanced support for MP4 (Mp4VideoDirectory) and MOV (QuickTimeVideoDirectory)

#### PNG Fallback Reader
- **Problem**: Some PNG files lack standard metadata
- **Solution**: Direct PNG header reading
- **Method**: Reads width/height from PNG IHDR chunk (bytes 16-23)
- **Result**: Guaranteed resolution for all valid PNG files

#### File Validation
- **Corrupted File Detection**: Uses metadata-extractor validation
- **Visual Indicator**: Corrupted files shown in dark red
- **User Benefit**: Easy identification of problematic files

### Enhanced Context Menus (v1.1)

#### Table Context Menu
- **Copy Filename**: Replaces "Copy Hash" (more useful)
- **Show in Folder**: Opens file manager to file location
- **Move to Trash**: Platform-aware trash/recycle operation

#### Tree Context Menu
- **Deep Scan**: Full recursive scan of selected folder
- **Quick Scan**: Incremental scan of selected folder
- **Open in File Browser**: Opens folder in system file manager
- **Make Target Folder**: Sets selected album folder as target
- **Unset as Target Folder**: Clears target folder designation
- **Remove from Album Folders**: Removes album folder from scan list

### Configuration Persistence (v1.1)

#### INI File Structure
All configuration now stored in `~/.album-organizer/album-organizer-config.ini`:

```ini
[AlbumFolders]
/Users/username/Pictures
/Users/username/Videos

[Settings]
targetFolder=/Users/username/Pictures
fontSizeFactor=2

[Organize]
mode=COPY
createYearFolder=true
createMonthFolder=true
createDayFolder=true
splitLowRes=true
splitMedRes=true
lowResThresholdPixels=300000
hiResThresholdPixels=1000000
```

#### Migration from Java Preferences
- **Before**: Target folder and organize settings in Java Preferences API
- **After**: All settings in INI file for better portability
- **Window Position/Size**: Still uses Java Preferences (platform-specific)

### UI Improvements (v1.1)

#### Directory Tree
- **Case-Insensitive Sorting**: All levels sorted alphabetically (case-insensitive)
- **Recursive Sorting**: Applied to all tree levels automatically
- **Visual Hierarchy**: Album folders and target folder clearly distinguished

#### Duplicate Detection & File Highlighting
- **Visual Highlighting**:
  - **Duplicate files**: Yellow background (#fff9c4) for files with same SHA-1 hash
  - **Corrupted files**: Dark red text (#8B0000) for files that fail metadata validation
- **Based on**: SHA-1 hash comparison across entire index
- **Performance**: O(1) duplicate lookup via HashMap
- **Context Menu**: "Remove Other Duplicates" option to keep selected file and move others to trash
- **Implementation**: 
  - Row factory set once during initialization
  - Checks fileIndex in real-time during row updates
  - CSS classes applied with `!important` flags for proper styling precedence

### Model Updates (v1.1)

#### DirectoryNode
```java
public class DirectoryNode {
    private boolean album;  // Renamed from 'anchor'
    // ... (isAlbum(), setAlbum() methods)
}
```

#### OrganizeSettings
```java
public class OrganizeSettings {
    private int lowResThresholdPixels;   // Changed from width/height
    private int hiResThresholdPixels;    // Simplified to total pixels
    // ...
}
```

#### MediaFile
```java
public class MediaFile {
    private Instant dateTaken;          // New: EXIF date
    private Long durationSeconds;       // New: Video duration
    private boolean corrupted;          // New: Validation flag
    // ...
}
```

## Security Considerations

### Hash Algorithm
- **Current**: SHA-1 (fast, sufficient for duplicate detection)
- **Not for**: Cryptographic verification
- **Future**: Could add SHA-256 option for paranoid users

### File Access
- **Read-only**: Application only reads media files, never modifies them
- **Cache storage**: Global snapshot cache stored in `~/.album-organizer/cache.json.gz`
- **Permissions**: Respects file system permissions

### Privacy
- **Local only**: No network communication, no telemetry
- **User data**: All data stays on user's machine
- **Cache content**: Contains filenames, timestamps, hashes (no image data)
- **No clutter**: No hidden files created in user directories

## Future Enhancements

### Planned Features
1. **File Organization**: Implement the organize feature using configured settings
2. **Database backend**: Replace INI files with SQLite for better performance
3. **Advanced filters**: Search by metadata, date range, resolution
4. **Export results**: CSV, JSON export
5. **Watch service**: Auto-detect file system changes
6. **Batch operations**: Additional bulk operations (copy, rename)
7. **Cloud integration**: Scan cloud storage (optional)

### Performance Improvements
1. **Incremental hashing**: Resume interrupted hash calculations
2. **Smart caching**: Metadata cache with TTL
3. **GPU acceleration**: Thumbnail generation
4. **Distributed scanning**: Multi-machine support (advanced)

## References

- **JavaFX Documentation**: https://openjfx.io/
- **metadata-extractor**: https://github.com/drewnoakes/metadata-extractor
- **Maven JavaFX Plugin**: https://github.com/openjfx/javafx-maven-plugin
- **Java NIO.2**: https://docs.oracle.com/javase/tutorial/essential/io/fileio.html
- **jpackage Guide**: https://docs.oracle.com/en/java/javase/17/jpackage/

## See Also

- [README.md](README.md) - User guide and installation
- [plan.md](plan.md) - Implementation plan and phases

## Recent Updates (2026)

### Cache Architecture Simplification (April 2026)
Eliminated per-directory cache files in favor of a single global snapshot cache:
- **Removed**: `.album-organizer.ini` files that were created in each scanned directory
- **Enhanced FileIndexEntry**: Added `lastModified` field to support future Quick Scan optimization
- **Single source of truth**: All file metadata now stored exclusively in `~/.album-organizer/cache.json.gz`
- **Simplified codebase**: Removed CacheService usage from scan strategies
- **No filesystem clutter**: Application no longer creates hidden files in user directories
- **Future optimization**: Quick Scan will use lastModified comparison to skip re-hashing unchanged files
- **Current behavior**: Both Deep Scan and Quick Scan perform full scans (Quick Scan optimization pending)

### File Organization System
Added complete file organization feature that copies or moves files to a target folder with automatic folder structure:
- **Target folder structure**: `YYYY/MM/DD/[resolution-folder]/filename`
- **Date-based hierarchy**: Year, Month, Day folders based on date taken (EXIF or estimated)
- **Resolution-based splitting**: `low-res`, `med-res` subfolders for images below thresholds
- **Collision handling**: Hash verification with automatic filename suffixing if content differs
- **Move integrity**: Copy → Verify hash → Delete original (guarantees no data loss)
- **Organize modes**:
  - Single file: Right-click file → "Organize File"
  - Recursive: Right-click folder → "Organize Folder Recursively"

### Date Estimation
Files without EXIF dates now get estimated dates from filenames:
- **Patterns supported**: `YYYYMMDD_HHmmSS`, `YYYYMMDDHHmmSS`, `YYYY-MM-DD`, `YYYYMMDD`
- **Validation**: Years 1800-2099, calendar-valid dates (including leap years)
- **EXIF validation**: EXIF dates also validated for year range
- **UI indication**: Estimated dates prefixed with "=>" in Date Taken column
- **UTC format**: All dates shown as `YYYY/MM/DD-HH:mm:SSZ`

### Progress Panel (UI Redesign)
Renamed "Scan Panel" to "Progress Panel" for multi-purpose use:
- **Dual purpose**: Handles both scanning and organizing operations
- **Real-time updates**: Shows discovered/processed files with scrolling list
- **Progress tracking**: Visual progress bar with status text
- **Cancellation**: Stop button to cancel operations mid-process
- **Collapsible**: Hide/show toggle in View menu
- **Component names**: All `scan*` variables renamed to `progress*`

### Enhanced File Index
Improved memory efficiency and functionality:
- **Recursive counts**: Directory nodes track both direct and recursive file counts
- **Display format**: Folders show "Name (direct/recursive)" e.g., "Photos (5/247)"
- **Snapshot cache**: Compressed JSON cache (`cache.json.gz`) for fast startup
- **Custom TypeAdapters**: Gson serialization for `Instant` and `Path` types
- **Auto-validation**: Quick scan after loading snapshot ensures accuracy

### Visual Improvements
Enhanced color scheme and file indicators:
- **Corrupted files**: Pale red background (not just text color)
- **Selected files**: Dark blue background with color-coded foreground:
  - White: normal files
  - Strong yellow: duplicates  
  - Pink: corrupted files
- **Hover removed**: Simplified selection visual feedback
- **Folder counts**: Always visible, showing direct/recursive file counts

### Context Menu Enhancements
- **Organize File**: Organizes single file to target folder (requires target folder set)
- **Organize Folder Recursively**: Organizes all files in folder tree with confirmation
- **Delete File**: Moves file to system trash
- **Remove Other Duplicates**: Now uses checkboxes for selective deletion
- **Visibility logic**: Menu items show/hide based on context (target folder, file count, etc.)

### Settings Dialog Improvements
- **Cascading checkboxes**: Month requires Year, Day requires Month
- **Validation**: Prevents saving invalid threshold values with modal error dialog
- **Updated labels**: "Organize with" instead of "Organize create"
- **Clear messaging**: "Split to low resolution folders (low-res/med-res)"

### Cache System Updates (April 2026)
- **Eliminated per-directory cache files**: Removed `.album-organizer.ini` files entirely
- **Single global cache**: All metadata stored in `~/.album-organizer/cache.json.gz`
- **FileIndexEntry enhancement**: Added `lastModified` field to support future Quick Scan optimization
- **Simplified architecture**: One cache system instead of dual (per-directory + snapshot)
- **No filesystem clutter**: Application no longer creates hidden files in user directories
- **GZIP compression**: Efficient storage with compressed JSON
- **Integrity**: Explicit `flush()` and `finish()` calls to prevent truncation

### New Service Classes
- **FileOrganizeService**: Handles file organization logic with collision detection
- **DirectoryScanService**: Non-recursive directory scanning for on-demand viewing
- **SnapshotRepository**: Manages compressed JSON snapshot cache
- **DateEstimator**: Extracts and validates dates from filename patterns
- **FileTrashUtil**: Cross-platform file deletion to system trash

### Architecture Notes
- **Separation of concerns**: Organization logic separate from scanning
- **Cancellation support**: Background tasks check `isCancelled()` regularly
- **Error handling**: Continues on errors, reports at end for batch operations
- **Auto-refresh**: Views update automatically after organizing
- **Validation layers**: Date validation in both estimation and EXIF extraction

