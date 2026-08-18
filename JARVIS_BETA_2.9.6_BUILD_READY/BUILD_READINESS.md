# JARVIS BETA 2.9.6 — Build Ready Notes

## Changes in this package

- Added `testImplementation("junit:junit:4.13.2")` for the existing JVM unit tests.
- Removed the hard-coded `/storage/emulated/0/...` Qwen model path from model auto-resolution.
- Qwen model discovery now relies on the existing remembered-location, SAF folder, import, and best-effort shared-storage mechanisms.
- Project contents are placed at the archive root so GitHub Actions can run Gradle directly from the repository root.

## Gradle bootstrap

This project intentionally uses its checked-in `gradlew` / `gradlew.bat` bootstrap scripts rather than the standard Gradle wrapper JAR. The scripts install/use Gradle 8.9 and do not require `gradle-wrapper.jar`.

Do not replace these scripts with a partial standard wrapper unless the official wrapper JAR is also committed.

## Recommended verification

On a network-enabled development machine with Android SDK 35 and JDK 17:

```bash
./gradlew --version
./gradlew test
./gradlew assembleDebug
```
