# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow the app's `versionName`.

## How to add an entry

Add changes under `## [Unreleased]` using the headings `Added`, `Changed`, `Fixed` and `Removed`. When releasing, rename `Unreleased` to the version and date, and add the APK SHA-256.

## [Unreleased]

## [1.0] - 2026-11-10

First public release.

APK SHA-256 (`allergotracker-1.0.apk`): `61b794e21a86d95180f775fa0e468015d56d6ab5667559c2e7694786de946dae`
Signing certificate SHA-256: `ec6e022e66bc2a6f63c102d92840d30fa3851329443ed8f4a8820c9369a5c52b`

### Added

- Ingestion, exposure and outbreak records with date and time pickers, intensity, duration and notes.
- Chronological records list with filters by date, type and intensity, and a detail view for every record.
- Reusable ingredients and compounds, with proportions that always total 100%, uniform and decreasing presets, and protection against circular compositions.
- Merge of duplicate ingredients and compounds with impact preview, and reference-aware retirement.
- Versioned JSON export with relations and compounds normalized to base ingredients, and validated import of structured compounds and datasets.
- **Provisional compounds**: save something as a provisional compound from the ingestion form, review the pending list from the Records bar, then complete it as a compound or keep it as a regular ingredient.
- **Make it a compound**: replace an ingredient with a compound, repointing saved records and other compounds in one transaction after an explicit confirmation.
- Editing an ingestion lets you change the linked ingredient or compound by typing and picking a suggestion.

### Changed

- Edit Compound shows component suggestions only once the search text starts matching.
- Ingestions must always point to a saved ingredient or compound; free text is no longer saveable when editing.

### Removed

- Informational banners ("Offline-ready…", "Delete asks for confirmation…", "Reusable … selected…").

### Notes

- Database version 16. Upgrading from earlier development builds is covered by migration tests.
- The app requests no permissions.
