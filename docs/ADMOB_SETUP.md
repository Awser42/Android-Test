# AdMob banner setup

The counter displays one anchored adaptive banner in a dedicated footer below the controls. The banner does not cover the counting button. Counting, names, targets and saved counts continue to work without an internet connection.

## IDs and build variants

The owner supplied both production IDs separately:

- AdMob **app ID**: `ca-app-pub-5696841849162209~6162796310`.
- **Banner ad unit ID**: `ca-app-pub-5696841849162209/7022917306`.

App IDs use `~`; ad unit IDs use `/`. An app ID cannot be derived from an ad unit ID. These identifiers are public configuration values, not passwords or signing credentials.

| Build | App ID | Banner unit |
| --- | --- | --- |
| `debug` | Google's sample app ID | Google's test anchored adaptive banner |
| `preview` | Google's sample app ID | Google's test anchored adaptive banner |
| `live` | Owner's app ID | Owner's banner unit |
| `release` | Owner's app ID | Owner's banner unit |

Debug and preview always use `ca-app-pub-3940256099942544~3347511713` and `ca-app-pub-3940256099942544/9214589741`, with `ADMOB_TEST_ADS=true`. They never request the owner's live ad unit. Live and release use the owner's production IDs with `ADMOB_TEST_ADS=false`; they never substitute sample IDs or a fake banner when a live ad is unavailable. Use the optimized preview APK for repeated ad-interaction testing. Do not click your own live ads to test revenue or serving.

Live and release default to the supplied production IDs. Optional Gradle properties or environment variables named `ADMOB_APP_ID` and `ADMOB_BANNER_ID` can override them. Overrides must have the correct separators and match the owner's publisher account; malformed or mismatched values cause a clear Gradle configuration error.

```sh
# Installable optimized test APK: always Google test ads
./gradlew assemblePreview

# Installable owner review APK: configured live AdMob IDs
./gradlew assembleLive

# Unsigned production build: uses the owner's real IDs
./gradlew assembleRelease bundleRelease
```

The main public `Tasbih-Counter-Zikr.apk` and install ZIP contain the live review build. The separate `Tasbih-Counter-Zikr-preview.apk` contains the Google test-ad build. Both public variants retain the cloud signing certificate used by earlier public downloads so updates preserve local data. CI builds use their own generated test certificate; they cannot replace a public APK signed with the cloud key. Use the same download source for subsequent updates. Uninstalling or clearing app data removes local counts.

Live is an installable review variant signed with a test certificate. Release APKs and app bundles still need the owner's production signing process before publication. Providing an AdMob ID does not publish the app, activate the account or app, make an ad unit eligible or guarantee ads/revenue. Real serving remains unverified here. Check AdMob readiness and banner status, internet access, suitable Google Play services and available ad fill before assessing live serving.

## Account readiness

The owner reports that the AdMob dashboard says **“Your account is being verified.”** Account verification is therefore pending and is a concrete external readiness blocker. Google's displayed message says verification generally takes 24 hours and can take up to two weeks in rare cases. This is Google's estimate, not a guarantee of activation or ad fill. App code and the Retry action cannot complete or force Google's verification.

## SDK choice and device support

The project pins Google's official `play-services-ads-lite:25.0.0` and `user-messaging-platform:4.0.0`. Their published artifacts support the existing Android 6.0/API 23 minimum. Lite 25.0.0 delegates to Ads API 24.8.0; explicit UMP 4.0.0 overrides its older transitive UMP version. The current full Ads 25.5.0 artifact requires API 24 and contains more SDK code, so it is not used here.

Lite loads ad implementation from Google Play services. A device without suitable Google Play services can count normally but may not display banners. A successful UI test on an AOSP emulator does not prove ad serving. Final APK size must be measured after shrinking; adding ads and consent libraries increases it.

## Consent and privacy options

The counter refreshes consent information through Google's User Messaging Platform when it becomes visible. If a consent form is required, UMP presents it. Ads initialize and load only when `canRequestAds()` permits requests; a failed consent refresh can reuse valid consent retained from an earlier session. The counter exposes status and a manual **Retry** action for recoverable failures, including a fresh consent refresh when needed. There is no app account or Google login flow.

When UMP marks privacy options as required, the app exposes **Ad privacy** to reopen the choices. The banner is cleared while choices are changed, and requests are gated again afterward. Configure the appropriate privacy messages under AdMob's **Privacy & messaging** for the production app and review the audience, regions and disclosures that apply to your distribution.

The SDK's manifest components and permissions remain intact, including its process-wide initialization provider. The SDK adds network and advertising-related permissions; see the app's [privacy policy](PRIVACY.md) and the merged production manifest. Explicit SDK initialization and ad loading are controlled by the counter's consent gate. Google's documented SDK data practices apply to the app.

## Testing and lifecycle

`BannerAds.factory` lets tests inject a fake controller. Robolectric defaults to a no-op controller so JVM/screenshot tests do not initialize Mobile Ads or make ad/consent network requests. Test fixture banners in screenshots represent layout, not proof that a real ad was served.

The live controller calculates adaptive size from the actual footer width, replaces the banner after width/orientation changes and pauses/resumes/destroys the AdView with its Activity. Theme changes reparent the existing loaded or pending banner rather than requesting another ad at the same width. New consent forms, initialization and ad loads wait while the counter is paused. Initialization happens in a background thread; view manipulation happens on the main thread.

Following Google's sample, the controller attaches the AdView before requesting an ad. It remains invisible while loading, then becomes visible on success. Failure status covers consent refresh, network/ad-load errors, no fill and missing suitable Google Play services. The counter remains usable. Manual **Retry** waits 60 seconds after a failure and shows the remaining delay; it does not reload an already loaded ad. Retry does not activate or approve an AdMob account/app, fix an invalid unit or force ad inventory to be available. There is no automatic background or resume retry loop after a failure. Actual serving remains unverified.

The CI live-APK smoke disables and verifies WiFi and mobile data before installing or opening the live build. It uses native coordinate taps to exercise counting, snapshots and header controls without owner ad impressions. XML, screenshots and a JSON result are uploaded with the emulator reports. These offline UI checks do not test live ad fill or account approval.

Official references:

- [Lite metadata](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-ads-lite/maven-metadata.xml) and [published Lite 25.0.0 POM](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-ads-lite/25.0.0/play-services-ads-lite-25.0.0.pom).
- [Google's banner and consent sample](https://github.com/googleads/googleads-mobile-android-examples/tree/eac5db1a64ff2858676c98889c275cef926e3ab6/kotlin/admob/BannerExample).
- [AdMob Android privacy guide](https://developers.google.com/admob/android/privacy).
- [Anchored adaptive banners](https://developers.google.com/admob/android/banner/anchored-adaptive).

SDK compatibility and APIs were verified from Google's published artifacts and official sample source. The developer-site pages were unavailable through this cloud environment's network proxy when this integration was prepared.
