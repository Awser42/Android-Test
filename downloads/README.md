# Download Tasbih Counter - Zikr

Version **1.8.0 / build 11**, Android 6.0/API 23 or newer.

- [Store submission APK](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-store.apk): production-signed, 2,420,104 bytes (about 2.4 MB), owner's live AdMob IDs. Use this for the first store upload.
- [Phone update APK](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr.apk): 2,419,652 bytes, retains the previous public review certificate. Update without uninstalling to preserve counts.
- [Install ZIP](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-install.zip): contains the phone-update APK.
- [Complete source ZIP](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-source.zip): Gradle wrapper, app code/resources, tests, scripts, CI and documentation. Extract and open the Android-Test folder in Android Studio.
- [Publishing ZIP](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-publishing.zip): icon PNGs, listing text, privacy policy and app-ads.txt entry.
- [Amazon landscape screenshots ZIP](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-Amazon-Screenshots.zip): four designed 1920 x 1200 feature PNGs using the actual native app UI. Extract and upload the PNGs individually in filename order. [Preview and provenance](../publishing/amazon-screenshots/README.md).
- [Google test-ad preview APK](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-preview.apk): use for repeated ad-interaction testing.

The store APK has a new production certificate and cannot normally update an installed phone-review APK. Keep the separately delivered **private signing backup** for future production releases. It is excluded from this public repository, source and publishing ZIPs. GitHub Actions uses another generated review certificate. Uninstalling or clearing app data removes local counter data.

The full Google Mobile Ads SDK 25.0.0 replaces Lite and bundles the ad implementation, removing the app's mandatory Google Play services preflight. Both live and store builds use app ID ca-app-pub-5696841849162209~6162796310 and banner ca-app-pub-5696841849162209/7022917306. Only preview/debug use test IDs. The owner reports the AdMob account is ready; actual serving remains unverified and still depends on app eligibility, consent, network and available fill. A failed live ad is never replaced with a sample banner. Counting remains available offline.

81 JVM tests passed with zero failures/errors/skips; lint reported zero errors and nine warnings. All required APK/bundle targets compiled. Tests include count persistence, saved snapshots, responsive header layout and absent-GMS consent/foreground controller behavior. These checks do not establish real Fire-device compatibility or live ad fill. Amazon Appstore's general Android distribution ended in August 2025; use the [Amazon/Fire publication guide](../docs/AMAZON_APPSTORE.md) and check the current console.

16 saved color themes, day/night, display light and haptics remain available. Fresh installs have no target or account/login; every tap saves automatically, and Save count creates a separate snapshot.

| Download | Bytes | SHA-256 |
| --- | ---: | --- |
| Tasbih-Counter-Zikr-install.zip | 2,170,631 | `cd149a2907869785386b5fcd37fb48e14b9de0dd6d360487078a8135014bb129` |
| Tasbih-Counter-Zikr-preview.apk | 2,419,652 | `1d29946b6120a3e90d400ce371cbd8ed195628b915583a59ba9dd4f892da31c1` |
| Tasbih-Counter-Zikr-publishing.zip | 210,831 | `52e0056c040d4cd1738196fa421895424914fdeefe27ae0cca2a4a0a2f72a529` |
| Tasbih-Counter-Zikr-source.zip | 861,013 | `f69eb0bb067968a09ac78afa9a1cb17f8a482d6818cac86b6c053411edddf6b3` |
| Tasbih-Counter-Zikr-store.apk | 2,420,104 | `84b9ed71fdff67a4f78410fbba9a5cb3354f68e00efa253498efa2227e333940` |
| Tasbih-Counter-Zikr.apk | 2,419,652 | `e4ec311a7211f0db1d7d1b69c42b5b1de2006e35c129dbf888447e36009a2402` |
