import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)

    // The host module's first tests. `DesktopDocumentStore` writes a player's saves and had never
    // been executed by anything but a hand run — see `DesktopDocumentStoreTest`, and Phase 4's
    // § What is not done, which recorded the gap.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.tripletriad.desktop.MainKt"

        nativeDistributions {
            // jpackage builds the format of the machine it runs on and refuses to cross-build, so
            // all three being listed here does **not** mean one invocation produces three files.
            // `packageDistributionForCurrentOS` picks the one it can make; the release workflow
            // runs it on three runners and collects the results. See `.github/workflows/release.yml`.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Triple Triad"
            description = "Triple Triad"
            // Without this jpackage writes the literal "Unknown" into the `.deb` maintainer field
            // and the MSI manufacturer, which is what it did before this line existed.
            vendor = "Triple Triad"
            // The same `clientVersion` that `:androidApp` derives its two fields from, so the
            // installer and the APK cannot claim different releases — they already had, at "1.0.0"
            // and "0.1.0". Used as the string, which is the shape the desktop formats want; they
            // also refuse a major of 0, which is the other reason the property is not a 0.x
            // placeholder.
            packageVersion = providers.gradleProperty("clientVersion").get()

            // One icon per platform, in the format each installer insists on. Derived from the
            // launcher icons already in `androidApp/src/main/res`, so the desktop build and the
            // phone show the same artwork. Without these, jpackage stamps every build with its own
            // default and the application has no identity on a taskbar.
            macOS {
                iconFile.set(project.file("icons/tto.icns"))
                // A macOS bundle is identified by this, not by its name: it is what the launch
                // services database keys on, and two applications sharing one are one application
                // as far as the system is concerned.
                bundleID = "com.tripletriad.desktop"
            }
            windows {
                iconFile.set(project.file("icons/tto.ico"))
                // Fixed, and must stay fixed. MSI decides whether an install is an *upgrade* or a
                // second copy by comparing this; a generated one would leave every release
                // installed side by side, each with its own entry in Add/Remove Programs.
                upgradeUuid = "4bb6f4f4-2a78-4238-859d-017c97fff778"
                menuGroup = "Triple Triad"
            }
            linux {
                iconFile.set(project.file("icons/tto.png"))
                // Debian package names may not hold spaces or capitals, so this cannot be derived
                // from `packageName` above and is stated instead.
                packageName = "triple-triad"
                appCategory = "Game"
                // An **email address alone** — jpackage wraps it as `<vendor> <address>`, so a
                // "Name <addr>" here nests one inside the other and produces
                // `Unknown <Triple Triad <addr>>`, which is what the first version of this line did.
                //
                // It is written into the `.deb` control file and readable by anyone who downloads
                // the package, so it is deliberately **not** defaulted to a personal address.
                // `-PdebMaintainer=` supplies a real one at build time; the placeholder uses
                // `.invalid`, reserved by RFC 6761 so that it can never resolve — an address that
                // visibly is not one beats an address that silently is not.
                debMaintainer = providers.gradleProperty("debMaintainer")
                    .getOrElse("unset@example.invalid")
            }
        }
    }
}
