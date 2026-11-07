# TapLock

TapLock is a local Android MVP. It registers a card UID, then uses that same card to start or stop focus mode. It lists launchable apps installed on the phone so the user can choose any of them. Focus sessions can be set to 30, 60 or 120 minutes; they automatically expire, and the registered NFC card can end a session early. When focus mode is on, its Accessibility Service returns selected apps to Home.

## Build on Windows without Android Studio

Java 17 is already present on the inspected machine. The Android SDK, ADB and Gradle are not installed there, and its current network policy could not reach Google's download server. When network access is available, open PowerShell in this project directory and run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\setup-windows.ps1
```

The script downloads the official Android command line tools (and checks their published SHA-256), SDK Platform 35, platform-tools and Gradle 8.9 into the project’s `.tools` folder, then builds the debug APK. SDK and Gradle downloads are large and require internet access. Accept the Android SDK licenses when prompted. Gradle also needs internet access to download Android and Compose dependencies on the first build.

APK output: `app\build\outputs\apk\debug\app-debug.apk`.

## Install and phone setup

After a successful build, connect the phone with USB debugging enabled and run:

```powershell
.\.tools\android-sdk\platform-tools\adb.exe devices -l
.\.tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

On the phone, open TapLock → Settings → **Open Accessibility settings**, then enable **TapLock app blocking**. Android may show a confirmation screen. Return to TapLock, choose the apps and session length, and tap **Start session** or scan an NFC card. Scan an unregistered card once to register it; later scans toggle focus mode. NFC can also open TapLock when a supported NFC tag is scanned. A timed session ends automatically; scanning the registered card or pressing **Unlock now** ends it early.

## MVP behavior and limits

- All installed launchable apps are listed. Android's broad package visibility is needed for this sideloaded MVP; Google Play distribution would require reviewing its package visibility policy.
- NFC card UID is stored locally. The app never writes to the card.
- The accessibility service places a gray TapLock screen over a selected app as it opens, then sends the phone to Home. Android may delay or restrict Accessibility behavior, and the user can disable the service or reopen an app. This is a focus aid, not a tamper-proof blocker. TapLock cannot gray the app icon in the phone launcher or suspend another app at the operating-system level without managed-device privileges.
- A device without NFC can still open the UI, but cannot register or scan a card.
- NFC detection and blocking must be verified on the real phone. Some phone models/regions do not include NFC.
- The app itself was not built or installed in the current environment because Android SDK, Gradle, ADB and outbound download access were unavailable.
