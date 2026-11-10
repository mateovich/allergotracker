# Maintenance

Allergotracker is a small personal project. This document sets honest expectations about how it is maintained.

## Scope and support

- The project is maintained on a best-effort basis by one person. There is no service level agreement.
- Bug reports and feature ideas are welcome as GitHub issues. Include the app version (Settings → Apps → Allergotracker), your Android version and steps to reproduce. **Never attach exported data or screenshots that contain personal health information** — describe or anonymize it instead.
- Support is limited to the latest released version. Older versions get no fixes.
- Allergotracker is not a medical product. Issues asking for medical advice or diagnosis cannot be answered.

## Issue handling

- Issues are read when time allows, with no guaranteed response time.
- Data-integrity bugs (lost, corrupted or wrongly linked records) are the highest priority, followed by crashes, then usability problems, then new features.
- Requests that would add network access, accounts, analytics or proprietary SDKs are out of scope by design.

## Releases

- No fixed cadence. A release is cut when a meaningful set of changes is verified.
- Every release is a signed APK attached to a GitHub Release, with its SHA-256 checksum and signing certificate fingerprint in the notes.
- Versions are recorded in [CHANGELOG.md](CHANGELOG.md).
- Database changes always ship with a migration and migration tests, so upgrading keeps your data.

## Security and contact limitations

- The app has no network surface and requests no permissions, which limits the attack surface to the local device and to files you choose to import.
- Please report suspected vulnerabilities through a private GitHub security advisory on this repository rather than a public issue. Expect a best-effort response, not a guaranteed fix timeline.
- The signing key is held privately by the maintainer. Only APKs signed with the certificate listed in the README are official.

## Contributions

There is no contribution process or code of conduct at this project's current scale. Issues are welcome; unsolicited large pull requests may not be reviewed.
