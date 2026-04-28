# In-Memory Data Structures

This document explains how Album Organizer stores and manages files and directories in memory.

## Overview

Album Organizer uses a dual-structure approach:
1. **fileIndex** - Global hash map for duplicate detection and file tracking
2. **directoryTree** - JavaFX TreeView for UI navigation

## 1. File Index (`fileIndex`)

### Structure
```java
Map<String, List<FileIndexEntry>> fileIndex
```

### Key Design
- **Type**: `HashMap<String, List<FileIndexEntry>>`
- **Key**: SHA-1 hash string (40 characters) OR special `"__NO_HASH__"` constant
- **Value**: List of `FileIndexEntry` objects (all files with that hash)

### Purpose
- **Primary**: Duplicate detection across all scanned files
- **Secondary**: File tracking with modification dates
- **Tertiary**: Building directory tree with file counts

### FileIndexEntry Class
```java
public class FileIndexEntry {
    private final Path directory;        // Shared reference!
    private final String filename;       // Just the name
    private final Instant lastModified;  // For change detection
}
```

**Memory Optimization**: The `directory` Path object is shared among all files in the same directory, dramatically reducing memory usage.

### Example in Memory
```
fileIndex = {
    "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3" -> [
        FileIndexEntry(/Users/bob/Pictures, "photo1.jpg", 2024-01-15T10:30:00Z),
        FileIndexEntry(/Users/bob/Backup, "photo1_copy.jpg", 2024-01-15T10:30:00Z)
    ],
    "b83d2a99fb19ba61c4c0873d391e987982fbcc7" -> [
        FileIndexEntry(/Users/bob/Pictures, "photo2.jpg", 2024-02-20T14:22:00Z)
    ],
    "__NO_HASH__" -> [
        FileIndexEntry(/Users/bob/Downloads, "new_image.jpg", 2024-04-25T08:15:00Z),
        FileIndexEntry(/Users/bob/Downloads, "temp.png", 2024-04-26T11:00:00Z)
    ]
}
```

### Special Key: `NO_HASH_KEY`
- **Value**: `"__NO_HASH__"`
- **Purpose**: Placeholder for files scanned without hash calculation (Quick Scan)
- **Behavior**:
  - Files under this key appear in directory tree and file counts
  - NOT included in duplicate detection (need hash to compare)
  - Replaced with actual hash on Full Scan with Hash

### Operations

#### Adding Files (After Scan)
```java
// 1. Build existing hash map (for preservation)
Map<Path, String> existingHashes = new HashMap<>();
fileIndex.forEach((hash, entries) -> {
    if (!hash.equals(NO_HASH_KEY)) {
        entries.forEach(entry -> 
            existingHashes.put(entry.getAbsolutePath(), hash)
        );
    }
});

// 2. Remove old entries from scanned folders
for (Path scannedFolder : currentlyScannedFolders) {
    fileIndex.values().forEach(entries ->
        entries.removeIf(entry -> 
            entry.getDirectory().startsWith(scannedFolder)
        )
    );
}

// 3. Add new files (preserving hashes from existingHashes map)
for (MediaFile file : scanResult.getAllFiles()) {
    String hash = file.getSha1Hash();
    
    // Preserve existing hash if quick scan
    if ((hash == null || hash.isEmpty()) && existingHashes.containsKey(fullPath)) {
        hash = existingHashes.get(fullPath);
    }
    
    String indexKey = (hash != null && !hash.isEmpty()) ? hash : NO_HASH_KEY;
    fileIndex.computeIfAbsent(indexKey, k -> new ArrayList<>())
             .add(new FileIndexEntry(directory, filename, lastModified));
}
```

#### Finding Duplicates
```java
// Get all entries for a hash (excluding NO_HASH_KEY)
String hash = mediaFile.getSha1Hash();
if (hash != null && !hash.isEmpty() && !hash.equals(NO_HASH_KEY)) {
    List<FileIndexEntry> entries = fileIndex.get(hash);
    boolean isDuplicate = entries != null && entries.size() > 1;
}
```

#### Building Directory Tree
```java
// Extract all unique directories from all entries
Set<Path> dirsWithFiles = fileIndex.values().stream()
    .flatMap(List::stream)
    .map(FileIndexEntry::getDirectory)
    .filter(dir -> dir.startsWith(basePath))
    .collect(Collectors.toSet());

// Count files per directory
long fileCount = fileIndex.values().stream()
    .flatMap(List::stream)
    .filter(entry -> entry.getDirectory().equals(dir))
    .count();
```

### Memory Usage Estimation

For **50,000 files** in **1,000 unique directories**:

**FileIndexEntry per file:**
- Path directory: 8 bytes (reference to shared object)
- String filename: ~70 bytes (30 chars avg)
- Instant lastModified: 24 bytes
- Object overhead: 16 bytes
- **Total per entry**: ~118 bytes

**Hash strings (5,000 unique hashes):**
- String object: ~80 bytes per hash
- **Total**: 5,000 × 80 = 400 KB

**Shared Path objects (1,000 directories):**
- Path object: ~150 bytes each
- **Total**: 1,000 × 150 = 150 KB

**FileIndexEntry objects:**
- 50,000 × 118 = 5.9 MB

**HashMap overhead:**
- ~200 KB (buckets, etc.)

**Grand Total**: ~6.65 MB for 50,000 files

## 2. Directory Tree (`directoryTree`)

### Structure
```java
TreeView<DirectoryNode> directoryTree
```

### Purpose
- **Primary**: UI navigation and folder selection
- **Secondary**: Display file counts per directory
- **Tertiary**: Organize and target folder management

### DirectoryNode Class
```java
public class DirectoryNode {
    private Path path;                      // Full path to directory
    private String displayName;             // Name shown in UI
    private List<DirectoryNode> children;   // Child directories (not used in TreeItem)
    private boolean scanned;                // Has this folder been scanned?
    private int mediaFileCount;             // Direct files in this directory
    private int recursiveFileCount;         // Total files including subdirectories
    private boolean album;                  // Is this a base/album folder?
}
```

### Tree Structure
```
Root (virtual)
├── Pictures (album, bold)
│   ├── 2024 (15/247 files)
│   │   ├── January (8 files)
│   │   └── February (7 files)
│   └── 2023 (10/232 files)
│       └── ...
├── Videos (album, bold)
│   └── ...
└── Target (album, bold, purple)
    └── ...
```

### Building Process

1. **Create Root**
```java
TreeItem<DirectoryNode> root = new TreeItem<>(
    new DirectoryNode(null, "All Album Folders")
);
```

2. **Add Album Folders**
```java
for (Path baseFolder : baseFolders) {
    DirectoryNode albumNode = new DirectoryNode(baseFolder, displayName, true);
    TreeItem<DirectoryNode> albumItem = new TreeItem<>(albumNode);
    buildSubtree(albumItem, baseFolder); // Add children
    root.getChildren().add(albumItem);
}
```

3. **Build Subtree from fileIndex**
```java
// Extract unique directories from fileIndex
Set<Path> dirsWithFiles = fileIndex.values().stream()
    .flatMap(List::stream)
    .map(FileIndexEntry::getDirectory)
    .filter(dir -> dir.startsWith(basePath))
    .collect(Collectors.toSet());

// Create tree nodes for each directory
for (Path dir : sortedDirs) {
    DirectoryNode node = new DirectoryNode(dir);
    
    // Count files directly in this directory
    long fileCount = fileIndex.values().stream()
        .flatMap(List::stream)
        .filter(entry -> entry.getDirectory().equals(dir))
        .count();
    
    node.setMediaFileCount((int) fileCount);
    node.setScanned(true);
    
    TreeItem<DirectoryNode> item = new TreeItem<>(node);
    parentItem.getChildren().add(item);
}
```

4. **Calculate Recursive Counts**
```java
private int calculateRecursiveCounts(TreeItem<DirectoryNode> item) {
    DirectoryNode node = item.getValue();
    int totalCount = node.getMediaFileCount(); // Direct files
    
    // Add counts from all children recursively
    for (TreeItem<DirectoryNode> child : item.getChildren()) {
        totalCount += calculateRecursiveCounts(child);
    }
    
    node.setRecursiveFileCount(totalCount);
    return totalCount;
}
```

5. **Selection Preservation**
```java
// Before rebuild - save current selection
Path currentlySelectedPath = null;
TreeItem<DirectoryNode> currentSelection = 
    directoryTree.getSelectionModel().getSelectedItem();
if (currentSelection != null) {
    currentlySelectedPath = currentSelection.getValue().getPath();
}

// After rebuild - restore selection
TreeItem<DirectoryNode> itemToSelect = 
    findTreeItemByPath(root, currentlySelectedPath);
if (itemToSelect != null) {
    directoryTree.getSelectionModel().select(itemToSelect);
}
```

### Memory Usage Estimation

For **1,000 directories** in tree:

**DirectoryNode per directory:**
- Path path: 8 bytes (reference)
- String displayName: ~50 bytes
- List children: 40 bytes (not used, but allocated)
- int fields: 12 bytes
- boolean flags: 2 bytes
- Object overhead: 16 bytes
- **Total per node**: ~128 bytes

**TreeItem wrapper per directory:**
- ~80 bytes per TreeItem

**Total**: 1,000 × (128 + 80) = ~208 KB

## 3. Current Displayed Files (`currentDisplayedFiles`)

### Structure
```java
ObservableList<MediaFile> currentDisplayedFiles
```

### Purpose
- Files currently shown in table/thumbnail view
- Bound to JavaFX UI components for automatic updates

### MediaFile Class (HEAVY)
```java
public class MediaFile {
    private String filename;              // ~70 bytes
    private Path absolutePath;            // 8 bytes (reference)
    private Path relativePath;            // 8 bytes
    private Instant lastModified;         // 24 bytes
    private MediaType type;               // 4 bytes
    private long sizeBytes;               // 8 bytes
    private Dimension resolution;         // 24 bytes
    private String sha1Hash;              // 80 bytes
    private String location;              // ~100 bytes
    private Map<String, Object> metadata; // ~200 bytes
    private Long durationSeconds;         // 24 bytes
    private Instant dateTaken;            // 24 bytes
    private boolean dateEstimated;        // 1 byte
    private boolean corrupted;            // 1 byte
    // Total: ~592 bytes per file
}
```

### Memory Usage
- **100 files displayed**: 100 × 592 = 59 KB
- **500 files displayed**: 500 × 592 = 296 KB
- **1,000 files displayed**: 1,000 × 592 = 592 KB

### Loading Process
```java
// User selects directory in tree
onDirectorySelected(Path directory) {
    Task<List<MediaFile>> scanTask = new Task<>() {
        protected List<MediaFile> call() {
            return directoryScanService.scanDirectory(directory);
        }
    };
    
    scanTask.setOnSucceeded(event -> {
        List<MediaFile> files = scanTask.getValue();
        currentDisplayedFiles.clear();
        currentDisplayedFiles.addAll(files);
        highlightDuplicates(); // Uses fileIndex to mark duplicates
        refreshCurrentView();  // Updates table or thumbnails
    });
}
```

## 4. Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         SCAN OPERATION                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  ScanResult      │
                    │  List<MediaFile> │
                    └──────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │  MainController.onScanSucceeded()       │
        │  - Preserve existing hashes             │
        │  - Remove old entries from folders      │
        │  - Add new FileIndexEntry objects       │
        └─────────────────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                ▼                           ▼
    ┌──────────────────────┐    ┌──────────────────────┐
    │   fileIndex          │    │  directoryTree       │
    │   (HashMap)          │    │  (TreeView)          │
    │                      │    │                      │
    │  - Duplicate detect  │    │  - UI navigation     │
    │  - File tracking     │    │  - File counts       │
    │  - Hash preservation │    │  - Selection state   │
    └──────────────────────┘    └──────────────────────┘
                │                           │
                └─────────────┬─────────────┘
                              ▼
                ┌──────────────────────────┐
                │  User selects directory  │
                └──────────────────────────┘
                              │
                              ▼
                ┌──────────────────────────┐
                │ currentDisplayedFiles    │
                │ (ObservableList)         │
                │                          │
                │ Shown in:                │
                │ - Table View             │
                │ - Thumbnail View         │
                └──────────────────────────┘
```

## 5. Memory Optimization Techniques

### 1. Shared Directory Paths
Instead of storing full path in every FileIndexEntry:
```java
// BAD: 50,000 × 150 bytes = 7.5 MB
for (MediaFile file : files) {
    new FileIndexEntry(file.getAbsolutePath(), ...);
}

// GOOD: 1,000 × 150 bytes + 50,000 × 8 bytes = 150 KB + 400 KB = 550 KB
Path sharedDir = file.getAbsolutePath().getParent();
for (MediaFile file : files) {
    new FileIndexEntry(sharedDir, file.getFileName(), ...);
}
```

### 2. Lightweight Index, Heavy Display
- fileIndex: Minimal data (118 bytes/file)
- currentDisplayedFiles: Full data only for visible files (592 bytes/file)
- Only load heavy MediaFile objects when directory is selected

### 3. NO_HASH_KEY Strategy
- Quick scan files still tracked in index
- Placeholder prevents null checks everywhere
- Easy to identify unhashed files: `hash.equals(NO_HASH_KEY)`

### 4. HashMap for O(1) Duplicate Detection
```java
// O(1) lookup instead of O(n) scan
List<FileIndexEntry> duplicates = fileIndex.get(hash);
boolean isDuplicate = duplicates != null && duplicates.size() > 1;
```

## 6. Persistence (Snapshot)

### Structure on Disk
```json
{
  "timestamp": "2026-04-26T11:30:00Z",
  "fileIndex": {
    "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3": [
      {
        "directory": "/Users/bob/Pictures",
        "filename": "photo1.jpg",
        "lastModified": "2024-01-15T10:30:00Z"
      }
    ]
  }
}
```

### Saved as: `~/.album-organizer/cache.json.gz`
- GZIP compressed (typical 10:1 compression)
- 50,000 files: ~500 KB compressed

### Load on Startup
```java
Map<String, List<FileIndexEntry>> snapshot = 
    snapshotRepository.loadSnapshot();
fileIndex.putAll(snapshot);
```

### Save on Shutdown
```java
snapshotRepository.saveSnapshot(fileIndex);
```

## 7. Performance Characteristics

### Time Complexity
- **Add file to index**: O(1) amortized
- **Find duplicates**: O(1) hash lookup
- **Get files in directory**: O(n) where n = total files (filtered by directory)
- **Build directory tree**: O(n) where n = total files

### Space Complexity
- **fileIndex**: O(f) where f = number of files
- **directoryTree**: O(d) where d = number of directories
- **currentDisplayedFiles**: O(v) where v = files in viewed directory

### Actual Memory Usage
```
Baseline (no files):           ~32 MB
Light (1,000 files):          ~32.3 MB
Medium (10,000 files):        ~33 MB
Heavy (50,000 files):         ~38 MB
During scan (50,000 files):   ~70 MB (temporary peak)
```

## 8. Key Invariants

1. **fileIndex always consistent**: Every file in every scanned folder appears exactly once
2. **NO_HASH_KEY excluded from duplicates**: Files without hashes can't be duplicates
3. **Directory tree built from fileIndex**: Tree always reflects current index state
4. **Selection preserved**: Tree selection maintained across rebuilds
5. **Hash preservation**: Existing hashes never lost during quick scan

## Summary

Album Organizer uses a **three-tier memory structure**:

1. **fileIndex** (HashMap): Lightweight global index for duplicate detection and tracking
2. **directoryTree** (TreeView): UI structure for navigation with file counts
3. **currentDisplayedFiles** (ObservableList): Heavy objects only for currently viewed directory

This architecture provides:
- ✅ **Fast duplicate detection**: O(1) hash lookups
- ✅ **Memory efficiency**: Shared paths, lightweight entries
- ✅ **UI responsiveness**: Only load full objects for visible files
- ✅ **Persistence**: Compressed snapshot for instant startup
- ✅ **Smart scanning**: Hash preservation and selective recalculation
