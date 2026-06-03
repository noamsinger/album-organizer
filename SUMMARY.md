# Album Organizer - Summary

## Project Status: v1.5.0

Album Organizer is a cross-platform desktop application for scanning, organizing, and AI-enhancing image and video files.

---

## Quick Start

```bash
./build.sh
open "target/dist/AlbumOrganizer.app"
```

### Basic Workflow

1. **Launch**: Open the app bundle
2. **Add Folders**: File > Add Album Folder
3. **Scan**: Right-click folder > Full Scan with Hash (or Quick Scan)
4. **Browse**: Click folders to view files in list or thumbnail mode
5. **Organize**: Right-click folder > Do Magic > Organize Folder Recursively
6. **Enhance**: Right-click file > Enhance with AI
7. **Find Duplicates**: Look for yellow-highlighted rows

---

## Key Features

- **Dual-Pane Interface**: Directory tree + file table / thumbnail grid
- **Smart Scanning**: Full Scan with Hash (SHA-1) or Quick Scan (fast, no hashing)
- **Archive Support**: ZIP/RAR appear as virtual folders in the tree (🗄); click to browse, double-click entries to open
- **Thumbnail View**: Async loading with spinners, video frame extraction, HEIC support, archive entry thumbnails
- **Duplicate Detection**: Yellow-highlighted files with same SHA-1 hash
- **File Organization**: Date-based folders with resolution splitting (Do Magic > Organize)
- **Special Folders** (always last in tree, created on startup):
  - **Target** (`~/AlbumTarget`, purple): destination for organized files
  - **AI-Generated** (`~/AlbumAiGenerated`, blue): all AI-enhanced output
  - **Recycle-Bin** (`~/AlbumRecycleBin`, dark red): deleted files staging
- **AI Image Enhancement**:
  - Multi-prompt checkbox selection (combine styles, persisted across sessions)
  - Cloud: Stability AI, OpenAI DALL·E, Google Gemini, Grok xAI
  - Local: Stable Diffusion WebUI, ComfyUI, InvokeAI, Real-ESRGAN
  - Enhanced output always goes to the AI-Generated special folder
  - **AI-Enhance from Clipboard**: `File > AI-Enhance from Clipboard` for clipboard images
- **First-Run Setup**: detects and suggests Pictures/Dropbox/Google Drive/OneDrive/iCloud
- **macOS Native**: images open in Preview; .app bundle + .dmg installer

---

## Settings

`File > Settings...` — two tabs:

| Tab | Contents |
|-----|----------|
| **Organize** | Copy/Move mode, Year/Month/Day folder structure, resolution thresholds |
| **AI Enhancement** | API keys / URLs for all cloud and local AI providers |

---

## Build & Distribution

```bash
./build.sh
# Output:
#   target/dist/AlbumOrganizer.app       - macOS app bundle
#   target/dist/AlbumOrganizer-1.5.0.dmg - macOS installer
```

---

## Architecture

```
com.albumorganizer/
├── AlbumOrganizerApp.java       # Main entry point
├── controller/                   # UI controllers (MainController, EnhancementDialog, SettingsDialog)
├── model/                        # Domain objects (MediaFile, DirectoryNode, AlbumOrganizerSettings)
├── service/                      # Business logic (Scanner, Thumbnail, Organize, Archive, Enhancement)
│   └── enhancement/              # AI providers + EnhancementConfig + ImageUtils
├── repository/                   # Data persistence (ConfigRepository, SnapshotRepository, RecycleBinRepository)
├── task/                         # Background tasks (ScanTask)
└── util/                         # Utilities (ErrorDialog, FormatUtils, FileTrash, Constants, AppDirs)
```

---

## Configuration

- App settings: `~/.config/album-organizer/album-organizer-config.ini`
- Snapshot cache: `~/.config/album-organizer/cache.json.gz`
- Thumbnails: `~/Library/Caches/album-organizer/thumbnails/` (Mac) / OS-equivalent
- Logs: `~/Library/Logs/album-organizer/` (Mac) / OS-equivalent
- Reports: `~/Library/Logs/album-organizer/reports/` (Mac) / OS-equivalent
- AI-Generated output: `~/AlbumAiGenerated/` (always)

---

*VibeCoded by Noam Singer*
