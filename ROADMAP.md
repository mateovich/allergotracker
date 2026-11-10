# Roadmap

This roadmap lists direction, not promises. Nothing here has a delivery date, and priorities can change.

## Now: 1.0 and stabilization

- Publish and verify the first signed APK release on GitHub.
- Fix data-integrity issues, crashes and usability problems found by real use.
- Keep the offline-first, no-permissions posture intact.
- Keep the build reproducible and free of proprietary SDKs so alternative distribution (for example F-Droid) stays possible.

## Next: helping users read their own data

These are the most likely next areas, in no guaranteed order:

- **Possible-cause hypotheses** — show ranked candidates for outbreak triggers based on the records, always framed with explicit uncertainty and never as a diagnosis, with a way to review the evidence behind each hypothesis.
- **Temporary exclusion suggestions** — optional, reversible suggestions to exclude items for a while to improve signal quality.

## Later / ideas

- **Photo-to-compound** — draft a compound from a photo of an ingredient list, always with mandatory human review and valid proportion suggestions.
- **Aliases** for ingredients and compounds, to find the same item under different names.
- **Multi-language interface** for the main experience.

## Deferred technical work

- Share catalog search and ranking logic between the ingestion create and edit screens.
- Clean up route and ViewModel plumbing left over from earlier flows.
- Export/import support for the "provisional compound" marker (it is currently local only).

## Out of scope

Accounts, cloud sync, ads, analytics, subscriptions and medical diagnosis.
