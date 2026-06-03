# Changelog

All notable changes to Album Organizer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.0] - 2026-06-03

### Added
- **Special folders**: AI-Generated (`~/AlbumAiGenerated`) and Recycle-Bin (`~/AlbumRecycleBin`) are now independent special folders alongside Target (`~/AlbumTarget`), always shown last in the directory tree
  - Target = purple, AI-Generated = blue, Recycle-Bin = dark red
  - All three created on startup if they don't exist; configurable via File menu
- **File > AI-Enhance from Clipboard**: new menu item that opens the enhancement dialog on the current clipboard image
  - Enabled only when clipboard contains an image and at least one AI provider is configured
  - Output saved as `Clipboard-{provider}-{epoch}.jpg` in the AI-Generated special folder
- **First-run dialog**: when no albums are configured, the app detects and suggests local folders (Pictures, Dropbox, Google Drive, OneDrive, iCloud) and runs a full scan if approved
- **`AppDirs` utility**: centralised OS-aware path resolution (Mac: `~/Library/…`; Windows: `%APPDATA%`/`%LOCALAPPDATA%`; Linux: `~/.local/share/…` / `~/.cache/…`)
- **`LogDirPropertyDefiner`**: Logback `PropertyDefinerBase` that resolves the OS-appropriate log directory at startup before the application code runs

### Changed
- Config directory changed from `~/.album-organizer/` to `~/.config/album-organizer/`; existing config is migrated automatically on first run
- Default special folder paths changed from `~/Target` / `~/AI-Generated` / `~/PictureRecycleBin` to `~/AlbumTarget` / `~/AlbumAiGenerated` / `~/AlbumRecycleBin`
- Log files moved to OS-appropriate path (Mac: `~/Library/Logs/album-organizer/`; Windows: `%APPDATA%\album-organizer\logs\`; Linux: `~/.local/share/album-organizer/logs/`)
- Thumbnail cache moved to OS-appropriate path (Mac: `~/Library/Caches/album-organizer/thumbnails/`; Windows: `%LOCALAPPDATA%\album-organizer\thumbnails\`; Linux: `~/.cache/album-organizer/thumbnails/`)
- Organizing reports moved to OS-appropriate persistent path (Mac: `~/Library/Logs/album-organizer/reports/`; Windows/Linux: same base as logs)
- AI-enhanced images always go to the AI-Generated special folder (the `aiOutputToTargetFolder` setting has been removed)
- Settings dialog: removed "General" tab (AI output location toggle is no longer needed)

### Removed
- `aiOutputToTargetFolder` setting (field, INI key, and Settings UI); AI output always goes to the AI-Generated folder
- Dead `ImageEnhancementService.resolveOutputPath(Path, String, boolean, Path)` overload



### Added
- **Settings — General tab**: new first tab in the Settings dialog with AI output location option
  - "Next to the original file" (default): AI-enhanced files saved alongside the source
  - "In the target folder under 'AI-Generated'": saves to `targetFolder/AI-Generated/`
  - `AI-Generated/` subdirectory created automatically if it doesn't exist
  - Setting persisted in `~/.album-organizer/album-organizer-config.ini` as `aiOutputToTargetFolder`
- **`EnhancementRequest.outputDir`**: optional output directory field threaded through all AI providers
- **`ImageEnhancementService.resolveOutputPath` overload**: accepts `useTargetFolder` + `targetFolder` params

### Changed
- All eight AI providers (`StabilityAI`, `OpenAIDalle`, `Gemini`, `Grok`, `StableDiffusion`, `ComfyUI`, `InvokeAI`, `RealESRGAN`) now respect the configured output directory
- After enhancement, `quickScanFolder` is called on the actual output directory so the new file appears immediately in the correct tree node
- `onSettings()` in `MainController` now also preserves `showArchivesInTree` when settings are saved (previously it could revert to the default)

## [1.3.0] - 2026-05-28

### Added
- **ZIP/RAR virtual folders**: archive files appear as nodes in the directory tree with a 🗄 icon
  - Click an archive node to scan and display its contents in the file table
  - Thumbnails generated for image entries inside archives
  - Double-click an archive entry to extract to a temp file and open it
  - On macOS, images always open in the Preview app
- **`View > Show Archives in Tree`** checkbox: toggle archive nodes on/off; persists across sessions
- **`ArchiveScanService`**: scans ZIP (via `java.util.zip.ZipFile`) and RAR (via Apache Commons Compress)
  - Virtual paths use `archivePath#entryName` convention
  - `extractEntry()` returns raw bytes for a single entry
- **`ThumbnailService.generateThumbnailFromBytes()`**: generates thumbnails from raw byte arrays (used for archive entries)
- **AI Enhancement — multi-prompt checkboxes**: replaced single-select prompt list with checkboxes
  - Multiple prompts can be checked; all checked prompts are concatenated and sent together
  - Checked state persisted across sessions (`checkedPromptTitles` in Enhancement config section)
- **Tooltip on prompt hover**: shows the full prompt text when hovering over a prompt entry
- **Edit/Delete prompt fix**: tracks `lastClickedPrompt` on mouse-click and focus events, ensuring Edit and Delete act on the intended prompt
- **JPEG conversion**: all AI providers now convert input images to JPEG in-memory before sending (`ImageUtils.loadAsJpeg()`)
  - Handles HEIC (via sips), PNG with alpha channel, and applies size/quality fallbacks
- **JSON error parsing**: cloud AI providers (Grok, Gemini, OpenAI) now parse JSON error bodies and display clean error messages instead of raw JSON strings

### Changed
- Archive scanning triggers on tree node click, not only via context menu
- `AlbumOrganizerSettings` gained `showArchivesInTree` field (default `true`)
- `EnhancementConfig` record gained `checkedPromptTitles` field
- `ConfigRepository` persists both new fields (`showArchivesInTree` in Settings section; `checkedPromptTitles` pipe-separated in Enhancement section)

### Dependencies Added
- `org.apache.commons:commons-compress:1.26.1` — ZIP/RAR archive reading

## [1.2.0] - 2026-05-10

### Added
- Thumbnail view with loading spinners and async loading (thread pool = CPU cores)
- Two-pass progress bar for folder navigation: scan phase (0-100%) then thumbnail phase (0-100%)
- Video frame rotation using FFmpeg displaymatrix (reliable rotation detection)
- HEIC thumbnail generation via macOS sips
- LRU thumbnail disk cache at `~/.album-organizer/thumbnails/` (42MB max)
- macOS Dock icon using Taskbar API
- Shutdown timeout (5s) to prevent quit from stalling
- "View Last Organizing Report" button — stays enabled across scans, writes to `/tmp/album-organizer-reports/` on demand
- Organization reports stored compressed in memory (GZIP), written as `.txt` only when requested
- "Do Magic" submenu in folder tree context menu containing "Organize Folder Recursively"
- macOS `.dmg` installer created by `build.sh`
- App icon with transparent corners for macOS (full ICNS with all sizes up to 1024px)

### Changed
- `build.sh` now builds + packages only (no auto-launch)
- Removed `run.sh` and `run.bat`
- macOS handles single-instance natively
- Startup no longer auto-selects a folder
- Video rotation now uses FFmpeg displaymatrix
- OrganizeReportWriter writes to in-memory StringWriter instead of disk

### Fixed
- ProgressBar crash at startup ("A bound value cannot be set")
- Video thumbnails showing upside-down or sideways
- EXIF orientation 5 and 7 now handled for photos
- App stalling on quit

## [1.1.0] - 2026-04-26

### Added
- Smart hash recalculation in Full Scan with Hash — only rehashes new, modified, or unhashed files
- Hash preservation during Quick Scan
- Tree selection preservation across scans and operations
- Context menu items always visible with disabled states for consistent UX

### Changed
- Renamed "Deep Scan" to "Full Scan with Hash"
- Quick Scan skips hash calculation entirely (10-50x faster)
- Context menus use `.setDisable()` instead of `.setVisible()`

### Fixed
- Quick Scan files now appear correctly in directory tree
- Hash data no longer lost when running Quick Scan after Full Scan with Hash
- Tree selection no longer jumps to first album after scans

## [1.0.0] - 2026-04-21

### Added
- Initial release: dual-pane UI, full and quick scan, SHA-1 duplicate detection, file organization, metadata extraction, date estimation from filenames, progress panel, INI-based configuration persistence
