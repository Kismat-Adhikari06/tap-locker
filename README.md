# TapLock

TapLock is a local Android focus planner and app blocker. The **Today** tab has a week strip and a daily timeline. Add a plan with a time, title, and short note; the most recent plan for today is highlighted in blue. Tap a plan to edit or delete it. Plans are stored locally on the phone.

The **Focus** tab keeps the app blocker: choose installed apps, start a 30, 60, or 120 minute session, and use an NFC card to register or toggle focus mode. During an active session, the Accessibility Service shows a gray TapLock screen over a selected app and returns the phone to Home. Android still allows Accessibility to be disabled, so this is a focus aid rather than a tamper-proof blocker.

## Build on Windows without Android Studio

Java 17, Android command-line tools, Android SDK Platform 35, platform-tools, and Gradle 8.9 have been set up for this workspace. To rebuild, open PowerShell in the project folder and run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\setup-windows.ps1
```

The script builds the debug APK at `app\build\outputs\apk\debug\app-debug.apk`. Internet access is needed for dependency downloads the first time. Accept Android SDK licenses if prompted.

## Install on a phone

Connect the phone with USB debugging enabled. In PowerShell, run:

```powershell
.\.tools\android-sdk\platform-tools\adb.exe devices -l
.\.tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

On the phone, enable **TapLock app blocking** in Android Accessibility settings. Then select apps and a session length in the **Focus** tab. NFC requires a phone with NFC hardware; TapLock reads a card UID but does not write to the card. NFC scans work when TapLock is open, and supported tags may open the app from the background.

## Limits

- The planner is a simple local daily schedule. It does not yet create notifications or automatically start a focus session when a plan begins.
- App blocking depends on Android Accessibility and may be bypassed by disabling the service or reopening an app.
- The app cannot gray an app's launcher icon or suspend it at the OS level without managed-device privileges.
- All installed launchable apps are listed. Broad package visibility is suitable for this sideloaded MVP; publishing through Google Play would require reviewing its package visibility policy.
