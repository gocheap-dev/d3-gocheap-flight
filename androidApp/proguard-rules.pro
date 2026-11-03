# R8 rules for the release build, which runs with `isMinifyEnabled` and `isShrinkResources` on.
#
# Most libraries here ship their own consumer rules inside their artifact, and those are applied
# automatically — nothing below needs to repeat them. What this file carries is the three things
# R8 cannot work out on its own:
#
#   1. The Travelpayouts SDK. It is a bare .aar with no POM and — checked, not assumed — no
#      `proguard.txt` inside it, so it contributes no consumer rules at all. Every rule it needs is
#      written here by hand.
#   2. Reflection this app's own code reaches through: kotlinx.serialization's generated
#      serializers, and the models they are generated for.
#   3. Crash-report readability, which is the whole point of uploading a mapping file.
#
# A rule that duplicates a library's own consumer rule is kept anyway where leaving it out would
# depend on that library never changing its mind — the cost is a few kilobytes, and the failure
# mode on the other side is a crash in a release build that no debug build reproduces.


# ---------------------------------------------------------------------------------------------
# Attributes
# ---------------------------------------------------------------------------------------------

# Generic signatures. Gson reads the element type of a `List<Foo>` field off this attribute, and
# Retrofit reads the return type of a `Call<Foo>` the same way — both of which the SDK does. Strip
# it and Gson deserialises into `LinkedTreeMap` instead of the model, which fails later and
# somewhere else.
-keepattributes Signature

# `-keepattributes Exceptions` is what lets Retrofit see a method's checked exceptions, and
# InnerClasses/EnclosingMethod is what keeps a nested model's name resolvable rather than reported
# as a synthetic.
-keepattributes Exceptions, InnerClasses, EnclosingMethod

# Annotations are the input to almost every rule below — @SerializedName, @Serializable,
# retrofit2.http.*, javax.inject.*. R8 drops annotations it is not told to keep, and a dropped
# annotation makes the rule that matches on it match nothing, silently.
-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations, AnnotationDefault

# Crashlytics reads a release stack trace back through the mapping file this build uploads. Without
# these two a report arrives with no file and no line, which the mapping cannot put back.
-keepattributes SourceFile, LineNumberTable
# The file name itself is not worth leaking once the line numbers are recoverable from the mapping.
-renamesourcefileattribute SourceFile


# ---------------------------------------------------------------------------------------------
# The Travelpayouts SDK
# ---------------------------------------------------------------------------------------------
#
# The vendor already obfuscated this .aar before shipping it — its internals live in one-character
# root packages (`a.*`, `D.*`, …) and only the public surface kept its names. There is no source,
# no mapping file and no consumer rules, so there is no way to tell which of its 3,846 classes are
# reached reflectively and which are not. Two of the things it does are reflective for certain:
# Gson over its response models (46 classes carry `@SerializedName`) and Dagger graph construction.
#
# So it is kept whole. Re-shrinking a blob that is already minified buys little and risks a
# ClassNotFoundException deep inside a screen this app opens but does not own; the SDK's classes are
# ~5.6 MB either way. The shrinking that matters happens in its transitive dependencies — Appodeal,
# AppsFlyer, Play Services, Material — which are ordinary artifacts with their own rules and are
# left to be shrunk normally.

-keep class com.travelapp.sdk.** { *; }
-keep interface com.travelapp.sdk.** { *; }
# Its pre-obfuscated internals. `?` matches a single character and not the package separator, so
# this is every class whose root package is one character long — which in this app's dependency set
# is exactly the SDK's own internals, and stays correct if a future .aar renames them.
-keep class ?.** { *; }
-dontwarn com.travelapp.sdk.**

# Kotlin `internal` constructors the SDK marks for its own screens, which this app calls from Java
# instead (see `TravelSdkModels`). Already covered by the keeps above; stated so that the
# integration's one unusual move is visible here too.
-keep class com.travelapp.sdk.internal.domain.** { *; }


# ---------------------------------------------------------------------------------------------
# Kotlin runtime
# ---------------------------------------------------------------------------------------------

# `Intrinsics.checkNotNullParameter` and friends throw with the parameter name in the message. Not
# strictly needed, but a null-parameter crash that names the parameter is worth the few bytes.
-keepclassmembers class kotlin.jvm.internal.Intrinsics { *; }

# Read by kotlin-reflect and by kotlinx.serialization's error paths. Keeping it on this app's own
# classes only, rather than globally, so the libraries that do not need it still shed theirs.
-keep class kotlin.Metadata { *; }

-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

# Coroutines. The library ships rules for the internal atomics; these two cover the service-loader
# entries R8 cannot see through.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**


# ---------------------------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------------------------
#
# Every model in the shared module goes through this — the four HTTP clients, the persisted
# settings, session and favourites records, and the picker results passed between screens.
#
# The plugin generates a `$$serializer` object per `@Serializable` class and a `serializer()` on its
# companion. Both are reached statically here (nothing in this app resolves a serializer by KClass
# at runtime, and there are no polymorphic or contextual serializers), but R8's full mode still
# needs telling that the generated members are live — it cannot see the companion's `serializer()`
# being called through the synthetic accessor the plugin emits.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# The generated serializer object on each @Serializable class.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer {
    *** serializer(...);
    *** descriptor;
    <fields>;
}

# The models themselves, plus the synthetic constructor the plugin emits for classes with default
# values — deserialising a record that omits an optional field goes through it, and R8 sees nothing
# calling it.
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    <init>(...);
    <fields>;
}
-keepclasseswithmembers @kotlinx.serialization.Serializable class * {
    <init>(int, ..., kotlinx.serialization.internal.SerializationConstructorMarker);
}

# This app's own models, named directly. The rules above match on the annotation, which is the
# general case; this is the belt to that braces, because the whole of the app's data layer is in
# here and a model silently losing a field is a blank screen rather than a crash.
-keep @kotlinx.serialization.Serializable class com.gocheap.flights.** { *; }
-keepclassmembers class com.gocheap.flights.** {
    *** Companion;
}

-dontwarn kotlinx.serialization.**


# ---------------------------------------------------------------------------------------------
# Ktor — this app's HTTP stack (flights, hotels, cars, users, ads)
# ---------------------------------------------------------------------------------------------
#
# Ktor resolves its engine off the classpath through a service loader, which R8 cannot trace. On
# Android that is OkHttp; losing it leaves every request failing with "no engine found" at runtime.

-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**

# Optional integrations Ktor references and this build does not use.
-dontwarn org.slf4j.**
-dontwarn io.netty.**
-dontwarn org.apache.**


# ---------------------------------------------------------------------------------------------
# OkHttp — under both Ktor (this app) and Retrofit (the SDK)
# ---------------------------------------------------------------------------------------------

-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**
# Optional TLS providers OkHttp reaches for by name and this app does not ship.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ---------------------------------------------------------------------------------------------
# Retrofit + Gson — the SDK's HTTP stack
# ---------------------------------------------------------------------------------------------
#
# The SDK's service interfaces carry @GET/@POST/@Query/@Header/@Body and are built by a dynamic
# proxy, so the annotations and the generic return types have to survive. They are inside the full
# SDK keep above; the rules here are what Retrofit and Gson need in general.

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.** <methods>; }
-keep,allowobfuscation interface <1>
-keepclasseswithmembers interface * {
    @retrofit2.http.** <methods>;
}
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# Gson instantiates models without calling a constructor and matches fields by @SerializedName, so
# an annotated field must not be removed. `allowobfuscation` is deliberate: the annotation carries
# the wire name, so the field's own name is free to change.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
# TypeToken subclasses exist only to carry a generic type through to runtime; erasing them erases
# the thing they are for.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.** { *; }
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory { <init>(); }
-keepclassmembers class * implements com.google.gson.JsonSerializer { <init>(); }
-keepclassmembers class * implements com.google.gson.JsonDeserializer { <init>(); }
-dontwarn com.google.gson.**
-dontwarn sun.misc.Unsafe


# ---------------------------------------------------------------------------------------------
# Dagger — how the SDK builds its object graph
# ---------------------------------------------------------------------------------------------

-keep class dagger.** { *; }
-keep interface dagger.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}
-keep @dagger.Module class * { *; }
-keep @dagger.Component class * { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **Dagger*Component* { *; }
-dontwarn dagger.**
-dontwarn javax.inject.**


# ---------------------------------------------------------------------------------------------
# Android framework reflection the SDK's View-based screens depend on
# ---------------------------------------------------------------------------------------------

# @Parcelize generates a CREATOR the framework looks up by name; 138 of the SDK's classes use it,
# and a lost CREATOR is a BadParcelableException the moment a screen is rotated or restored.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class * implements android.os.Parcelable { *; }
-keepnames class * implements android.os.Parcelable

# Fragments are instantiated by class name — from the SDK's navigation graph, and again by the
# framework when it restores a saved state.
-keep class * extends androidx.fragment.app.Fragment { <init>(); }
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# ViewBinding's generated classes are reached through `inflate`/`bind` statics. The SDK uses 337 of
# them via viewbindingpropertydelegate; the -noreflection variant does not reflect, but the
# generated classes themselves still have to exist.
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}

# Views inflated from XML by name. AAPT generates keeps for views named in this app's and its
# libraries' layouts, but the SDK's layouts live inside an .aar whose R is already compiled, so
# these cover the two constructors the inflater calls.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
# Data-binding/animator property access, which goes through setters by name.
-keepclassmembers class * extends android.view.View {
    void set*(***);
    *** get*();
}

# CoordinatorLayout resolves a Behavior from the `app:layout_behavior` string in a layout.
-keep class * extends androidx.coordinatorlayout.widget.CoordinatorLayout$Behavior {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# AndroidX / Material / AppCompat.
-keep class androidx.appcompat.widget.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# Lifecycle + ViewModel. A ViewModel is constructed by a factory that finds its constructor by
# signature; SavedStateHandle restoration does the same.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * implements androidx.lifecycle.DefaultLifecycleObserver { *; }

# Navigation — the SDK's screens are driven by the View-based one, this app's by the Compose one.
-keep class androidx.navigation.** { *; }
-keepnames class * extends androidx.navigation.Navigator
-keep class * extends androidx.navigation.Navigator { *; }

# androidx.startup initializers, named in the merged manifest and instantiated by name. Firebase,
# WorkManager and several of the SDK's dependencies install themselves this way, before any of this
# app's code runs.
-keep class * implements androidx.startup.Initializer { *; }


# ---------------------------------------------------------------------------------------------
# Compose — this app's entire UI
# ---------------------------------------------------------------------------------------------
#
# The Compose runtime ships its own rules and they are sufficient; these cover the parts of
# Compose Multiplatform's resource loading that go by name rather than by reference.

-keep class androidx.compose.runtime.** { *; }
-keep class org.jetbrains.compose.resources.** { *; }
-keepclassmembers class **.ComposableSingletons* { *; }
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**


# ---------------------------------------------------------------------------------------------
# Coil — both versions
# ---------------------------------------------------------------------------------------------
#
# Coil 3 over Ktor for this app's images, and Coil 2 for the SDK's. Both resolve decoders and
# fetchers through component registries; the SVG decoder in particular is reached by type.

-keep class coil3.** { *; }
-keep class coil.** { *; }
-dontwarn coil3.**
-dontwarn coil.**


# ---------------------------------------------------------------------------------------------
# Firebase — Analytics, Crashlytics, Messaging, Installations
# ---------------------------------------------------------------------------------------------
#
# Firebase components are discovered through `ComponentRegistrar` classes named in each artifact's
# manifest and instantiated by name, which is invisible to R8.

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.firebase.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keepnames class com.google.firebase.components.ComponentRegistrar

# Crashlytics' own classes, and the uncaught-exception handler it installs from its startup
# provider before any of this app's code runs.
-keep class com.google.firebase.crashlytics.** { *; }
-keepclassmembers class com.google.firebase.crashlytics.** { *; }

# The payload of a push arrives as a Map and is read by key, but the service that receives it is
# declared in the manifest and so already kept; this keeps the messaging models it hands over.
-keep class com.google.firebase.messaging.** { *; }

-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# The three identifiers `users/add` falls back through. Each is optional at runtime — a device
# without Play Services registers under the next one down — which means R8 sees calls guarded by
# try/catch and the classes have to survive for the fallback to be reachable at all.
-keep class com.google.android.gms.ads.identifier.** { *; }
-keep class com.google.android.gms.appset.** { *; }
-dontwarn com.google.android.gms.ads.identifier.**
-dontwarn com.google.android.gms.appset.**

# Where the install came from, sent as `acquired_route`. An AIDL-backed service connection.
-keep class com.android.installreferrer.** { *; }
-dontwarn com.android.installreferrer.**

# Play Core review flow, which the SDK prompts with.
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# Maps, declared because the SDK's manifest needs the meta-data to resolve.
-dontwarn com.google.android.gms.maps.**


# ---------------------------------------------------------------------------------------------
# The SDK's remaining runtime dependencies
# ---------------------------------------------------------------------------------------------

# AppsFlyer — constructed by the SDK's analytics as it initialises, and heavily reflective
# internally (it reads the install referrer, the advertising id and several optional integrations
# by name).
-keep class com.appsflyer.** { *; }
-keep interface com.appsflyer.** { *; }
-dontwarn com.appsflyer.**

# Appodeal — reached on the way up by the SDK's results screen even with advertising unconfigured.
# It loads ad-network adapters by class name, and every network this build does not ship is a
# missing class it expects to catch.
-keep class com.appodeal.ads.** { *; }
-keep interface com.appodeal.ads.** { *; }
-keep class com.explorestack.** { *; }
-dontwarn com.appodeal.ads.**
-dontwarn com.explorestack.**

# Lottie parses animations into models by field name.
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

-keep class ru.cleverpumpkin.calendar.** { *; }
-dontwarn ru.cleverpumpkin.calendar.**

-keep class com.faltenreich.skeletonlayout.** { *; }
-dontwarn com.faltenreich.skeletonlayout.**

-keep class dev.chrisbanes.insetter.** { *; }
-dontwarn dev.chrisbanes.insetter.**

-keep class by.kirich1409.viewbindingdelegate.** { *; }
-dontwarn by.kirich1409.viewbindingdelegate.**

-keep class com.hannesdorfmann.adapterdelegates4.** { *; }
-dontwarn com.hannesdorfmann.adapterdelegates4.**

-keep class reactivecircus.flowbinding.** { *; }
-dontwarn reactivecircus.flowbinding.**

# Timber plants a tree whose tag is derived from the stack trace, which needs the class names it
# reads to still be there. Its own rules cover the library; this keeps the tag readable.
-keep class timber.log.** { *; }
-dontwarn timber.log.**


# ---------------------------------------------------------------------------------------------
# This app
# ---------------------------------------------------------------------------------------------

# Read from `FlightTpApplication` and from the SDK config it builds.
-keep class com.gocheap.flights.BuildConfig { *; }

# The bridge the shared module hands a flight search to, and the Java helper that builds the SDK's
# models. Both are reached across a module boundary through an interface the SDK side implements.
-keep class com.gocheap.flights.sdk.** { *; }

# Enum constants are matched by name — `valueOf` on the way in, `name` on the way out — by both
# kotlinx.serialization and the SDK's Gson models.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# The generated R classes the resource shrinker and the SDK's own code read through.
-keepclassmembers class **.R$* {
    public static <fields>;
}

# JNI.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Serializable, for anything that still uses it.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Desugaring. minSdk is 26 so java.time needs no desugaring at runtime, but the SDK and
# crunchycalendar are built against it expecting to be desugared and their metadata requires it.
-dontwarn java.lang.invoke.**
-dontwarn **$$ExternalSyntheticLambda*
-dontwarn build.IgnoreJava8API
