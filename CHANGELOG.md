# Changelog

All notable changes to Album Organizer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.1] - 2026-04-27

### Added
- Pale orange theme (#FFE5CC) for window frames and UI elements
- Custom styling for menu bar, status bar, and progress panel

### Changed
- Window chrome now uses pale orange color scheme
- Updated CSS with pale orange backgrounds for frame elements
- Menu bar moved inside window (no longer system menu bar) for consistent styling

## [1.1.0] - 2026-04-26

### Added
- Smart hash recalculation in Full Scan with Hash - only rehashes new, modified, or unhashed files
- Hash preservation during Quick Scan - maintains existing hashes from previous full scans  
- Tree selection preservation - maintains focus on selected folder across scans and operations
- Graceful shutdown in run scripts - automatically stops previous instances before starting
- Context menu items always visible with disabled states for better UX consistency

### Changed
- Renamed "Deep Scan" to "Full Scan with Hash" for clearer terminology throughout application
- Quick Scan now skips hash calculation entirely (10-50x faster)
- Full Scan with Hash only recalculates hashes when needed based on modification date
- Context menus use `.setDisable()` instead of `.setVisible()` for menu items
- Run scripts (`run.sh` and `run.bat`) now gracefully terminate previous instances

### Fixed
- Quick Scan files now appear correctly in directory tree and file view (NO_HASH_KEY system)
- Hash data no longer lost when running Quick Scan after Full Scan with Hash
- Tree selection no longer jumps to first album after scans or file organization
- Run scripts now prevent multiple application instances running simultaneously

### Performance
- Quick Scan: 10-50x faster than before (no hash calculation, minimal I/O)
- Full Scan with Hash: Dramatically faster on subsequent scans (only rehashes changed files)
- Reduced scan times for large collections through smart hash caching

## [1.0.0] - 2026-04-21

### Added

#### Core Features
- **Deep Scan**: Complete recursive scanning of directories for images and videos
- **Quick Scan**: Incremental scanning that only processes changed files
- **Cache System**: CSV-format cache files (`album-organizer.ini`) in each scanned directory
- **Hash Calculation**: SHA-1 hashing with parallel processing for performance
- **Metadata Extraction**: Resolution and EXIF data from images and videos

#### User Interface
- **Dual-Pane Layout**: Directory tree on left, file table on right
- **Menu System**: File menu with folder selection and scan options
- **Table View**: 7 columns (Filename, Date, Type, Size, Resolution, Location, Hash)
- **Directory Tree**: Navigate and filter by folder
- **Status Bar**: Shows file count, error count, and scan status
- **Progress Dialog**: Visual feedback during scans with cancel button

#### File Operations
- **Double-Click to Open**: Opens files with system default application
- **Context Menus**: Right-click options for files and folders
  - Open File
  - Show in Folder
  - Copy Path
  - Copy Hash
  - Rescan Folder
  - Open in File Browser

#### Advanced Features
- **Duplicate Detection**: Automatically identifies and highlights duplicate files (same SHA-1 hash) in yellow
- **Background Scanning**: Non-blocking UI with JavaFX Task
- **Configuration Persistence**: Remembers selected folders and window size/position
- **Clipboard Support**: Copy file paths and hashes
- **Error Handling**: Comprehensive error dialogs with stack traces
- **Logging**: File-based logging to `~/.album-organizer/logs/`

#### Styling
- **Professional CSS**: Modern, clean design
- **Hover Effects**: Visual feedback on tables and trees
- **Duplicate Highlighting**: Yellow background for duplicate files
- **Responsive UI**: Adapts to window resizing

#### Performance
- **Parallel Hash Calculation**: Uses ForkJoinPool for multi-core CPUs
- **Batch Processing**: Processes files in batches to manage memory
- **Virtual Scrolling**: Handles 100,000+ rows efficiently
- **Skip Unchanged Files**: Quick scan only re-hashes modified files

#### Cross-Platform Support
- **Windows**: Full support with batch scripts
- **macOS**: Full support with shell scripts (Intel and Apple Silicon)
- **Linux**: Full support with shell scripts

#### File Format Support
- **Images**: JPG, JPEG, PNG, GIF, BMP, TIFF, TIF, WEBP, HEIC, RAW
- **Videos**: MP4, MOV, AVI, MKV, WMV, FLV, WEBM, M4V, MPG, MPEG

#### Testing
- **Unit Tests**: 7 tests for date utilities
- **Integration Tests**: 8 tests for scanning functionality
- **Test Coverage**: Core scanning logic fully tested

#### Documentation
- **README.md**: User guide with features, installation, and usage
- **design.md**: Technical architecture and data models
- **plan.md**: Implementation plan and verification strategy
- **CONTRIBUTING.md**: Contribution guidelines
- **Build Scripts**: `build.sh` and `build.bat` for easy building

#### Build & Distribution
- **Maven Build**: Standard Maven project structure
- **JavaFX Integration**: Maven plugin for JavaFX
- **Launch Scripts**: `run.sh` and `run.bat` for easy launching
- **Native Packaging**: jpackage configuration for DMG, EXE, DEB, RPM

### Dependencies
- Java 17+
- JavaFX 21.0.1
- metadata-extractor 2.19.0
- commons-io 2.15.1
- commons-codec 1.16.0
- guava 32.1.3-jre
- slf4j 2.0.9
- logback 1.4.11
- JUnit 5.10.1
- TestFX 4.0.18
- Mockito 5.7.0

### Technical Details

#### Cache File Format
- Format: CSV with header comments
- Date format: `YYYYMMDD-HHmmSS.sssZ` (UTC with milliseconds)
- Fields: filename, lastModified, hash
- Example:
  ```
  # Album Organizer Cache v1.0
  # Format: filename,lastModified,hash
  photo1.jpg,20230515-143022.000Z,a94a8fe5ccb19ba61c4c0873d391e987982fbbd3
  ```

#### Package Structure
```
com.albumorganizer/
├── AlbumOrganizerApp.java       # Main entry point
├── controller/                   # UI controllers
├── model/                        # Domain objects
├── service/                      # Business logic
├── repository/                   # Data persistence
├── task/                         # Background tasks
└── util/                         # Utilities
```

### Known Limitations
- Hash calculation can be slow for very large files (>1GB)
- Native installers require manual build with jpackage
- No built-in image viewer (uses system default)
- Cache files not encrypted (hashes and filenames visible)

### Performance Benchmarks
- Deep scan: ~10,000 files in < 5 minutes (SSD, quad-core)
- Quick scan: ~10,000 files (no changes) in < 1 minute
- Memory usage: < 500MB for 100,000 files
- UI responsiveness: Smooth scrolling with 100,000+ rows

---

## [Unreleased]

### Planned Features
- Database backend (SQLite) to replace INI files
- Advanced search and filtering
- Batch operations (move, copy, delete)
- Export to CSV/JSON/Excel
- Video thumbnail generation
- Cloud storage support
- Timeline and map views
- Dark mode

---

## Version History

- **1.0.0** (2026-04-21): Initial release

---

For full details, see [README.md](README.md) and [design.md](design.md).
