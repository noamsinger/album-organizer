# Album Organizer - Architecture Notes

> This file was the original greenfield implementation plan. The application is now fully built.
> For current architecture, see [design.md](design.md).
> For feature history, see [CHANGELOG.md](CHANGELOG.md).

## Current Status

v1.4.0 — feature-complete for scanning, organizing, archive browsing, and AI enhancement.

## Key Architectural Decisions

- **INI file for all settings** (`~/.album-organizer/album-organizer-config.ini`) — portable, human-editable
- **Single global snapshot cache** (`~/.album-organizer/cache.json.gz`) — compressed JSON, no per-directory cache files
- **JavaFX Task** for all background work — keeps UI thread responsive
- **`#` separator** for archive virtual paths (`archive.zip#entry/name`)
- **EnhancementRequest.outputDir** — providers are output-location-agnostic; output directory injected at call site
- **All AI images converted to JPEG in-memory** before sending to any provider
