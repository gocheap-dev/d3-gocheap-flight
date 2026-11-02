This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: run `pod install` in [/iosApp](./iosApp), then open `iosApp.xcworkspace` — **not**
  `iosApp.xcodeproj`. The Travelpayouts SDK is a CocoaPods dependency, so the bare project links
  against frameworks only the workspace builds and fails with `ld: framework 'Alamofire' not
  found`. The same applies to the IDE's iOS run configuration: it must point at the workspace.

  Credentials come from `local.properties` by way of `Secrets.xcconfig` — see below.

### Credentials

Every secret and every per-environment value lives in `local.properties` at the repository root,
which is git-ignored. Nothing credential-shaped is committed, on either platform.

Android reads the file directly: Gradle turns it into `BuildConfig` fields in `androidApp`, and
into the generated `BuildValues` object in `shared` that both platforms compile against.

iOS cannot read a `.properties` file, so the same values are written out as an xcconfig instead:

```
./gradlew :shared:generateIosSecrets
```

That writes `iosApp/Configuration/Secrets.xcconfig` (git-ignored) from `local.properties`.
`Configuration/Config.xcconfig` includes it last, so it overrides the blanks committed there, and
from the xcconfig the values reach Swift through `Info.plist` — see `FirebaseBootstrap`.

Run it once as part of setup, alongside `pod install`. After that an Xcode build regenerates it on
its own: the project's "Compile Kotlin Framework" phase runs `embedAndSignAppleFrameworkForXcode`,
which the task hangs off. One caveat — Xcode resolves an xcconfig when a build *starts*, before
that phase runs, so a value changed in `local.properties` reaches the app on the *next* build. The
task prints a `warning:` when it rewrites the file to say so.

#### The keys

| Key | Used by | Notes |
| --- | --- | --- |
| `TRAVELPAYOUTS_MARKER`, `TRAVELPAYOUTS_API_KEY` | both | One affiliate account, so both platforms share them. Request them from support@travelpayouts.com. Leave them blank and the SDK is not initialised — on iOS the flight search button then does nothing, since there is no in-app results screen to fall back to. |
| `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID` | Android | |
| `IOS_FIREBASE_APP_ID`, `IOS_FIREBASE_API_KEY`, `IOS_FIREBASE_GCM_SENDER_ID`, `IOS_FIREBASE_PROJECT_ID` | iOS | **Not interchangeable with the Android four.** One Firebase project holds one app per platform: the app id and the API key differ between them, and pointing iOS at the Android values would file its events under the Android app with nothing on screen to say so. Only the project and sender ids are genuinely shared, and those fall back to the Android keys when unset. |
| `HOTEL_BASE_URL`, `CAR_BASE_URL`, `FLIGHT_BASE_URL`, `USER_BASE_URL` | both | Defaulted to staging; generated into `shared`, so the two platforms cannot drift apart over them. |
| `PRIVACY_POLICY_URL`, `SUPPORT_EMAIL` | both | Where the Profile tab leaves for. |
| `APPLICATION_ID` | Android | Must match a client in `google-services.json`; the plugin fails the build otherwise. |
| `IOS_TEAM_ID` | iOS | Optional. Suffixed onto `PRODUCT_BUNDLE_IDENTIFIER` for side-by-side installs. |
| `TRAVELPAYOUTS_CLIENT_DEVICE_HOST`, `TRAVELPAYOUTS_APPSTORE_ID` | iOS | Optional; the SDK falls back to its own defaults. |

#### The Firebase config files

`androidApp/google-services.json` and `iosApp/iosApp/GoogleService-Info.plist` are both git-ignored
— download each from the Firebase console. Either one takes precedence over the `FIREBASE_*`
values above when present; the keys are the way to run without it.

They are not equivalent in how they fail, though. On iOS the file is picked up by the target's
synchronized folder, so a checkout without it simply builds without it and falls back to the keys.
On Android the `googleServices` plugin requires it and fails the build when it is missing, so a
fresh clone needs the download before `assembleDebug` will run at all.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…