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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Triple Triad"
            // The same `clientVersion` that `:androidApp` derives its two fields from, so the
            // installer and the APK cannot claim different releases — they already had, at "1.0.0"
            // and "0.1.0". Used as the string, which is the shape the desktop formats want; they
            // also refuse a major of 0, which is the other reason the property is not a 0.x
            // placeholder.
            packageVersion = providers.gradleProperty("clientVersion").get()
        }
    }
}
