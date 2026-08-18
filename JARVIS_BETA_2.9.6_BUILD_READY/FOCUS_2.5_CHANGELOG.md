# JARVIS BETA 2.7 Focus Changelog

## Added
- Focus Center UI integrated into JARVIS.
- Countdown focus sessions with persistent wall-clock state.
- Strict mode.
- Pause/resume for non-strict sessions.
- Subject tags.
- Focus session history and daily totals.
- Daily focus goals.
- Local screen-time reports using Android UsageStats.
- Optional Accessibility app blocker.
- Daily app usage limits.
- Weekly focus-plan storage.
- Local focus-room mode.
- Local productivity coach.
- Privacy-preserving blocker disclosure.

## Deliberately not faked
- No hidden screen scraping.
- No remote leaderboard without a backend.
- No system-wide website interception without a proper VPN/browser implementation.
- No uninstall prevention or security-grade anti-bypass claims.

## BETA 2.5.1 — Always-On Regain-style Adult Protection

- Added a local Accessibility-based adult-site blocker for common Android browsers.
- The service inspects browser address-bar/URI-like accessibility nodes only; it does not read arbitrary screen content, messages, passwords, or keystrokes.
- Added a bundled conservative seed list of adult domains and URL indicators.
- Added a blocking screen with no disable/unblock control.
- No VPN, proxy, or remote traffic routing is used, so the feature does not intentionally add network latency.
- Adult Protection has no JARVIS UI switch to stop it. The protection is active whenever the required Accessibility Service is enabled.
- This is a normal-app/Accessibility blocker, not an absolute device-security guarantee. A user who disables the Accessibility Service, force-stops/uninstalls JARVIS, or changes the device environment can bypass it.
