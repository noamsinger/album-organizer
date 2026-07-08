# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

```bash
mvn javafx:run                             # fastest dev loop — no packaging
mvn compile                                # compile only
mvn package -DskipTests                    # fat JAR + copies deps to target/lib/
mvn test                                   # run all tests
mvn -Dtest=ScannerServiceIntegrationTest test   # run a single test class
bash run.sh                                # package + launch via java directly
bash build.sh                              # macOS .app bundle + .dmg (requires jpackage, sips, iconutil)
```

Main class for `mvn javafx:run`: `com.albumorganizer.Launcher`  
Fat JAR entry: `com.albumorganizer.AlbumOrganizerApp`  
JavaFX version: 21.0.7 (Java 17 source/target)

---

## Architecture

### Three-tier: UI → Service → Repository

```
controller/          UI layer (JavaFX controllers + dialogs)
service/             Business logic (no JavaFX dependencies)
  enhancement/       AI-provider subsystem
repository/          Persistence (typed JSON files + gzipped JSON snapshot)
model/               Pure data records/classes
task/                JavaFX Task wrappers for background work
util/                Stateless helpers
```

### Data flow — scan

1. `MainController` creates a `ScanTask` (JavaFX `Task<ScanResult>`)
2. `ScanTask` calls `ScannerService.scan()`
3. `ScannerService` delegates to either `FullScanWithHashStrategy` (SHA-1, smart rehash on mtime change) or `QuickScanStrategy` (no hashing)
4. Both strategies call `DirectoryScanService` for recursive file enumeration and `MetadataService` for EXIF extraction
5. `ArchiveScanService` handles ZIP/RAR as virtual folders (same `DirectoryNode` tree model)
6. Results cached by `SnapshotRepository` (`~/.config/album-organizer/cache.json.gz`, gzipped JSON)

### Data flow — AI enhancement

1. `MainController` opens `EnhancementDialog` (pass `EnhancementConfig` + `debugProtocol` flag)
2. `EnhancementDialog` builds `EnhancementRequest` (input path, prompt, target size, output dir, params map, customOutputPath)
3. Provider selected by user; `provider.enhance(request)` called on background thread via JavaFX `Task`
4. All providers convert input to JPEG in-memory via `ImageUtils.loadAsJpeg()` before sending
5. Provider returns `EnhancementResult` (success/failure, output path, raw request/response bodies)
6. If `debugProtocol` is enabled, `MainController` shows `DebugProtocolDialog` after `showAndWait()` returns

### Enhancement provider interface

```java
interface EnhancementProvider {
    String getName();
    String getShortId();          // used in output filename: {base}_{shortId}-{epoch}.jpg
    boolean isConfigured();
    boolean supportsPrompt();
    EnhancementResult enhance(EnhancementRequest request) throws IOException;
}
```

Providers: `StabilityAIProvider`, `OpenAIDalleProvider`, `GeminiProvider`, `GrokProvider`,
`StableDiffusionLocalProvider`, `ComfyUIProvider`, `InvokeAIProvider`, `RealEsrganProvider`

All HTTP providers build a `cleanBody` string with base64 image data replaced by a placeholder before logging/storing.

`EnhancementResult` fields: `outputPath`, `success`, `errorMessage`, `technicalDetail`, `rawRequestBody`, `rawResponseBody`

### Output filename pattern

- **Regular enhancement**: `{original-basename}_{providerShortId}-{epoch-seconds}.jpg`  
  Example: `photo_Grok-1749123456.jpg`
- **Clipboard enhancement**: `Clipboard-{providerShortId}-{epoch-seconds}.jpg`  
  Example: `Clipboard-Grok-1749123456.jpg`

The filename shown in `EnhancementDialog` at the moment the user clicks Enhance is exactly the filename written to disk (`customOutputPath` in `EnhancementRequest`). The epoch is refreshed on click so the displayed name matches reality. For clipboard, the filename is pre-set by `MainController` via `dialog.setInitialOutputFilename(...)` and the `↺` button resets it to the auto-generated pattern.

The clipboard temp input is written to the system temp directory (not `aiDir`) so it never appears in scans or the directory tree.

### Prompt system

- `NamedPrompt` record: `int id`, `String title`, `String prompt`
- `id < 0` = built-in default (cannot be deleted or edited); `id > 0` = user-created
- Default prompts defined in `ImageEnhancementService.DEFAULT_PROMPTS` with fixed negative IDs (-1 to -9)
- `ImageEnhancementService.seedPrompts()` prepends any missing defaults (by ID) at load time
- `p.isDefault()` identifies built-ins (`id < 0`); used to bold them in the UI and protect them from deletion
- "Adult Content Filter" (`id = -1`) is checked by default for new users

When multiple prompts are checked, they are joined as:
```
Title 1:
Prompt text.
---
Title 2:
Prompt text.
---
P.S:
Additional free-text prompt
```

### Configuration persistence

Two JSON files, both in `~/.config/album-organizer/`:

| File | Class | Written when |
|------|-------|--------------|
| `album-organizer-settings.json` | `AppSettings` | Settings dialog saved |
| `album-organizer-usage.json` | `AppUsage` | Any user interaction |

**`AppSettings`** holds: organize mode, folder structure flags, resolution thresholds, special folder paths, album folders, all AI provider credentials/flags/URLs, `debugProtocol`.

**`AppUsage`** holds: `fontSizeFactor`, `lastSelectedFolder`, `thumbnailView`, `showArchivesInTree`, `savedPrompts`, `checkedPromptTitles`, `comfyUiCheckpoint`, `comfyUiCheckpoints`, window geometry, `lastScanDateEpochMilli`, `sortField`, `sortAscending`.

Repositories: `SettingsRepository` and `UsageRepository` (Gson, atomic write via `.tmp` + `Files.move`).  
`UsageRepository.load()` runs `migratePromptIds()` → `seedDefaultCheckedPrompts()` on each load.

`AppSettings.toEnhancementConfig(AppUsage)` bridges to the in-memory `EnhancementConfig` record used by dialogs and providers.  
`AppSettings.toOrganizeSettings()` builds the in-memory `AlbumOrganizerSettings`.

### Sort state

Sort column and direction are persisted in `AppUsage` (`sortField` string key + `sortAscending` boolean).  
`sortField` values: `"name"`, `"dateTaken"`, `"date"`, `"type"`, `"duration"`, `"resolution"`.  
Restored in `MainController.initialize()` via `restoreSortMenuSelection()`.  
Saved via `saveUsage()` whenever `applySortOrder()` is called.

### Recycle bin

- Files moved to bin via `deleteFile()` → `moveToBin()` + `RecycleBinRepository.add()`
- Files in the bin show "Permanently Delete" in the context menu instead of "Delete File"
- `permanentlyDeleteFile()` calls `Files.delete()` + `RecycleBinRepository.remove()` after a WARNING confirmation with a red button (Cancel is default)
- Files can be restored via "Restore to Original Location" using `RecycleBinRepository.getOriginalPath()`

### UI patterns

- **Single FXML**: `MainView.fxml` — `SplitPane` with directory tree (left) and file table / thumbnail grid (right)
- **Font scaling**: `MainController.applyFontSize(factor)` sets `-fx-font-size: Xem` on `rootPane`; scale = `2^(factor/4)`. All dialogs apply the same em-scale to their root pane. Avoid hardcoded `px` font sizes anywhere in FXML or CSS.
- **Dialog ownership**: Always pass the primary stage (or a stable window) as owner. Never call `showAndWait()` inside a `Task.setOnSucceeded` callback — the scene may be null. Show dialogs after `dialog.showAndWait()` returns in the calling controller method.
- **Special folders**: Target, AI-Generated, Recycle-Bin are always appended last in the tree with distinct CSS colors.
- **Output directory**: Enhanced images always land in the configured AI-Generated folder.

### Debug protocol dialog

`DebugProtocolDialog` — resizable 4-quadrant layout using nested `SplitPane`s. Each quadrant is a `VBox` with a bold `Label` header. Image data in request/response text is redacted via regex before display. "Copy Analysis Info" button is on the right side of the button bar.
