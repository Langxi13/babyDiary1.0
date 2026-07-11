# Changelog

## 2026-07-11

- Reworked the phone UI across authentication, home, diaries, drafts, timeline, calendar, albums, anniversaries, AI reports, and profile pages.
- Added a shared mobile foundation for safe-area gutters, 44px touch controls, narrow-screen overflow protection, dialogs, messages, date pickers, and upload targets.
- Refined the mobile app shell with a compact edge-to-edge tab bar, icon-based secondary navigation, route preloading, scroll-locked sheets, and automatic tab-bar hiding while the software keyboard is open.
- Replaced the diary list's mobile range calendar with two independent date fields and corrected the calendar page's duplicated month control and wrapped navigation buttons.
- Consolidated login and registration styling, improved mobile autocomplete and input sizing, and removed their duplicated page CSS.
- Corrected new anniversary dates to use the local calendar day instead of UTC truncation.
- Added mobile layout regression tests and multi-viewport browser checks covering 320px through 768px layouts.

## 2026-07-10

- Prepared a privacy-safe public source tree with no production credentials, runtime data, private domains, or server-specific paths.
- Required database, invitation, JWT, and AI encryption secrets to be supplied through environment variables.
- Added local Compose services, public setup documentation, contribution and security policies, CI, and dependency update configuration.
- Made operational scripts resolve the repository root dynamically and load health-check targets from private runtime configuration.

Earlier private deployment notes are intentionally not included in the public repository because they contain environment-specific operational details.
