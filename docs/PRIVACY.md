# Privacy policy — Tasbih Counter - Zikr

Tasbih Counter - Zikr does not require an account. Counting, saved counts and the separate Quran reader work offline. The main counter includes an AdMob banner that uses Google's advertising services and requires internet access. The Quran reader displays no advertisements and does not request ads.

## Data stored on your device

The app stores your current count, saved count snapshots, counter name, optional target, selected color theme, dark/night mode, display-light choice and other counter settings locally on your Android device. This counter data is not sent to an app server or passed to the advertising SDK. Your current count saves automatically so you can continue after closing and reopening the app. The Save count button creates a separate snapshot; it is not required to keep the current count.

A fresh install has no target until you choose one, and you can turn the target off again. Existing counts and target settings are preserved when upgrading. Resetting a count sets the current total to zero. Deleting a saved snapshot removes that item from the app. The app keeps the latest 100 saved snapshots. Uninstalling the app or clearing its data removes its local counter data.

The Quran readings are loaded from packaged Arabic text assets rather than downloaded while reading. Opening or selecting a reading does not pass the verse text or selected passage to the advertising controller. The reader's source credit and license notices are described in [Quran text sources](QURAN_TEXT_SOURCE.md).

## Permissions

The app uses Android's `VIBRATE` permission for optional vibration feedback. You can turn off haptics in the app. Internet and network access support advertisements and consent messages. The advertising SDK also declares permissions for advertising identifiers, Android AdServices and wake locks. Google Mobile Ads uses advertising features according to consent, device settings and its SDK configuration. The app does not request location, contacts, microphone, camera or shared-storage access.

## Advertising and analytics

The main counter uses Google Mobile Ads to display an AdMob banner. The SDK may collect or share an IP address and approximate location inferred from it, device or advertising identifiers, ad interactions and diagnostic information. Google uses this information for purposes such as advertising, measurement and fraud prevention, according to the relevant consent choices and device settings. This is separate from the counter names, totals and saved counts stored by the app. The Quran screen has no advertising controller and starts no ad requests. The SDK's shared manifest components remain in the app, so its documented data practices still apply.

See Google's [Mobile Ads SDK data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure) and [privacy policy](https://policies.google.com/privacy) for the SDK's data practices.

The app uses Google's User Messaging Platform to refresh consent status and show consent messages where required before requesting advertisements. Where a privacy-options entry is required, the app provides a way to revisit those choices. Consent tools may store and process consent choices separately from the app's local counter data. The availability of personalized advertisements depends on consent and device settings. There are no in-app purchases or subscriptions.

## Distribution and testing

Downloading APKs and running tests through GitHub uses GitHub's services and is covered by GitHub's own privacy practices. Installing and using the app does not require GitHub access.

The main public APK uses the owner's production AdMob identifiers. The separate preview and debug builds use Google's test identifiers. Both use the advertising and consent SDKs described above; using a test ad unit does not mean those SDKs have no data processing.

This policy describes the source code in this repository. If future versions add data collection or new permissions, the policy must be updated to explain that behavior.
