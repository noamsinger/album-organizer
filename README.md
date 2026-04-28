# Album Organizer

A cross-platform desktop application for scanning, organizing, and managing image and video files on your local machine.

## Features

- **Dual-Panel Interface**: Directory tree view on the left, detailed file table on the right
- **Pale Orange Theme**: Custom pale orange (#FFE5CC) color scheme for window frames, menu bar, status bar, and progress panel
- **Multiple View Modes**: 
  - **List View**: Detailed table with sortable columns
  - **Thumbnail View**: Visual grid of thumbnails
- **Smart Scanning**: 
  - **Full Scan with Hash**: Complete recursive scan with SHA-1 hash calculation
    - Smart hash recalculation: only rehashes new, modified, or unhashed files
    - Reuses existing hashes for unchanged files (based on modification date)
    - Dramatically faster on subsequent scans
  - **Quick Scan**: Ultra-fast scan without hash calculation
    - Scans file names, modification times, and basic metadata only
    - Preserves existing hashes from previous full scans
    - 10-50x faster than full scan
    - Ideal for quick updates and file discovery
- **Album Folder Management**:
  - Add multiple album folders to scan
  - Set one folder as target folder (marked in purple)
  - Prevent adding overlapping folders (no parent/child conflicts)
- **File Management**:
  - View detailed metadata: filename, date, date taken (UTC), type, duration, size, resolution, location, SHA-1 hash
  - Date Taken shows EXIF date or estimated date from filename (prefixed with "=>")
  - Date validation: years must be 1800-2099, dates must be calendar-valid
  - Folder counts show both direct and recursive file counts (e.g., "Photos (5/247)")
  - Double-click to open files with system default application
  - Right-click context menu: Copy filename, Copy path, Show in folder, Organize File, Remove Other Duplicates, Delete File
  - Corrupted files displayed in **pale red background** for easy identification
  - Selected files: dark blue background with color-coded foreground (white for normal, strong yellow for duplicates, pink for corrupt)
- **Duplicate Detection**: 
  - Automatically identifies duplicate files using SHA-1 hashing
  - Duplicates highlighted with **yellow background**
  - "Remove Other Duplicates" context menu option with selective checkboxes to choose which duplicates to delete
  - Memory-optimized duplicate index using HashMap with shared directory paths
- **File Organization**:
  - Organize single files or entire folder trees to target folder
  - Automatic folder structure: Year/Month/Day based on date taken
  - Resolution-based splitting: low-res/med-res folders (high-res files at root)
  - Collision detection with hash verification and automatic filename suffixing
  - Move mode with integrity verification (copy, verify hash, then delete)
  - Progress panel shows real-time organizing status with stop capability
- **Font Size Control**: 
  - Adjustable font size with keyboard shortcuts (Cmd +, Cmd -, Cmd 0)
  - Font size persists between sessions
  - All dialogs automatically scale with font size and are resizable
- **Organize Settings**: Configure file organization preferences
  - Copy or Move mode
  - Folder structure (Year/Month/Day hierarchies)
  - Resolution-based splitting (low-res/med-res/high-res)
  - Customizable pixel thresholds with validation
- **Progress Panel**: Real-time progress tracking for scans and organize operations
  - Shows discovered files during operations
  - Stop button to cancel operations mid-process
  - Collapsible panel with hide/show toggle
- **Efficient Caching**: 
  - Single global snapshot cache (`~/.album-organizer/cache.json.gz`)
  - Compressed JSON format with GZIP for fast startup
  - Stores file metadata including lastModified timestamps
  - Automatic cache validation on startup
  - No hidden files created in user directories
- **Cross-Platform**: Works on Windows, macOS, and Linux

## System Requirements

- **Java**: 17 or higher (LTS)
- **Operating Systems**:
  - Windows 10/11
  - macOS (Intel and Apple Silicon)
  - Ubuntu 22.04 or equivalent Linux distribution
- **Memory**: Minimum 512MB RAM (1GB+ recommended for large collections)
- **Disk Space**: 50MB for application + space for cache files

## Installation

### Option 1: Native Installers (Recommended)

Download the appropriate installer for your platform:

- **Windows**: `album-organizer-setup.msi` or `album-organizer-setup.exe`
- **macOS**: `album-organizer.dmg` or `album-organizer.pkg`
- **Linux**: `album-organizer.deb` (Debian/Ubuntu) or `album-organizer.rpm` (Fedora/RHEL)

Run the installer and follow the on-screen instructions.

### Option 2: Running from JAR

If you prefer to run from the JAR file or native installers are not available:

```bash
java -jar album-organizer.jar
```

Make sure Java 17+ is installed and available in your PATH.

## Usage

### Getting Started

1. **Launch the application**
2. **Add album folders**:
   - Go to `File > Add Album Folder`
   - Choose one or more base directories containing your images/videos
   - Album folders are shown in **bold** in the directory tree
3. **Set a target folder** (optional):
   - Right-click an album folder and select "Make Target Folder"
   - Target folder is shown in **purple** and bold
4. **Run your first scan**:
   - Right-click a folder and select "Full Scan with Hash" to perform a complete scan with hash calculation
   - Or select "Quick Scan" for a fast scan without hashing
   - Wait for the scan to complete (progress panel shows status)

### Understanding the Interface

#### Left Panel: Directory Tree
- Shows the hierarchy of scanned directories with file counts
- **Folder counts** display as "Folder (direct/recursive)" - e.g., "Photos (2/753)"
  - First number: files directly in that folder
  - Second number: total files including all subdirectories
- **Album folders** are displayed in bold
- **Target folder** is displayed in purple and bold
- Click a folder to filter the table view to files in that directory
- Right-click for options: Full Scan with Hash, Quick Scan, Open in file browser, Organize Folder Recursively, Make Target Folder, Unset as Target Folder, Remove from Album Folders
- Context menu items are always visible (disabled when not applicable) for consistent UX
- Tree selection is preserved across scans and operations
- Right-click for options: Deep Scan, Quick Scan, Open in file browser, Organize Folder Recursively, Make Target Folder, Unset as Target Folder, Remove from Album Folders

#### Right Panel: File Table
- Displays all scanned image and video files with columns:
  - **Filename**: Name of the file
  - **Date**: Last modified date
  - **Date Taken (UTC)**: EXIF date or estimated from filename (with "=>" prefix)
  - **Type**: IMAGE or VIDEO
  - **Duration**: Video length (for video files)
  - **Size**: File size (formatted as KB/MB/GB)
  - **Resolution**: Dimensions (width x height pixels)
  - **Location**: Full directory path
  - **Hash**: SHA-1 hash for duplicate detection
- **Double-click** a file to open it with your system's default application
- **Right-click** for context menu:
  - Copy filename
  - Copy path
  - Show in folder
  - Organize File (moves/copies to target folder based on date and resolution)
  - Delete File (moves to trash)
  - Remove Other Duplicates (selective deletion with checkboxes)
- **Sort** by clicking column headers
- **Visual indicators**:
  - **Duplicates**: Yellow background (same SHA-1 hash)
  - **Corrupted files**: Pale red background
  - **Selected files**: Dark blue background with color-coded text
    - White: normal files
    - Strong yellow: duplicates
    - Pink: corrupted files

### View Modes

Switch between view modes via the `View` menu:

- **List View**: Detailed table with all metadata columns
- **Thumbnail View**: Visual grid showing file thumbnails

### Font Size Control

Adjust the interface font size:

- **Increase Font Size**: `View > Increase Font Size` or **Cmd +**
- **Decrease Font Size**: `View > Decrease Font Size` or **Cmd -**
- **Reset Font Size**: `View > Reset Font Size` or **Cmd 0**

Font size preference is saved and restored between sessions.

### Settings

Configure organize settings via `File > Settings...`:

1. **Organize Mode**: Copy or Move (move with verification)
2. **Folder Structure** (cascading checkboxes):
   - Organize with Year Folder (e.g., 2024/)
   - Organize with Month Folder (e.g., 2024/01/) - requires Year
   - Organize with Day Folder (e.g., 2024/01/15/) - requires Month
3. **Resolution-Based Splitting**:
   - Split to low resolution folders (low-res/med-res)
   - Low-res threshold (pixels) - default: 300,000
   - Hi-res threshold (pixels) - default: 1,000,000
   - Validation: low-res must be positive, hi-res must be greater than low-res

Files with date taken will be organized into date-based folders. Files without resolution metadata go into "unknown-resolution" folder. High-res files (above hi-res threshold) are placed at the root level without resolution subfolder.

All settings are persisted in `~/.album-organizer/album-organizer-config.ini`

### Organizing Files

**Organize Single File** (Right-click file → Organize File):
- Requires target folder to be set
- Organizes based on date taken (EXIF or estimated from filename)
- Applies folder structure and resolution settings
- Handles collisions with hash verification
- Auto-refreshes views to show organized files

**Organize Folder Recursively** (Right-click folder → Organize Folder Recursively):
- Requires target folder to be set and folder must have files
- Shows confirmation dialog with file count
- Recursively organizes all media files in folder and subdirectories
- Real-time progress in Progress Panel with file-by-file updates
- Stop button to cancel operation
- Continues on errors and reports them at end
- Auto-rescans target folder after completion

**Organization Logic**:
1. Target path = Target Folder + Date folders + Resolution folder + filename
2. Date folders: YYYY/MM/DD based on date taken (or "unknown-date")
3. Resolution folders: "low-res", "med-res", or none for high-res
4. Collision handling: If file exists, compare hashes
   - Same hash: skip (already there)
   - Different hash: append full SHA-1 to filename
5. Move mode: Copy → Verify hash → Delete original (integrity guaranteed)

### Progress Panel

Access via `View > Show Progress Panel` or automatically shown during operations:

- **Shows**: Real-time file discovery and processing
- **Scrolling list**: Recently discovered/processed files
- **Progress bar**: Visual progress indicator
- **Status text**: Current operation and counts
- **Stop button**: Cancel operation (enabled during active operations)
- **Hide button**: Collapse panel (always enabled)
- **Auto-hide**: Panel remains visible after operation for review

### Scanning Modes

#### Full Scan with Hash (Right-click folder → Full Scan with Hash)
- Scans all folders recursively for image and video files
- Calculates SHA-1 hash for duplicate detection
- **Smart hash recalculation**: Only rehashes files that need it
  - New files: Always hashed
  - Modified files: Rehashed if modification date changed
  - Unchanged files: Reuses existing hash (no I/O, no hashing)
  - Files without hash (from quick scan): Hashed
- Extracts metadata (resolution, dates, duration)
- Validates file integrity (detects corrupted files)
- **Use when**: 
  - First scan of a folder
  - After adding many new files
  - When you need duplicate detection
  - After quick scanning and want full hashes

#### Quick Scan (Right-click folder → Quick Scan)
- **Ultra-fast**: Scans ALL directories recursively without hash calculation
- Reads file names, modification times, sizes, and basic metadata only
- **Preserves existing hashes** from previous full scans
- New files get placeholder hash (no duplicate detection until full scan)
- 10-50x faster than full scan with hash
- **Use when**: 
  - Checking for new/deleted files
  - Quick updates between full scans
  - File discovery and counting
  - When duplicate detection isn't needed immediately

**Recommended Workflow**:
1. Run "Full Scan with Hash" once on all folders
2. Use "Quick Scan" for daily/regular updates (instant)
3. Run "Full Scan with Hash" periodically (only rehashes changed files)

### Configuration Files

The application stores configuration in `~/.album-organizer/album-organizer-config.ini`:

```ini
# Album Organizer Configuration File
# This file stores all application settings

[AlbumFolders]
/Users/username/Pictures
/Users/username/Videos

[Settings]
targetFolder=/Users/username/Pictures
fontSizeFactor=0

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

### Cache Files

**Snapshot Cache** (`~/.album-organizer/cache.json.gz`):
- Compressed JSON snapshot of entire file index
- Stores hash, filename, directory, and lastModified timestamp per file
- Loaded on startup for instant duplicate detection
- Updated after each scan (full or quick)
- Enables smart hash recalculation (only rehash when needed)
- Significantly reduces startup time and scan times for large collections
- Single global cache - no per-directory cache files

## Supported File Formats

### Images
- JPG/JPEG, PNG, GIF, BMP, TIFF, WEBP, HEIC, RAW

### Videos
- MP4, MOV, AVI, MKV, WMV, FLV, WEBM, M4V, MPG, MPEG

## Keyboard Shortcuts

- **Cmd +** (or Cmd =): Increase font size
- **Cmd -**: Decrease font size
- **Cmd 0**: Reset font size to 100%

## Troubleshooting

### Application won't start
- Verify Java 17+ is installed: `java -version`
- Check that JavaFX is available (included in Java 17+)
- Try running from command line to see error messages

### Scan is very slow
- First Full Scan with Hash of large collections (100,000+ files) can take time
- Use Quick Scan for fast updates (10-50x faster, preserves hashes)
- Subsequent Full Scans are much faster (only rehashes changed files)
- Close other resource-intensive applications
- Consider scanning smaller folder sets

### Permission denied errors
- Ensure you have read access to the folders you're scanning
- On macOS: Grant "Full Disk Access" in System Preferences > Security & Privacy
- On Linux: Check file/folder permissions with `ls -la`

### Files not appearing in table
- Verify file extensions are supported (see Supported File Formats)
- Check the file isn't corrupted (corrupted files appear in dark red)
- Look for error messages in the status bar

### Files without resolution
- Some PNG files may lack standard metadata
- Application includes fallback PNG header reader for maximum compatibility
- Videos require proper metadata in MP4/MOV format

### Can't open file on double-click
- Verify system has default application for that file type
- Check file hasn't been moved/deleted since scan
- On Linux: Ensure `xdg-open` is available

### High memory usage
- Memory-optimized for large collections using HashMap with shared directory paths
- Expected memory usage: ~310 MB for 1M files (22% reduction vs previous implementation)
- Filter to specific directories using the tree view to show fewer files
- Close and restart application to free memory

## Building from Source

See [design.md](design.md) and [plan.md](plan.md) for architecture details and build instructions.

```bash
# Clone repository
git clone <repository-url>
cd album-organizer

# Build with Maven (includes run.sh/run.bat scripts)
mvn clean package

# Run application (Unix/macOS)
./run.sh

# Run application (Windows)
run.bat

# Note: run scripts automatically stop previous instances gracefully before starting

# Or run manually with Java
java --module-path target/lib --add-modules javafx.controls,javafx.fxml \
  -cp "target/album-organizer-1.0.0.jar:target/lib/*" \
  com.albumorganizer.AlbumOrganizerApp

# Create native installer
mvn jpackage:jpackage
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[Specify your license here]

## Support

- **Issues**: Report bugs and feature requests at [GitHub Issues](your-repo-url/issues)
- **Documentation**: See [design.md](design.md) for technical details
- **Implementation Plan**: See [plan.md](plan.md) for development roadmap

## Acknowledgments

- Built with [JavaFX](https://openjfx.io/) for cross-platform UI
- Metadata extraction powered by [metadata-extractor](https://github.com/drewnoakes/metadata-extractor)
- File utilities from [Apache Commons](https://commons.apache.org/)
- VibeCoded by Noam Singer
