# Publishing files

Use `downloads/Tasbih-Counter-Zikr-store.apk` for the first store submission. The public phone-update APK uses a separate review certificate. See [Amazon publication](../docs/AMAZON_APPSTORE.md) for signing, device and ad-readiness details.

`app-ads.txt` contains the supplied publisher's Google authorized-seller entry. Confirm it in AdMob, then host it at the root of the developer website recognized by AdMob for this app. Bundling this file in an APK or uploading it as a store asset does not verify the website.

The publishing ZIP includes PNG icon exports at 512 and 114 pixels, listing text, the privacy policy and this seller entry. Check the current Amazon Developer Console's image dimensions and required fields. Documentation screenshots with a test-banner fixture demonstrate layout; capture the shipping app on a Fire tablet for the store screenshots.

Private release-signing material is delivered separately and is excluded from all public downloads and source. Retain it for future updates.
