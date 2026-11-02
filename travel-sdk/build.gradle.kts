// The Travelpayouts SDK ships as a bare .aar with no POM, so it is wired in the way their docs
// prescribe: a module whose only artifact is the file itself.
//
// Because there is no POM, nothing the SDK needs comes along with it — every library it calls at
// runtime is declared by hand in androidApp/build.gradle.kts, where the list is explained.
configurations.maybeCreate("default")
artifacts.add("default", file("travel-sdk-release.aar"))
