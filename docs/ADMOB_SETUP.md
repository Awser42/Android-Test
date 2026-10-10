# AdMob banner setup

Version 1.3.0 adds one anchored adaptive banner in a dedicated footer below the counter controls. The banner does not cover the counting button. Counting, names, targets and saved counts continue to work without an internet connection. The footer briefly reserves the banner's adaptive height while an ad loads; failed loads collapse it back to zero height.

## IDs and build variants

The owner supplied both production IDs separately:

- AdMob **app ID**: `ca-app-pub-5696841849162209~6162796310`.
- **Banner ad unit ID**: `ca-app-pub-5696841849162209/7022917306`.

App IDs use `~`; ad unit IDs use `/`. An app ID cannot be derived from an ad unit ID. These identifiers are public configuration values, not passwords or signing credentials.

| Build | App ID | Banner unit |
| --- | --- | --- |
| `debug` | Google's sample app ID | Google's test anchored adaptive banner |
| `preview` | Google's sample app ID | Google's test anchored adaptive banner |
| `release` | Owner's app ID | Owner's banner unit |

Debug and preview always use `ca-app-pub-3940256099942544~3347511713` and `ca-app-pub-3940256099942544/9214589741`. They never request the owner's live ad unit. Use the optimized, test-signed preview APK to interact with and review ads. Do not click live ads to test revenue or serving.

Release defaults to the supplied production IDs. Optional Gradle properties or environment variables named `ADMOB_APP_ID` and `ADMOB_BANNER_ID` can override them. Overrides must have the correct separators and match the owner's publisher account; malformed or mismatched values cause a clear Gradle configuration error.

```sh
# Installable optimized test APK: always Google test ads
./gradlew assemblePreview

# Unsigned production build: uses the owner's real IDs
./gradlew assembleRelease bundleRelease
```

Release APKs and app bundles still need the owner's production signing process before publication. Providing an AdMob ID does not publish the app, make an ad unit eligible or guarantee ads/revenue. Check the app's readiness and banner status in AdMob before production distribution.

## SDK choice and device support

The project pins Google's official `play-services-ads-lite:25.0.0` and `user-messaging-platform:4.0.0`. Their published artifacts support the existing Android 6.0/API 23 minimum. Lite 25.0.0 delegates to Ads API 24.8.0; explicit UMP 4.0.0 overrides its older transitive UMP version. The current full Ads 25.5.0 artifact requires API 24 and contains more SDK code, so it is not used here.

Lite loads ad implementation from Google Play services. A device without suitable Google Play services can count normally but may not display banners. A successful UI test on an AOSP emulator does not prove ad serving. Final APK size must be measured after shrinking; adding ads and consent libraries increases it.

## Consent and privacy options

Every Activity launch refreshes consent information through Google's User Messaging Platform. If a consent form is required, UMP presents it. Ads initialize and load only when `canRequestAds()` permits requests; a failed consent refresh can reuse valid consent retained from an earlier session. There is no app account or Google login flow.

When UMP marks privacy options as required, the app exposes **Ad privacy** to reopen the choices. The banner is cleared while choices are changed, and requests are gated again afterward. Configure the appropriate privacy messages under AdMob's **Privacy & messaging** for the production app and review the audience, regions and disclosures that apply to your distribution.

The SDK's manifest components and permissions remain intact. The SDK adds network and advertising-related permissions; see the app's [privacy policy](PRIVACY.md) and the merged production manifest. The SDK's initialization provider remains supported; explicit SDK initialization and ad loading are controlled by the consent gate. No unsupported removal of SDK components or claims about preventing all SDK data collection are made.

## Testing and lifecycle

`BannerAdController` exposes `attach`, `start`, `resume`, `pause`, `destroy`, `showPrivacyOptions` and `privacyOptionsRequired`. `BannerAds.factory` lets tests inject a fake controller. Robolectric defaults to a no-op controller so JVM/screenshot tests do not initialize Mobile Ads or make ad/consent network requests. Test fixture banners in screenshots represent layout, not proof that a real ad was served.

The live controller calculates adaptive size from the actual footer width, replaces the banner after width/orientation changes and pauses/resumes/destroys the AdView with its Activity. Theme changes reparent the existing loaded or pending banner rather than requesting another ad at the same width. Initialization happens in a background thread; view manipulation happens on the main thread.

Following Google's sample, the controller attaches the AdView before requesting an ad. It remains invisible while loading, then becomes visible on success. Ad failures collapse the footer and do not stop the counter. A failed load may retry when you next return to the app after a 60-second cooldown; there is no timer or repeated background retry loop.

Official references:

- [Lite metadata](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-ads-lite/maven-metadata.xml) and [published Lite 25.0.0 POM](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-ads-lite/25.0.0/play-services-ads-lite-25.0.0.pom).
- [Google's banner and consent sample](https://github.com/googleads/googleads-mobile-android-examples/tree/eac5db1a64ff2858676c98889c275cef926e3ab6/kotlin/admob/BannerExample).
- [AdMob Android privacy guide](https://developers.google.com/admob/android/privacy).
- [Anchored adaptive banners](https://developers.google.com/admob/android/banner/anchored-adaptive).

SDK compatibility and APIs were verified from Google's published artifacts and official sample source. The developer-site pages were unavailable through this cloud environment's network proxy when this integration was prepared.
