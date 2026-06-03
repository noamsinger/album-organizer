# Album Organizer

A cross-platform desktop application for scanning, organizing, and AI-enhancing image and video files on your local machine.

## Features

- **Dual-Panel Interface**: Directory tree view on the left, detailed file table on the right
- **Pale Orange Theme**: Custom pale orange (#FFE5CC) color scheme for window frames, menu bar, status bar, and progress panel
- **Multiple View Modes**:
  - **List View**: Detailed table with sortable columns
  - **Thumbnail View**: Visual grid of thumbnails with loading spinners
    - Per-file progress indicators during thumbnail generation
    - Two-pass progress bar: file reading (0-100%) then thumbnail loading (0-100%)
    - Automatic video frame rotation using FFmpeg displaymatrix
    - HEIC support via macOS sips conversion
    - LRU disk cache (42MB max) at platform-standard cache dir (Mac: `~/Library/Caches/album-organizer/thumbnails/`)
- **Smart Scanning**:
  - **Full Scan with Hash**: Complete recursive scan with SHA-1 hash calculation
    - Smart hash recalculation: only rehashes new, modified, or unhashed files
    - Reuses existing hashes for unchanged files (based on modification date)
  - **Quick Scan**: Ultra-fast scan without hash calculation
    - Reads file names, modification times, and basic metadata only
    - Preserves existing hashes from previous full scans
    - 10-50x faster than full scan
- **Archive Support (ZIP/RAR)**:
  - Archive files appear as virtual folders in the directory tree (🗄 icon)
  - Click an archive node to browse its contents
  - Thumbnails generated for images inside archives
  - Double-click an archive entry to extract to a temp file and open it
  - Toggle archive visibility via `View > Show Archives in Tree`
- **Special Folders** (always last in tree):
  - **Target** (`~/AlbumTarget`, purple): destination for organized files
  - **AI-Generated** (`~/AlbumAiGenerated`, blue): all AI-enhanced output
  - **Recycle-Bin** (`~/AlbumRecycleBin`, dark red): deleted files staging area
  - All three created automatically on startup; configurable via File menu
- **Album Folder Management**:
  - Add multiple album folders to scan
  - Set one folder as target folder (marked in purple)
  - Prevent adding overlapping folders (no parent/child conflicts)
- **File Management**:
  - View detailed metadata: filename, date, date taken (UTC), type, duration, size, resolution, location, SHA-1 hash
  - Date Taken shows EXIF date or estimated date from filename (prefixed with "=>")
  - Folder counts show both direct and recursive file counts (e.g., "Photos (5/247)")
  - Double-click to open files (images always open in Preview on macOS)
  - Right-click context menu: Copy filename, Copy path, Show in folder, Organize File, Remove Other Duplicates, Delete File, Enhance with AI
  - Corrupted files displayed in **pale red background**
  - Selected files: dark blue background with color-coded foreground
- **Duplicate Detection**:
  - Automatically identifies duplicate files using SHA-1 hashing
  - Duplicates highlighted with **yellow background**
  - "Remove Other Duplicates" with selective checkboxes
- **File Organization**:
  - Organize single files or entire folder trees to target folder
  - Automatic folder structure: Year/Month/Day based on date taken
  - Resolution-based splitting: low-res/med-res folders
  - Collision detection with hash verification
  - Move mode with integrity verification
- **AI Image Enhancement**:
  - Enhance images using multiple AI providers (cloud and local)
  - Multi-prompt checkbox selection — combine multiple enhancement styles
  - Prompt selection and provider choice persisted across sessions
  - **Cloud providers**: Stability AI, OpenAI DALL·E, Google Gemini, Grok xAI
  - **Local providers**: Stable Diffusion WebUI, ComfyUI, InvokeAI, Real-ESRGAN
  - All providers receive images as JPEG (converted in-memory)
  - Enhanced output always saved to the AI-Generated special folder
  - **AI-Enhance from Clipboard**: `File > AI-Enhance from Clipboard` enhances the current clipboard image (enabled only when clipboard has an image and a provider is configured)
- **First-Run Setup**: on first launch with no albums configured, the app detects Pictures, Dropbox, Google Drive, OneDrive, and iCloud folders and offers to add them with a full scan
- **Settings**:
  - **Organize tab**: copy/move mode, folder structure, resolution thresholds
  - **AI Enhancement tab**: per-provider API keys and URLs, with setup instructions
- **Font Size Control**:
  - Adjustable font size with keyboard shortcuts (Cmd +, Cmd -, Cmd 0)
  - Font size persists between sessions
- **Progress Panel**: Real-time progress tracking for scans and organize operations
  - Two-pass progress for folder view: scan phase then thumbnail phase
  - Stop button to cancel operations mid-process
  - Collapsible panel with hide/show toggle
  - "View Last Organizing Report" button

## System Requirements

- **Java**: 17 or higher (LTS)
- **Operating Systems**:
  - Windows 10/11
  - macOS (Intel and Apple Silicon)
  - Ubuntu 22.04 or equivalent Linux distribution
- **Memory**: Minimum 512MB RAM (1GB+ recommended for large collections)
- **Disk Space**: 50MB for application + space for cache files

## Installation

### macOS (Recommended)

```bash
./build.sh
open "target/dist/AlbumOrganizer.app"
```

### Windows

```bash
build.bat
start "target\dist\Album Organizer\Album Organizer.exe"
```

## Usage

### Getting Started

1. **Launch the application**
2. **Add album folders**: `File > Add Album Folder`
3. **Set a target folder** (optional): right-click an album folder → "Make Target Folder"
4. **Run a scan**: right-click a folder → Full Scan with Hash (or Quick Scan)

### Directory Tree

- Click a folder to show its files in the right panel
- Archive files (.zip, .rar) appear as virtual folder nodes — click to browse contents
- Toggle archive visibility with `View > Show Archives in Tree`
- Right-click for: Full Scan with Hash, Quick Scan, Open in file browser, Do Magic > Organize Folder Recursively, Make Target Folder, Unset as Target Folder, Remove from Album Folders

### File Table

Columns: Filename, Date, Date Taken (UTC), Type, Duration, Size, Resolution, Location, Hash

- **Double-click**: open file (images open in Preview on macOS)
- **Right-click**: Copy filename, Copy path, Show in folder, Organize File, Delete File, Remove Other Duplicates, Enhance with AI
- **Visual indicators**: yellow = duplicate, pale red = corrupted

### View Modes

Switch via the `View` menu:
- **List View**: Detailed table with all metadata columns
- **Thumbnail View**: Visual grid of file thumbnails

### AI Enhancement

1. Right-click a file → **Enhance with AI** (or select it and use the context menu), or use `File > AI-Enhance from Clipboard` for clipboard images
2. In the dialog:
   - Select one or more prompts using checkboxes (e.g., "Upscale & Sharpen", "Restore Old Photo")
   - Choose an AI provider from the dropdown
   - Set output dimensions (optional)
   - Click **Enhance**
3. The enhanced file is saved to the **AI-Generated** special folder (`~/AlbumAiGenerated`) and the folder view refreshes automatically

**Setting up AI providers**: open `File > Settings > AI Enhancement` and follow the Instructions button for each provider.

### Settings

`File > Settings...` has two tabs:

**Organize**
- Organize mode: Copy or Move (with integrity verification)
- Folder structure: Year / Month / Day hierarchies (cascading)
- Resolution splitting: low-res / med-res thresholds (pixels)

**AI Enhancement**
- Image Cloud AI: Stability AI, OpenAI DALL·E, Google Gemini, Grok xAI (API keys)
- Image Local AI: Stable Diffusion WebUI, ComfyUI, InvokeAI, Real-ESRGAN (base URLs / paths)

### Organizing Files

**Single file**: right-click → Organize File
**Recursive**: right-click folder → Do Magic → Organize Folder Recursively

Target path = `targetFolder / YYYY/MM/DD / [resolution] / filename`

Collision handling: if a file already exists at the target, hashes are compared — same hash means skip, different hash means suffix the filename.

### Scanning

**Full Scan with Hash** — first scan, or when you need duplicate detection. Smart: only rehashes changed files.

**Quick Scan** — fast updates (10-50x faster). Preserves existing hashes, no duplicate detection for new files until a full scan.

**Recommended workflow**: Full scan once → Quick scan daily → Full scan periodically.

### Configuration Files

`~/.config/album-organizer/album-organizer-config.ini`:

```ini
[AlbumFolders]
/Users/username/Pictures

[Settings]
targetFolder=/Users/username/AlbumTarget
aiGeneratedFolder=/Users/username/AlbumAiGenerated
recycleBinFolder=/Users/username/AlbumRecycleBin
fontSizeFactor=0
showArchivesInTree=true

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
...
```

### Cache and Log Locations

| Item | macOS | Windows | Linux |
|------|-------|---------|-------|
| Config | `~/.config/album-organizer/` | `~/.config/album-organizer/` | `~/.config/album-organizer/` |
| Snapshot cache | `~/.config/album-organizer/cache.json.gz` | same | same |
| Thumbnails | `~/Library/Caches/album-organizer/thumbnails/` | `%LOCALAPPDATA%\album-organizer\thumbnails\` | `~/.cache/album-organizer/thumbnails/` |
| Logs | `~/Library/Logs/album-organizer/` | `%APPDATA%\album-organizer\logs\` | `~/.local/share/album-organizer/logs/` |
| Reports | `~/Library/Logs/album-organizer/reports/` | `%APPDATA%\album-organizer\reports\` | `~/.local/share/album-organizer/reports/` |

## Supported File Formats

### Images
JPG/JPEG, PNG, GIF, BMP, TIFF, WEBP, HEIC, RAW

### Videos
MP4, MOV, AVI, MKV, WMV, FLV, WEBM, M4V, MPG, MPEG

### Archives
ZIP, RAR

## Keyboard Shortcuts

- **Cmd +** / **Cmd =**: Increase font size
- **Cmd -**: Decrease font size
- **Cmd 0**: Reset font size

## Troubleshooting

### Application won't start
- Verify Java 17+: `java -version`
- Run from terminal to see error output

### Scan is slow
- Use Quick Scan for regular updates (10-50x faster)
- Subsequent Full Scans rehash only changed files

### Permission denied
- macOS: grant Full Disk Access in System Settings > Privacy & Security
- Linux: check permissions with `ls -la`

### Files not appearing
- Verify extension is supported
- Check the file isn't corrupted (corrupted files appear in pale red)

### AI Enhancement failing
- Check the API key is correct and has credits
- For local providers, verify the server is running at the configured URL
- Error messages now parse JSON from the API response for clear diagnostics

### Can't open file
- macOS images always open in Preview
- Other files use the system default application
- Verify the file hasn't been moved since the last scan

## Building from Source

```bash
git clone <repository-url>
cd album-organizer
./build.sh              # macOS
build.bat               # Windows
```

See [design.md](design.md) for architecture details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[Specify your license here]

## Acknowledgments

- Built with [JavaFX](https://openjfx.io/)
- Metadata extraction: [metadata-extractor](https://github.com/drewnoakes/metadata-extractor)
- Archive support: [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)
- VibeCoded by Noam Singer
