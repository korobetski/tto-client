// No `org.jetbrains.kotlin.android` here: since AGP 9.0 the Android plugin brings Kotlin
// itself, and applying the standalone plugin on top of it now fails the build outright
// ("no longer required for Kotlin support since AGP 9.0" — issuetracker 438678642). It was
// still listed until the `android.builtInKotlin=false` shim came out of `gradle.properties`,
// which had been suppressing exactly this. `:shared` is unaffected: there the Kotlin
// Multiplatform plugin owns the Kotlin setup, not AGP.
import java.util.zip.ZipFile
import javax.inject.Inject

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

/**
 * The Compose resources, laid out as assets by `:shared:androidComposeAssets`.
 *
 * Declared as a dependency rather than read out of the other project's build directory, so the
 * artifact carries its producing task and `mergeDebugAssets` cannot run before it has written.
 * `:shared` explains why this is done by hand at all: the Compose plugin's own wiring is inert
 * under AGP 9's KMP library plugin, and the APK shipped with an empty `assets/`.
 */
val composeAssetsDependencies = configurations.dependencyScope("composeAssets")
val sharedComposeAssets = configurations.resolvable("sharedComposeAssetsPath") {
    extendsFrom(composeAssetsDependencies.get())
}

dependencies {
    add(
        composeAssetsDependencies.name,
        project(mapOf("path" to ":shared", "configuration" to "androidComposeAssetsElements")),
    )
}

/** Restates the resolved assets as this project's own output, for [addGeneratedSourceDirectory]. */
abstract class ComposeAssets : DefaultTask() {
    @get:InputFiles
    abstract val source: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            from(source)
            into(outputDirectory)
        }
    }
}

// `addGeneratedSourceDirectory` and not `sourceSets.main.assets.srcDir(configuration)`: the latter
// compiles, resolves, and silently leaves the sync task out of the graph — a clean build packaged
// an APK with no resources again, which is the bug this whole arrangement exists to fix. The
// Variant API is the supported way to hand AGP a directory some task writes, and it takes the
// task provider rather than a path, so the ordering is not a matter of luck.
androidComponents.onVariants { variant ->
    val assets = tasks.register<ComposeAssets>(
        "composeAssetsFor${variant.name.replaceFirstChar(Char::uppercaseChar)}",
    ) {
        source.from(sharedComposeAssets)
    }
    variant.sources.assets?.addGeneratedSourceDirectory(assets, ComposeAssets::outputDirectory)
}

/**
 * `versionCode` and `versionName`, both derived from the one `clientVersion` in `gradle.properties`.
 *
 * They were `1` and `"0.1.0"`, written by hand and never moved. `versionCode` being the one that
 * had to move: Android refuses an APK whose code is not **greater** than the installed one, so a
 * value frozen at 1 does not produce a release that fails to install — it produces one that cannot
 * be installed at all, and the message the player gets says nothing about a version.
 *
 * The mapping reserves two digits each for minor and patch, which makes the code readable back —
 * `10203` is `1.2.3` — and orders exactly as the version does, which is the only property Android
 * actually requires. Both bounds are checked rather than trusted: `1.2.100` would otherwise encode
 * as `10300` and collide with `1.3.0`, silently, on a number nobody reads until an update refuses
 * to install.
 */
val clientVersion = providers.gradleProperty("clientVersion").get()
val versionParts = clientVersion.split(".").mapNotNull(String::toIntOrNull)
require(versionParts.size == 3 && versionParts.none { it < 0 }) {
    "clientVersion is '$clientVersion'; expected major.minor.patch, all non-negative"
}
val (versionMajor, versionMinor, versionPatch) = versionParts
require(versionMinor < 100 && versionPatch < 100) {
    "clientVersion is '$clientVersion'; minor and patch must each be below 100 — see the mapping " +
        "in androidApp/build.gradle.kts"
}

/**
 * The release signing material, read from properties or the environment and **never from a file in
 * this repository**.
 *
 * `~/.gradle/gradle.properties`   ttoKeystore / ttoKeystorePassword / ttoKeyAlias / ttoKeyPassword
 * the environment                 TTO_KEYSTORE / TTO_KEYSTORE_PASSWORD / TTO_KEY_ALIAS / TTO_KEY_PASSWORD
 *
 * The same two sources, in the same order, that `tto-server` reads its GitHub Packages credentials
 * from — developers configure a file once, CI passes secrets through the environment.
 *
 * ### Why a missing keystore is not an error
 *
 * `assembleDebug` is what a contributor builds, and it signs itself with the debug key; failing the
 * configuration of the whole module because a *release* credential is absent would make the project
 * unbuildable for everyone who never signs anything. So a build without this material still
 * produces an unsigned release APK, exactly as before — and `release.yml` refuses to publish one,
 * which is where the check belongs. An unsigned APK is only dangerous once it is offered to a
 * player as an update.
 */
val releaseSigning: Map<String, String>? = run {
    fun secret(property: String, environment: String): String? =
        providers.gradleProperty(property)
            .orElse(providers.environmentVariable(environment))
            .orNull
            ?.takeIf { it.isNotBlank() }

    val material = mapOf(
        "storeFile" to secret("ttoKeystore", "TTO_KEYSTORE"),
        "storePassword" to secret("ttoKeystorePassword", "TTO_KEYSTORE_PASSWORD"),
        "keyAlias" to secret("ttoKeyAlias", "TTO_KEY_ALIAS"),
        "keyPassword" to secret("ttoKeyPassword", "TTO_KEY_PASSWORD"),
    )
    // All four or none. A partial set is a typo in someone's `gradle.properties`, and silently
    // signing with three of them is not a thing that can happen — it fails deep inside apksigner
    // with a message about the keystore rather than about the value that was left out.
    if (material.values.any { it == null }) {
        require(material.values.all { it == null }) {
            "release signing needs all four of ttoKeystore, ttoKeystorePassword, ttoKeyAlias and " +
                "ttoKeyPassword (or their TTO_* environment equivalents); got only " +
                material.filterValues { it != null }.keys.sorted().joinToString()
        }
        null
    } else {
        material.mapValues { (_, value) -> value!! }
    }
}

android {
    namespace = "com.tripletriad.android"

    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tripletriad.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch
        versionName = clientVersion
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        releaseSigning?.let { material ->
            create("release") {
                storeFile = file(material.getValue("storeFile"))
                storePassword = material.getValue("storePassword")
                keyAlias = material.getValue("keyAlias")
                keyPassword = material.getValue("keyPassword")
                // Both schemes on purpose. v2 is what every supported device verifies; v1 is what
                // makes the APK installable on the API 24 floor this app still declares, where the
                // v2 block is ignored and an APK carrying only it looks unsigned.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8, which a store would require and which is worth having regardless: it strips the
            // unused half of every library this app links and shrinks the download a player waits
            // for. What it costs is a class of failure that **no unit test can see** — a type
            // removed because the only thing that reaches it is a reflective lookup — which is why
            // `proguard-rules.pro` states a reason beside every keep, and why the end-to-end run in
            // the plan's § 5 has to be repeated against a minified build rather than a debug one.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro"),
            )
            // Null when no material was supplied, which leaves the variant unsigned rather than
            // failing — see [releaseSigning].
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/** Mirrors `packageOfResClass` in `:shared`; the two have to agree or the runtime finds nothing. */
val resPackage = "tripletriad.shared.generated.resources"

/**
 * Fails if the debug APK ships without its Compose resources.
 *
 * The app crashed on launch with `MissingResourceException` because the APK carried a single
 * asset and no locale bundle, and nothing in the build had an opinion about that — the 520 desktop
 * tests read the same resources from a different, working pipeline, so they stayed green. This
 * reads the packaged APK, which is the only place the question is actually settled.
 */
val verifyComposeAssets = tasks.register("verifyComposeAssets") {
    group = "verification"
    description = "Checks the debug APK actually contains the Compose resources."

    val apk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
    dependsOn("assembleDebug")
    inputs.file(apk)

    doLast {
        val entries = ZipFile(apk.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        val locale = "assets/composeResources/$resPackage/files/locales/tto-en_US.json"
        check(locale in entries) {
            "The debug APK has no Compose resources — expected $locale. " +
                "See :shared:androidComposeAssets."
        }
    }
}

/**
 * The same check against the **shrunk** APK, plus the thing shrinking can break.
 *
 * ### Why the debug check is not enough
 *
 * `isShrinkResources` removes resources R8 believes nothing references, and it cannot see a file
 * addressed by path at runtime — which is exactly how `Res.readBytes` reaches every catalogue and
 * every locale bundle in this app. The debug variant is not shrunk, so the check next door passes
 * whatever the release build does.
 *
 * ### And why the dex is searched for serializers
 *
 * The other half of the same problem. `kotlinx.serialization` finds a generated `$serializer` by
 * name, so R8 sees a class nobody calls; remove it and the app builds, installs, launches, and
 * crashes the first time it decodes a payload. `proguard-rules.pro` keeps them — this is what says
 * the rules are still doing their job after a library upgrade rewrites the class shapes.
 *
 * A string search is coarse: it proves the names survived, not that every serializer did. It is
 * still the difference between noticing at build time and noticing in a player's hands, and the
 * only cheaper alternative — running the APK — needs a device this build does not have.
 */
/** Whether [slice] appears anywhere in this array. A dex is binary, so this is a byte search. */
fun ByteArray.containsSlice(slice: ByteArray): Boolean {
    if (slice.isEmpty() || slice.size > size) return false
    outer@ for (start in 0..size - slice.size) {
        for (offset in slice.indices) {
            if (this[start + offset] != slice[offset]) continue@outer
        }
        return true
    }
    return false
}

val verifyReleaseApk = tasks.register("verifyReleaseApk") {
    group = "verification"
    description = "Checks the minified APK kept its resources and its serializers."

    val apk = layout.buildDirectory.file("outputs/apk/release/androidApp-release-unsigned.apk")
    dependsOn("assembleRelease")
    inputs.file(apk)

    doLast {
        ZipFile(apk.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val locale = "assets/composeResources/$resPackage/files/locales/tto-en_US.json"
            check(locale in entries) {
                "The release APK has no Compose resources — expected $locale. " +
                    "`isShrinkResources` removed what `Res.readBytes` reads by path."
            }

            // Searched as bytes rather than decoded to a string: a dex is not text, and the
            // obvious `readBytes().decodeToString()` on four megabytes of it allocates a copy the
            // size of the file for a substring search that does not need one.
            val marker = "\$serializer".encodeToByteArray()
            val kept = entries.filter { it.endsWith(".dex") }.any { name ->
                zip.getInputStream(zip.getEntry(name)).use { it.readBytes().containsSlice(marker) }
            }
            check(kept) {
                "The release APK has no generated serializers — R8 removed what nothing calls. " +
                    "See androidApp/proguard-rules.pro."
            }
        }
    }
}

// `verifyComposeAssets` only. `verifyReleaseApk` is deliberately **not** wired in here: it has to
// build and minify the release variant, which is two minutes added to every `check` anybody runs,
// including the dozen a day that touch one test. The guarantee is not lost — the release workflow
// runs it explicitly against the signed artifact, which is the moment before anything reaches a
// player and the only moment a broken keep rule can still be caught for free.
tasks.named("check") { dependsOn(verifyComposeAssets) }

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)

    // The host module's first tests, and **host-side, not instrumented**: `AndroidDocumentStore`
    // takes a `File` root rather than a `Context`, so the only thing that ever needed Android is
    // gone from its constructor. See `AndroidDocumentStoreTest` for why that is worth more than
    // adding Robolectric, and Phase 4's § What is not done for the gap it closes.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
