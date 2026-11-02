rootProject.name = "FlightTp"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // viewbindingpropertydelegate, which the Travelpayouts SDK calls at runtime, is published
        // here and nowhere else.
        maven("https://www.jitpack.io")
        // Appodeal, which the SDK's results screen loads whether or not ads are configured.
        maven("https://artifactory.appodeal.com/appodeal")
    }
}

include(":androidApp")
include(":shared")

// The Travelpayouts flight SDK, as a local .aar — Android only, so no shared-module dependency.
include(":travel-sdk")