# Amazon Appstore publication

This app targets touch-screen **Fire tablets**. Amazon discontinued its Appstore for general Android devices on **20 August 2025**; its Fire-tablet and Fire TV stores continue. This counter has not been designed or tested for Fire TV remote navigation, so do not select Fire TV compatibility for this build.

Amazon's announcement and submission references are linked below. Their pages could not be fetched from this workspace because the network proxy returned HTTP 403. Confirm current device, asset and submission requirements in the Amazon Developer Console before uploading.

## APK and complete source

`Tasbih-Counter-Zikr-store.apk` is the release APK signed with a new, long-lived production key for the first store submission. `Tasbih-Counter-Zikr.apk` retains the existing cloud **test certificate** for direct phone updates and review. Both use the owner's live AdMob IDs. Unsigned release artifacts from CI still need production signing. Neither packaging a ZIP nor supplying AdMob IDs publishes the app.

The complete source archive should contain the Gradle wrapper, application code, resources, tests, CI scripts and documentation. It must exclude keystores, signing passwords, `local.properties`, Gradle caches and generated build directories. A source ZIP is for rebuilding; upload the signed APK to the store, not the source archive.

Keep package name `com.awser.counter` and increment the version code for updates. Preserve the encrypted production keystore and its private credential backup outside the public repository and source ZIP. Future production builds need that same key. The store and phone-review certificates differ; the store APK cannot normally update an installed review APK without a signature conflict. Uninstalling removes local counter data. Amazon may apply its own signing during distribution; follow its console guidance for store updates.

The delivered version 1.8.0/code 11 store APK is 2,420,104 bytes. Its SHA-256 is `84b9ed71fdff67a4f78410fbba9a5cb3354f68e00efa253498efa2227e333940`; its production signing certificate SHA-256 is `7a2c55077caa128aaae94a65214239eda4bdf90d0f5f96495f7fd35188385fde`. These fingerprints identify the build and key, and do not establish Amazon approval or actual ad serving.

## Build with the owner's production key

Use JDK 21, Android SDK 36/build tools 36.0.0 and the included Gradle wrapper. The source update targets version **1.8.0 / code 11** with minimum Android API 23. From the extracted repository root, supply these four environment variables through your secure build environment:

- `ANDROID_RELEASE_STORE_FILE`: absolute path to the owner's keystore.
- `ANDROID_RELEASE_STORE_PASSWORD`: keystore password.
- `ANDROID_RELEASE_KEY_ALIAS`: signing-key alias.
- `ANDROID_RELEASE_KEY_PASSWORD`: signing-key password.

Do not put passwords or the keystore in the source ZIP, repository, shell history or shared logs. The variables are environment-only. All four must be supplied together; partial or empty configuration fails. If all are absent, the release remains unsigned. `live` and `preview` retain their test-certificate signing regardless of these variables.

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleRelease

# With all four signing variables supplied, inspect the signed release:
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Without production signing variables, the output is `app/build/outputs/apk/release/app-release-unsigned.apk`. Google Play's app bundle can be built separately with `bundleRelease`; the Amazon submission described here uses an APK.

## Fire-tablet checks and store listing

Test on a supported Fire tablet before declaring it compatible. Fire OS devices can lack Google Play services; the app now uses Google's **full Mobile Ads SDK 25.0.0**, which bundles the ad implementation instead of depending on Lite's Google Play services loader. This removes the app's earlier Lite limitation; it does not establish that an ad was served on Fire or that every Fire device is compatible. Counting and saved counts continue offline.

In the [Amazon Developer Console](https://developer.amazon.com/apps-and-games/console), prepare:

- The production-signed APK and only the Fire-tablet devices you have checked.
- The title **Tasbih Counter - Zikr**, accurate descriptions, category, content rating and ads disclosures requested by the console. [The listing draft](PLAY_STORE_LISTING.md) provides reusable counter descriptions; adapt it to Amazon's fields and limits.
- A support contact, developer website, publicly accessible [privacy policy](https://github.com/Awser42/Android-Test/blob/main/docs/PRIVACY.md), icon and tablet screenshots in the current console's required formats.
- Genuine store screenshots from the shipping app. Documentation images with **TEST AD · Layout fixture** demonstrate layout and must not be presented as actual Google ad-serving evidence.

Submission and review happen in Amazon's console. This repository does not contain proof of Amazon publication or approval.

## AdMob store link and app-ads.txt

The owner reports that the AdMob account is **ready**. Account status, the app's store link, app readiness review, consent and available inventory are separate from actual banner fill. Amazon Appstore has been an AdMob-supported Android store; confirm the Amazon link and this app's current readiness in **your AdMob dashboard**. The current supported-store help page could not be fetched here. Amazon publication alone does not guarantee AdMob approval, eligibility or serving, and it must not be represented as a Google Play listing.

If AdMob requires app verification through `app-ads.txt` for this app, use the developer website recognized for its store listing and follow the instructions shown in AdMob. The file belongs at the website root, such as `https://your-domain.example/app-ads.txt`, not inside the APK or source ZIP. Copy the authorized-seller entry from the owner's AdMob account. For this publisher, confirm that it matches:

```text
google.com, pub-5696841849162209, DIRECT, f08c47fec0942fa0
```

Keep it publicly readable and check AdMob's crawler/verification result. Do not assume that a file on an unrelated domain, or an Amazon-only listing, automatically completes Google's verification. Applicability and store-link recognition must be checked in the dashboard.

The live and release builds retain app ID `ca-app-pub-5696841849162209~6162796310` and banner unit `ca-app-pub-5696841849162209/7022917306`. They never substitute sample ads when a banner is unavailable. Use the separate preview for repeated ad-interaction testing; do not click your own live ads. CI tests the live APK with networks disabled and cannot prove live ad fill.

## Official references

- [Amazon's Appstore changes announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program).
- [Amazon submission process](https://developer.amazon.com/docs/app-submission/understanding-submission.html).
- [Amazon store assets](https://developer.amazon.com/docs/app-submission/appstore-assets.html).
- [Porting Android apps to Fire OS](https://developer.amazon.com/docs/fire-tablets/ft-porting-app-to-fireos.html).
- [Set up an app in AdMob](https://support.google.com/admob/answer/10037806).
- [AdMob app readiness](https://support.google.com/admob/answer/9989980).
- [Set up app-ads.txt](https://support.google.com/admob/answer/9363762).

These links are owner verification references; the blocked pages' current wording and submission field requirements were not verified in this workspace.
