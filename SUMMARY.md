# Album Organizer - Summary

## Project Status: v1.2.0

Album Organizer is a cross-platform desktop application for scanning, organizing, and managing image and video files.

---

## Quick Start

```bash
# Build the application and create installer
./build.sh

# Run the app (macOS)
open "target/dist/Album Organizer.app"
```

### Basic Workflow

1. **Launch**: Open the app bundle
2. **Add Folders**: File > Add Album Folder
3. **Scan**: Right-click folder > Full Scan with Hash (or Quick Scan)
4. **Browse**: Click folders to view files in list or thumbnail mode
5. **Organize**: Right-click folder > Do Magic > Organize Folder Recursively
6. **Find Duplicates**: Look for yellow-highlighted rows

---

## Key Features

- **Dual-Pane Interface**: Directory tree + file table/thumbnail grid
- **Smart Scanning**: Full Scan with Hash (SHA-1) or Quick Scan (fast, no hashing)
- **Thumbnail View**: Async loading with spinners, video frame extraction, HEIC support
- **Duplicate Detection**: Yellow-highlighted files with same SHA-1 hash
- **File Organization**: Date-based folders with resolution splitting (Do Magic > Organize)
- **Two-Pass Progress**: Separate progress for file reading and thumbnail generation
- **Organization Reports**: Compressed in memory, written to `/tmp/album-organizer-reports/` on demand
- **macOS Native**: .app bundle + .dmg installer, single-instance via Launch Services
- **Graceful Shutdown**: 5-second timeout prevents stalling on quit

---

## Build & Distribution

```bash
./build.sh
# Output:
#   target/dist/Album Organizer.app       - macOS app bundle
#   target/dist/Album Organizer-1.1.0.dmg - macOS installer
```

---

## Architecture

```
com.albumorganizer/
├── AlbumOrganizerApp.java       # Main entry point
├── controller/                   # UI controllers (MainController)
├── model/                        # Domain objects (MediaFile, etc.)
├── service/                      # Business logic (Scanner, Thumbnail, Organize, Metadata)
├── repository/                   # Data persistence (Config, Snapshot)
├── task/                         # Background tasks (ScanTask)
└── util/                         # Utilities (ErrorDialog, FormatUtils, FileTrash)
```

---

## Configuration

- App settings: `~/.album-organizer/album-organizer-config.ini`
- Snapshot cache: `~/.album-organizer/cache.json.gz`
- Thumbnail cache: `~/.album-organizer/thumbnails/`
- Reports: `/tmp/album-organizer-reports/album-organizer.YYYY-MM-DD_HH-mm-ss.txt`

---

*VibeCoded by Noam Singer*
