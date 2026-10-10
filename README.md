# Tasbih Counter - Zikr

A simple Android counter with a large digital display, a dark counter body and silver count/reset buttons. Choose from 16 color themes with light or dark backgrounds. Built with Kotlin and Android's drawing APIs, with an AdMob banner at the bottom.

## Use the app

Tap the large silver button to count anything you like. The current count saves automatically and returns when you close and reopen the app; you do not need to press **Save count** to keep it. **Save count** creates a separate snapshot, and **Saved counts** lets you restore or delete those snapshots. Use the small reset button to start again after a confirmation.

A fresh install starts with no target. If you want one, choose 25, 50, 100 or a custom number; you can turn the target off again. Existing counts and target settings are preserved when upgrading. The default name is simply **Counter**, and you can edit it. Counter names, totals and counting settings stay on your device and are not passed to the advertising SDK. Counting and saved counts work offline without an account; advertisements and consent messages need internet access.

Open **Quran** for a separate offline, ad-free reader containing all 83 verses of Surah Ya-Sin (36:1–83), the complete Ayat-e-Karima verse (21:87) and Ayatul Kursi (2:255). The Arabic text is bundled from an attributed Tanzil snapshot; see [Quran text sources and licensing](docs/QURAN_TEXT_SOURCE.md). The counter keeps its neutral name and optional target.

Features include:

- A physical counter design with a green display and a large silver tap button.
- **Theme colors** and display-light controls in the header, with compact haptics, target and counter-name controls below.
- No target on a fresh install; optional targets of 25, 50 or 100, a custom number, or target off.
- An editable counter name with no preset categories.
- 16 named color themes, saved independently of dark/night mode and the display light.
- A spring-like button release and a brief display glow after counting.
- Optional haptics and reduced-motion support using Android's animation setting.
- Automatic count persistence, optional target progress and saved snapshots you can restore or delete.
- A confirmation before resetting your current count.
- A layout that adapts to window size, screen insets and text size, with scrolling when needed.
- An AdMob banner at the bottom, separate from the count and save controls.
- Ad status messages and a manual **Retry** action for recoverable ad/consent failures.
- A separate offline, ad-free Quran reader for complete Ya-Sin, Ayat-e-Karima and Ayatul Kursi.

The app requires Android 6.0 or newer (API 23). It is free to use and includes advertising. See [the privacy policy](docs/PRIVACY.md) for local counter data, advertising SDK data and consent choices.

## App preview and artwork

The screenshot below shows the implemented Android screen. [Light theme screenshot](docs/media/counter-light.webp) · [Tap animation preview](docs/media/counter-tap.gif).

See the [Ocean theme](docs/media/counter-ocean.webp), [Rose theme](docs/media/counter-rose.webp) and [Theme colors picker](docs/media/theme-colors.webp).

The separate offline reader is shown in these native Android captures: [Surah Yaseen](docs/media/quran-yaseen.webp), [Ayat-e-Karima](docs/media/quran-kareema.webp) and [Ayatul Kursi](docs/media/quran-kursi.webp). The Quran screen has no banner, ad status or ad controls.

<img src="docs/media/counter-dark.webp" alt="Tasbih Counter - Zikr Android app in its dark theme" width="320">

The bottom **TEST AD** area in the host-rendered screenshots is a layout fixture, not an advertisement served by Google. These screenshots show the banner placement; they do not verify ad serving.

The launcher icon is generated artwork, optimized for the app. The app's counter and animations are drawn at runtime. The tap preview uses eight actual Android view renders captured at animation frames; it is kept in the documentation and is not bundled in the APK.

## Download and test an APK

Download the [APK with the owner's live AdMob IDs](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr.apk), or [download the install ZIP](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-install.zip) and extract the APK. For repeated ad-interaction testing, use the separate [Google test-ad preview APK](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-preview.apk). See [download details](downloads/README.md).

After this repository is pushed to GitHub, open **Actions → Android**, select a successful run and download **Tasbih-Counter-Zikr-apks** under **Artifacts**. Extract the ZIP on your phone or computer.

- **Tasbih-Counter-Zikr.apk** and **Tasbih-Counter-Zikr-live-ads.apk** are optimized, installable review builds using the owner's live AdMob IDs.
- **Tasbih-Counter-Zikr-preview.apk** is the optimized preview APK with Google's test ads, suitable for repeated ad-interaction testing.
- **Tasbih-Counter-Zikr-debug.apk** is the development APK used for emulator testing, also with Google's test ads.
- **Tasbih-Counter-Zikr-release-UNSIGNED.apk** is an optimized unsigned build configured for production ads. It needs production signing before installation or distribution.
- **Tasbih-Counter-Zikr-release-UNSIGNED.aab** is the unsigned production app bundle for the owner's signing and Play Console release process.

Allow installation from the browser or file manager that opens the APK. The public live and preview downloads use the same cloud test certificate as earlier public APKs, so install them as updates without uninstalling. GitHub Actions generates a different test certificate: use the committed public downloads again when updating an APK installed from those links. Mixing public and CI APKs can fail signature checks. Uninstalling or clearing the app's data removes local counter data. These review APKs are not production-signed Play Store releases.

Debug and preview builds always use Google's official test application and banner IDs; live and release builds use the owner's configured production IDs. Do not click your own live ads; use preview or debug for ad-interaction testing. Real ad serving remains unverified here and needs internet access, suitable Google Play services, an eligible AdMob app/ad unit and available ad fill. The counter shows ad status and a **Retry** action for recoverable consent/network failures. Retry cannot approve an AdMob account or guarantee ad fill. Counting remains available when an ad fails. Ads appear only on the main counter screen; the Quran reader does not request or display them. See [AdMob setup](docs/ADMOB_SETUP.md) for configuration and consent.

The workflow builds the live and test-ad APK variants and runs JVM tests, lint, API 35 emulator tests and offline process-restart/same-APK reinstall checks. Open **Actions → Android** for the current push's status and downloadable reports. The JVM suite includes actual-window touch checks for Save/history/restore/delete. The emulator job additionally taps the minified live APK offline to check saved-snapshot persistence, Restore/Delete, the three ad-free Quran readings and Back preserving the counter. Offline live-variant tests do not establish real ad serving. The workflow uses standard `ubuntu-latest` GitHub-hosted runners, which GitHub currently provides free for public repositories. This is automated testing; GitHub Actions does not provide a phone screen you can interact with in your browser.

### Roman Urdu mein

1. GitHub par **Actions → Android** kholo aur successful run select karo.
2. **Tasbih-Counter-Zikr-apks** download karke ZIP kholo.
3. Phone par **Tasbih-Counter-Zikr.apk** install karo.
4. Bara button dabao: har tap par count barhega aur automatically save hoga. App band karke kholo to count wapas aayega; **Save count** alag snapshot rakhta hai.
5. Pehli install par target set nahi hota. Apni marzi se target chuno ya off rakho.
6. **Theme colors** se 16 rangon mein se apna theme chuno. Live ads par khud click na karo; ad buttons test karne ke liye **preview** APK use karo.
7. **Quran** mein poori Surah Ya-Sin, Ayat-e-Karima aur Ayatul Kursi offline aur baghair ads parho.

## Develop locally

Required tools:

- JDK 21.
- Android SDK platform 36 and build tools 36.0.0.
- The included Gradle wrapper (Gradle 8.13); no system Gradle is required.

The project pins Android Gradle Plugin 8.13.2 and Kotlin 2.2.21, with Google Mobile Ads Lite 25.0.0 and User Messaging Platform 4.0.0 for the banner and consent flow. Set `ANDROID_HOME` to your Android SDK directory, or create an untracked `local.properties` containing `sdk.dir=/your/sdk/path`. Do not commit machine-specific paths or signing credentials.

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
./gradlew testDebugUnitTest lintDebug assemblePreview
```

The optimized live review APK is written to `app/build/outputs/apk/live/app-live.apk`; the Google test-ad preview is written to `app/build/outputs/apk/preview/app-preview.apk`.

```sh
# Development APK and production unsigned APK
./gradlew assembleDebug assembleRelease

# Installable owner review APK with live AdMob IDs
./gradlew assembleLive

# With an Android emulator or USB-connected Android device running
./gradlew connectedDebugAndroidTest

# Install the preview on a connected device
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

Local unit and Robolectric tests run on the JVM without an emulator or hardware acceleration. The built-in Android instrumentation tests need a running device; the GitHub workflow starts an accelerated emulator for those tests. The cloud development environment may not have `/dev/kvm`, so run the JVM tests locally and use GitHub Actions for emulator checks.

HTML reports appear under `app/build/reports/tests/`, `app/build/reports/androidTests/` and `app/build/reports/lint-results-debug.html`.

## Build validation

Version 1.6.0 (version code 9) passed all 93 JVM tests with zero failures, errors or skips: storage, themes, counting, actual-window Save/history gestures, real-controller ad recovery, offline Quran text and native screen layouts. All reports belong to this build. Android lint reported zero errors and 15 warnings. Debug, live, preview, unsigned release and instrumentation APKs, plus the unsigned release app bundle, compiled successfully. The signed live APK is 435,235 bytes (425.0 KiB); the test-ad preview is 435,236 bytes. Both passed v1/v2 signature checks using the same certificate as previous public downloads. The production/test app and banner IDs, test-ad flags and all packaged Quran assets were checked. Native Quran captures show readable Arabic and diacritics; narrow/large-font and short-landscape checks passed. Local KVM/device execution is unavailable; check the current GitHub Actions run for the separate real-emulator results. Actual ad serving remains unverified, and the owner reports that AdMob account verification is pending.

## Play Store listing draft

[PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md) contains the proposed title and descriptions. It is a publication draft; the app has not been published to Google Play. Production signing and the required Play Console release setup remain separate steps.

## Keeping the APK small

The counter is drawn with Android Canvas instead of bundling a UI framework or video animation. Tap animations use native Android animation APIs. Preview and release builds shrink unused code and resources with R8, and the launcher icon is compact. The advertising SDK adds dependencies and increases the APK size. Measure the actual build outputs rather than assuming the size of the earlier build without advertisements.
