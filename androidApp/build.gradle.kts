import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Turns google-services.json into the resources Firebase reads at startup. The file is inert
    // without it — AGP does not look at it on its own.
    alias(libs.plugins.googleServices)
    // Stamps each build with the id Crashlytics files its reports under, and uploads the mapping
    // file a minified release has to be read through. Must come after googleServices, which is
    // what tells it which Firebase app the symbols belong to.
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    // For AppCompatDelegate.setDefaultNightMode, which is what pins the SDK's DayNight screens to
    // light. It also arrives under the SDK's own dependencies below; declared here because this
    // module's code calls it directly.
    implementation(libs.androidx.appcompat)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Push. The shared module depends on this too, for the token and topics, but `implementation`
    // does not carry across a module boundary and the messaging service below is declared here.
    implementation(libs.firebase.messaging)

    // The Travelpayouts flight SDK, plus everything it calls at runtime. A bare .aar carries no
    // POM, so none of this arrives on its own: leaving a line out compiles fine and then dies with
    // NoClassDefFoundError the first time the SDK opens a screen that needs it.
    implementation(project(":travel-sdk"))
    implementation(libs.tp.dagger)
    // Constructed by the SDK's analytics as it initialises, whether or not a dev key is configured.
    implementation(libs.tp.appsflyer)
    // Its results screen reaches for Appodeal on the way up, even with advertising unconfigured.
    implementation(libs.tp.appodeal.core)
    implementation(libs.tp.material)
    implementation(libs.tp.viewbindingdelegate)
    implementation(libs.tp.insetter)
    implementation(libs.tp.lottie)
    implementation(libs.tp.skeletonlayout)
    implementation(libs.tp.crunchycalendar)
    implementation(libs.tp.timber)
    implementation(libs.tp.gson)
    implementation(libs.tp.coil)
    implementation(libs.tp.coil.base)
    implementation(libs.tp.coil.svg)
    implementation(libs.tp.adapterdelegates4)
    implementation(libs.tp.adapterdelegates4.dsl)
    implementation(libs.tp.adapterdelegates4.dsl.layoutcontainer)
    implementation(libs.tp.adapterdelegates4.dsl.viewbinding)
    implementation(libs.tp.flowbinding.core)
    implementation(libs.tp.flowbinding.android)
    implementation(libs.tp.flowbinding.material)
    implementation(libs.tp.flowbinding.recyclerview)
    // The SDK's screens are Fragments driven by View-based Navigation; its layouts reference that
    // library's attributes, so resource linking fails without it in a Compose-only app.
    implementation(libs.tp.navigation.fragment)
    implementation(libs.tp.navigation.ui)
    implementation(platform(libs.tp.okhttp.bom))
    implementation(libs.tp.okhttp)
    implementation(libs.tp.okhttp.logging)
    implementation(libs.tp.retrofit)
    implementation(libs.tp.retrofit.converter.gson)
    implementation(libs.tp.retrofit.converter.scalars)
    implementation(libs.tp.play.services.maps)
    implementation(libs.tp.play.services.location)
    implementation(libs.tp.review)
    implementation(libs.tp.review.ktx)
    implementation(platform(libs.tp.firebase.bom))
    implementation(libs.tp.firebase.analytics)
    implementation(libs.tp.firebase.crashlytics)
    // The SDK and its calendar library are built against java.time APIs they expect to be
    // desugared, and their AAR metadata refuses to link without it.
    coreLibraryDesugaring(libs.tp.desugar.jdk.libs)
}

/**
 * Values read out of local.properties — which is git-ignored — rather than committed: the
 * Travelpayouts marker is what the affiliate revenue is paid against, the API key is a secret,
 * and the application id is per-build rather than per-checkout.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localProperty(name: String, default: String = ""): String =
    (localProperties.getProperty(name) ?: System.getenv(name))?.takeIf(String::isNotBlank)
        ?: default

android {
    namespace = "com.gocheap.travel"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Overridable so one checkout can produce side-by-side installs, but defaulted rather
        // than required: local.properties is git-ignored, so a fresh clone has no value to read.
        //
        // Whatever this resolves to has to be a package `google-services.json` lists a client for
        // — the plugin matches on it and fails the build when nothing matches, so an override
        // here needs a matching entry there.
        applicationId = localProperty("APPLICATION_ID", default = "com.example.flighttp")
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // Read at build time rather than hardcoded; an absent value leaves the SDK uninitialised
        // and logged about, instead of failing the build on a machine without the credentials.
        buildConfigField(
            "String",
            "TRAVELPAYOUTS_MARKER",
            "\"${localProperty("TRAVELPAYOUTS_MARKER")}\"",
        )
        buildConfigField(
            "String",
            "TRAVELPAYOUTS_API_KEY",
            "\"${localProperty("TRAVELPAYOUTS_API_KEY")}\"",
        )
        // A Firebase app has to exist for two reasons: this app's own GA4 reporting, and the
        // Travelpayouts SDK, which builds a Firebase Analytics client as it initialises.
        //
        // `google-services.json` in this module is how it is normally created, and it is present
        // and takes precedence over all three of these — the plugin above turns it into resources
        // that Firebase's startup provider reads before any of this app's code runs. These are the
        // way to run without that file, and are kept in step with it deliberately: if they named a
        // different Firebase project, deleting the JSON would silently redirect every event into
        // that other project with nothing on screen to say so.
        buildConfigField(
            "String",
            "FIREBASE_APP_ID",
            "\"${localProperty("FIREBASE_APP_ID")}\"",
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            "\"${localProperty("FIREBASE_API_KEY")}\"",
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            "\"${localProperty("FIREBASE_PROJECT_ID")}\"",
        )
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        // Carries the Travelpayouts marker and API key through from local.properties.
        buildConfig = true
    }
}