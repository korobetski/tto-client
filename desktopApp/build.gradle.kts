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
