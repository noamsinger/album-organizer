# Album Organizer - Design Document

## Overview

Album Organizer is a cross-platform desktop application built with JavaFX for scanning, organizing, and AI-enhancing image and video files. It maintains an inventory of media files across multiple directories with duplicate detection, change tracking, and efficient incremental rescanning.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer (JavaFX)                     │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │ MainController  │  │ EnhancementDialog│  │SettingsDlg │ │
│  └────────┬────────┘  └──────────────────┘  └────────────┘ │
└───────────┼─────────────────────────────────────────────────┘
            │
┌───────────┼─────────────────────────────────────────────────┐
│           │              Service Layer                        │
│  ┌────────▼──────┐  ┌─────────────────┐  ┌──────────────┐  │
│  │ScannerService │  │ThumbnailService │  │ArchiveScan   │  │
│  │               │  │                 │  │Service       │  │
│  └───────────────┘  └─────────────────┘  └──────────────┘  │
│  ┌────────────────────┐  ┌─────────────────────────────┐    │
│  │FileOrganizeService │  │ImageEnhancementService      │    │
│  └────────────────────┘  │  + EnhancementProviders     │    │
│                           └─────────────────────────────┘   │
└───────────────────────────────────────────────────────────┘
            │
┌───────────▼─────────────────────────────────────────────────┐
│                     Repository Layer                         │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │SnapshotRepository│         │ConfigRepository  │         │
│  │(cache.json.gz)   │         │(INI file)        │         │
│  └──────────────────┘         └──────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### Package Structure

```
com.albumorganizer/
├── AlbumOrganizerApp.java
│
├── controller/
│   ├── MainController.java          # Orchestrates UI, menu handlers, scan/organize/enhance
│   ├── EnhancementDialog.java       # AI enhancement dialog with checkbox prompt list
│   └── SettingsDialog.java          # Settings dialog (General, Organize, AI Enhancement tabs)
│
├── model/
│   ├── MediaFile.java               # Image/video file with metadata
│   ├── DirectoryNode.java           # Tree node (directory or virtual archive node)
│   ├── AlbumOrganizerSettings.java  # All persisted app settings
│   ├── FileIndexEntry.java          # Lightweight duplicate-index entry
│   ├── ScanResult.java              # Result of a scan operation
│   └── MediaType.java               # IMAGE / VIDEO
│
├── service/
│   ├── ScannerService.java          # Orchestrates scan operations
│   ├── DeepScanStrategy.java        # Full recursive scan with hashing
│   ├── QuickScanStrategy.java       # Fast scan, no hashing
│   ├── DirectoryScanService.java    # On-demand single-directory scan
│   ├── FileOrganizeService.java     # File organization with collision handling
│   ├── ThumbnailService.java        # Thumbnail generation (disk + bytes)
│   ├── ArchiveScanService.java      # ZIP/RAR scanning and entry extraction
│   └── enhancement/
│       ├── ImageEnhancementService.java   # Provider management + resolveOutputPath
│       ├── EnhancementProvider.java       # Provider interface
│       ├── EnhancementRequest.java        # Input: path, prompt, targetSize, outputDir
│       ├── EnhancementResult.java         # Output: path or error message
│       ├── EnhancementConfig.java         # Persisted enhancement settings record
│       ├── NamedPrompt.java               # Title + text prompt pair
│       ├── ImageUtils.java                # loadAsJpeg(), saveAsJpeg()
│       ├── StabilityAIProvider.java       # Stability AI creative upscale
│       ├── OpenAIDalleProvider.java       # OpenAI DALL·E image edit
│       ├── GeminiProvider.java            # Google Gemini image generation
│       ├── GrokProvider.java              # xAI Grok image generation
│       ├── StableDiffusionLocalProvider.java  # AUTOMATIC1111 WebUI img2img
│       ├── ComfyUIProvider.java           # ComfyUI workflow
│       ├── InvokeAIProvider.java          # InvokeAI batch queue
│       └── RealEsrganProvider.java        # Real-ESRGAN ONNX upscaling
│
├── repository/
│   ├── SnapshotRepository.java      # Compressed JSON global cache
│   └── ConfigRepository.java        # INI file read/write for all settings
│
├── task/
│   └── ScanTask.java                # JavaFX Task wrapper for scanning
│
└── util/
    ├── FileTypeDetector.java        # Extension-based type + archive detection
    ├── Constants.java               # IMAGE_EXTENSIONS, VIDEO_EXTENSIONS, ARCHIVE_EXTENSIONS
    ├── DateUtils.java               # Date parsing/formatting
    ├── FormatUtils.java             # Size/date formatting for UI
    ├── ErrorDialog.java             # Standardized error dialogs
    └── FileTrashUtil.java           # Cross-platform trash/recycle
```

## Data Models

### MediaFile

```java
public class MediaFile {
    private String filename;
    private Path absolutePath;        // May be "archive.zip#entry/name" for archive entries
    private Instant lastModified;
    private MediaType type;           // IMAGE or VIDEO
    private long sizeBytes;
    private Dimension resolution;
    private String sha1Hash;
    private String location;
    private Long durationSeconds;
    private Instant dateTaken;        // EXIF date or estimated from filename
    private boolean corrupted;
}
```

### DirectoryNode

```java
public class DirectoryNode {
    private Path path;
    private String displayName;
    private boolean scanned;
    private int mediaFileCount;
    private String archiveType;       // "ZIP", "RAR", or null for real directories
    private Path archivePath;         // Set when this node represents an archive file
    
    public boolean isArchiveNode() { return archiveType != null; }
}
```

### AlbumOrganizerSettings

```java
public class AlbumOrganizerSettings {
    // Organize
    private OrganizeMode mode;            // COPY or MOVE
    private boolean createYearFolder;
    private boolean createMonthFolder;
    private boolean createDayFolder;
    private boolean splitLowRes;
    private boolean splitMedRes;
    private int lowResThresholdPixels;
    private int hiResThresholdPixels;
    // General
    private Path targetFolder;
    private boolean aiOutputToTargetFolder; // true = save to targetFolder/AI-Generated/
    // UI state
    private int fontSizeFactor;
    private String lastSelectedFolder;
    private boolean thumbnailView;
    private boolean showArchivesInTree;
}
```

### EnhancementConfig (record)

```java
public record EnhancementConfig(
    boolean stabilityAiEnabled, String stabilityAiKey,
    boolean openAiEnabled,      String openAiKey,
    boolean geminiEnabled,      String geminiKey,
    boolean grokEnabled,        String grokKey,
    boolean sdLocalEnabled,     String sdLocalUrl,
    boolean realEsrganEnabled,  String realEsrganModelPath,
    boolean comfyUiEnabled,     String comfyUiUrl,
    boolean invokeAiEnabled,    String invokeAiUrl,
    List<NamedPrompt> savedPrompts,
    List<String> checkedPromptTitles   // persisted checkbox selections
) {}
```

### EnhancementRequest (record)

```java
public record EnhancementRequest(
    Path inputPath,
    String prompt,
    Dimension targetSize,
    Path outputDir   // null = next to original; non-null = use this directory
) {}
```

## Archive Support

### Virtual Path Convention

Archive entries use `#` as a separator between the archive file path and the entry name:

```
/Users/alice/Photos/album.zip#2024/vacation/photo1.jpg
```

Detection: `path.toString().contains("#")`  
Splitting: `ArchiveScanService.splitArchivePath(path)` → `["/path/to/album.zip", "2024/vacation/photo1.jpg"]`

### ArchiveScanService

- `scanArchive(Path)` → dispatches to `scanZip` or `scanRar`
- `scanZip(Path)` — uses `java.util.zip.ZipFile`; returns `List<MediaFile>` with virtual paths
- `scanRar(Path)` — uses `org.apache.commons.compress.archivers.ArchiveStreamFactory`
- `extractEntry(Path archivePath, String entryName)` → `byte[]`
- `static boolean isArchiveEntry(Path)` — checks for `#` in path string

### Thumbnail for Archive Entries

`ThumbnailService` has two entry points:
- `generateThumbnail(Path, MediaType)` — normal files
- `generateThumbnailFromBytes(byte[], String cacheKey)` — for archive entry bytes

Cache key for archive entries: `ThumbnailService.archiveEntryCacheKey(archivePath, entryName)`

## AI Enhancement

### Provider Interface

```java
public interface EnhancementProvider {
    String getName();
    String getShortId();        // used in output filename
    boolean isConfigured();
    boolean supportsPrompt();
    EnhancementResult enhance(EnhancementRequest request) throws IOException;
}
```

### Image Preparation

All providers receive images converted to JPEG via `ImageUtils.loadAsJpeg(Path)`:
- Reads any format (HEIC via macOS sips, PNG with alpha stripped)
- Scales to ≤1568px longest edge
- Returns JPEG bytes at quality 0.92 (with fallback)

### Output Path Resolution

`ImageEnhancementService.resolveOutputPath()` has two overloads:

```java
// next to original
static Path resolveOutputPath(Path inputPath, String providerName)

// explicit output directory (creates dir if needed)
static Path resolveOutputPath(Path inputPath, String providerName,
                               boolean useTargetFolder, Path targetFolder) throws IOException
```

Output filename pattern: `{base}_AI-{providerSlug}-{epoch}.jpg`

When `aiOutputToTargetFolder` is enabled, output goes to `targetFolder/AI-Generated/{filename}`.

### Error Handling

Cloud providers parse JSON error bodies to extract `error.message` or equivalent fields, displaying clean messages instead of raw JSON.

## Configuration Persistence

### INI File (`~/.album-organizer/album-organizer-config.ini`)

```ini
[AlbumFolders]
/Users/username/Pictures

[Settings]
targetFolder=/Users/username/Pictures
fontSizeFactor=0
lastSelectedFolder=/Users/username/Pictures/2024
thumbnailView=false
showArchivesInTree=true
aiOutputToTargetFolder=false

[Organize]
mode=COPY
createYearFolder=true
createMonthFolder=true
createDayFolder=true
splitLowRes=true
splitMedRes=true
lowResThresholdPixels=300000
hiResThresholdPixels=1000000

[Enhancement]
stabilityAiEnabled=false
stabilityAiKey=
openAiEnabled=false
openAiKey=
geminiEnabled=false
geminiKey=
grokEnabled=false
grokKey=
sdLocalEnabled=false
sdLocalUrl=http://localhost:7860
realEsrganEnabled=false
realEsrganModelPath=
comfyUiEnabled=false
comfyUiUrl=http://localhost:8188
invokeAiEnabled=false
invokeAiUrl=http://localhost:9090
savedPrompts=Upscale & Sharpen::Upscale and...||(next prompt)
checkedPromptTitles=Upscale & Sharpen||Denoise
```

`savedPrompts` and `checkedPromptTitles` are pipe-separated (`||`); literal `||` in values is escaped as `__PIPE__`.

Window position/size uses Java Preferences API.

## Cache Architecture

**Snapshot Cache** (`~/.album-organizer/cache.json.gz`):
- Single global compressed JSON file
- Stores hash → list of FileIndexEntry (directory, filename, lastModified)
- Loaded on startup for instant duplicate detection
- Enables smart hash recalculation (skip rehash when lastModified unchanged)

## Threading Model

| Thread | Responsibilities |
|--------|-----------------|
| JavaFX Application Thread | All UI updates, event handlers |
| Background Task (ScanTask) | File system walking, metadata extraction, hashing |
| Thumbnail Thread Pool | Async thumbnail generation (N = CPU cores) |
| Enhancement Task | AI HTTP requests (one per enhancement) |

Communication to the UI thread uses `Platform.runLater()`.

## Technology Stack

| Library | Version | Purpose |
|---------|---------|---------|
| JavaFX | 21 | UI framework |
| metadata-extractor | 2.19.0 | EXIF/metadata extraction |
| commons-compress | 1.26.1 | ZIP/RAR archive reading |
| commons-io | 2.15.1 | File utilities |
| commons-codec | 1.16.0 | Hex encoding |
| gson | 2.x | JSON serialization |
| slf4j + logback | 2.0.9 / 1.4.11 | Logging |
| JUnit 5 | 5.10.1 | Testing |

## Security Considerations

- **Local only**: no network communication except to configured AI provider APIs
- **Read-only scanning**: application never modifies source files (except trash/organize)
- **Cache content**: filenames, timestamps, hashes only — no image data
- **API keys**: stored in INI file in plain text; no in-app encryption

## See Also

- [README.md](README.md) — User guide and installation
- [CHANGELOG.md](CHANGELOG.md) — Version history
