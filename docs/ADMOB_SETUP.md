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

# Production build: owner's real IDs; signing depends on owner key variables
./gradlew assembleRelease bundleRelease
```

The main public `Tasbih-Counter-Zikr.apk` and install ZIP contain the live phone-review build. The separate `Tasbih-Counter-Zikr-preview.apk` contains the Google test-ad build. Both retain the cloud signing certificate used by earlier public downloads so direct phone updates preserve local data. `Tasbih-Counter-Zikr-store.apk` is the production-signed release for first store submission and has a different certificate. It cannot normally update the phone-review APK. CI builds use another generated test certificate. Use the same signing source for subsequent updates; uninstalling or clearing app data removes local counts.

Live is an installable phone-review variant signed with a test certificate. The delivered store release uses a new production key retained privately with its credential backup. Release APKs and app bundles use that signing process only when all four owner signing variables are supplied; otherwise they remain unsigned, including normal CI builds. See [Amazon publication and signing](AMAZON_APPSTORE.md). Providing an AdMob ID does not publish the app, make an ad unit eligible or guarantee ads/revenue. Real serving remains unverified here. Check app readiness, banner status, internet access, device compatibility, consent and available fill before assessing live serving.

## Account readiness

The owner now reports that the AdMob account is **ready**. Actual banner serving has not been verified here. Account readiness does not by itself establish the app's store link, app readiness approval, consent or available fill. Check those separately in AdMob; Retry cannot force approval or inventory.

## SDK choice and device support

The project pins Google's official full `play-services-ads:25.0.0` and `user-messaging-platform:4.0.0`. Their published artifacts support the existing Android 6.0/API 23 minimum. This full SDK includes its local ad implementation and uses Ads API 25.0.0. It replaces the earlier Lite dependency and the app's explicit Google Play services availability gate.

Lite depended on Google Play services to load its ad implementation. The full SDK is needed for the Fire-tablet path where those services may be absent. That change does not guarantee compatibility or serving on every device; verify real Fire-tablet behavior and the app's AdMob store link/readiness. A successful offline UI test does not prove an ad was served. Full SDK packaging increases the APK size; measure the actual shrunk build.

## Consent and privacy options

The counter refreshes consent information through Google's User Messaging Platform when it becomes visible. If a consent form is required, UMP presents it. Ads initialize and load only when `canRequestAds()` permits requests; a failed consent refresh can reuse valid consent retained from an earlier session. The counter exposes status and a manual **Retry** action for recoverable failures, including a fresh consent refresh when needed. There is no app account or Google login flow.

When UMP marks privacy options as required, the app exposes **Ad privacy** to reopen the choices. The banner is cleared while choices are changed, and requests are gated again afterward. Configure the appropriate privacy messages under AdMob's **Privacy & messaging** for the production app and review the audience, regions and disclosures that apply to your distribution.

The SDK's manifest components and permissions remain intact, including its process-wide initialization provider. The SDK adds network and advertising-related permissions; see the app's [privacy policy](PRIVACY.md) and the merged production manifest. Explicit SDK initialization and ad loading are controlled by the counter's consent gate. Google's documented SDK data practices apply to the app.

## Testing and lifecycle

`BannerAds.factory` lets tests inject a fake controller. Robolectric defaults to a no-op controller so JVM/screenshot tests do not initialize Mobile Ads or make ad/consent network requests. Test fixture banners in screenshots represent layout, not proof that a real ad was served.

The live controller calculates adaptive size from the actual footer width, replaces the banner after width/orientation changes and pauses/resumes/destroys the AdView with its Activity. Theme changes reparent the existing loaded or pending banner rather than requesting another ad at the same width. New consent forms, initialization and ad loads wait while the counter is paused. Initialization happens in a background thread; view manipulation happens on the main thread.

Following Google's sample, the controller attaches the AdView before requesting an ad. It remains invisible while loading, then becomes visible on success. Failure status covers consent refresh, network/ad-load errors, no fill and initialization/device failures. The counter remains usable. Manual **Retry** waits 60 seconds after a failure and shows the remaining delay; it does not reload an already loaded ad. Retry does not approve an AdMob app, fix an invalid unit or force ad inventory to be available. There is no automatic background or resume retry loop after a failure. Actual serving remains unverified.

The CI live-APK smoke disables and verifies WiFi and mobile data before installing or opening the live build. It uses native coordinate taps to exercise counting, snapshots and header controls without owner ad impressions. XML, screenshots and a JSON result are uploaded with the emulator reports. These offline UI checks do not test live ad fill or account approval.

Official references:

- [Published full Ads 25.0.0 POM](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-ads/25.0.0/play-services-ads-25.0.0.pom).
- [Google's banner and consent sample](https://github.com/googleads/googleads-mobile-android-examples/tree/eac5db1a64ff2858676c98889c275cef926e3ab6/kotlin/admob/BannerExample).
- [AdMob Android privacy guide](https://developers.google.com/admob/android/privacy).
- [Anchored adaptive banners](https://developers.google.com/admob/android/banner/anchored-adaptive).

SDK compatibility and APIs were verified from Google's published artifacts and official sample source. The developer-site and support pages were unavailable through this cloud environment's network proxy. Amazon/store-link and app-ads.txt guidance is in [AMAZON_APPSTORE.md](AMAZON_APPSTORE.md); confirm current dashboard requirements before publication.
