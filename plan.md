# Album Organizer - Architecture Notes

> This file was the original greenfield implementation plan. The application is now fully built.
> For current architecture, see [design.md](design.md).
> For feature history, see [CHANGELOG.md](CHANGELOG.md).

## Current Status

v1.5.0 — feature-complete for scanning, organizing, archive browsing, AI enhancement, special folders, clipboard enhancement, and first-run setup.

## Key Architectural Decisions

- **INI file for all settings** (`~/.config/album-organizer/album-organizer-config.ini`) — portable, human-editable; migrated from `~/.album-organizer/` on first run
- **Single global snapshot cache** (`~/.config/album-organizer/cache.json.gz`) — compressed JSON, no per-directory cache files
- **OS-appropriate paths** via `AppDirs` — logs/reports to `~/Library/Logs/` (Mac), thumbnails to `~/Library/Caches/` (Mac), with Windows and Linux equivalents
- **JavaFX Task** for all background work — keeps UI thread responsive
- **`#` separator** for archive virtual paths (`archive.zip#entry/name`)
- **EnhancementRequest.outputDir** — providers are output-location-agnostic; output directory injected at call site
- **All AI images converted to JPEG in-memory** before sending to any provider
- **AI output always goes to AI-Generated special folder** — `aiOutputToTargetFolder` setting removed; `MainController` moves enhanced files to `aiGeneratedFolder` after any enhancement
