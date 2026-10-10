# Download Tasbih Counter - Zikr

[Download APK with the owner's live AdMob IDs](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr.apk)

If your browser blocks APK downloads, [download the install ZIP](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-install.zip), extract it and open the APK inside.

- [Download the Google test-ad preview](https://github.com/Awser42/Android-Test/raw/refs/heads/main/downloads/Tasbih-Counter-Zikr-preview.apk) for repeated ad-interaction testing.
- Version 1.6.0 (build 9), Android 6.0/API 23 or newer.
- Main live APK: 435,235 bytes (425.0 KiB). Safe test-ad preview: 435,236 bytes (425.0 KiB).
- Signed with a debug/test certificate for installation and review; this is not a production-signed Play Store release.
- The main APK and install ZIP use the owner's production AdMob app ID and banner unit. The separate preview uses Google's official test IDs.
- 16 color themes, saved independently of dark/night mode and the display light.
- A separate offline, ad-free Quran reader: complete Surah Ya-Sin (83 verses), Ayat-e-Karima (the complete 21:87 verse) and Ayatul Kursi (2:255). See [Quran text sources and licensing](../docs/QURAN_TEXT_SOURCE.md).
- Fresh installs start with no target. Targets are optional and can be removed without clearing the count.
- Each tap saves automatically; closing and reopening retains the count and settings. **Save count** creates a separate snapshot.
- No account or login is required. Counting and saved counts work offline; ads and consent messages need internet access.

On Android, allow installation from the browser or file manager that opens the APK when prompted. The live and preview APKs from these public links use the same cloud test certificate as previous public downloads. Install them as updates without uninstalling to preserve your local counts and settings.

GitHub Actions artifacts use a different generated test certificate. Use these public downloads for updates to an APK installed from these links; switching to a CI APK may fail signature checks. Uninstalling or clearing the app's data removes local counts.

Real ad serving and AdMob account/app activation have not been verified here. Banners need internet access, suitable Google Play services, an eligible app and ad unit, and available ad fill. The counter shows ad status and offers **Retry** for recoverable failures; it cannot approve an account or guarantee fill. Do not click your own live ads; use the test-ad preview for ad-interaction testing. If an ad cannot load, counting remains available. The separate Quran reader does not request or display ads.

Both APKs passed v1/v2 signature checks using the same certificate as previous public cloud downloads.

| APK | SHA-256 |
| --- | --- |
| Main live-ID APK | `b72f864a02941bad309727ade6410ad28f1220d7ef30972c1e7209bc25a267ae` |
| Safe test-ad preview | `ffa051f4c7da3f48c16084e8a1ae8677d83c25e4f6253f2f9972b0c98c4aa149` |
