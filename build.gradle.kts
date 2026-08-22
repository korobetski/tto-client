plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

val ktlintPluginId = libs.plugins.ktlint.get().pluginId
val detektPluginId = libs.plugins.detekt.get().pluginId
// Resolved against this file, so it stays correct in every module: `allprojects` runs the
// block below with each subproject as receiver, and a bare relative path there would look
// for `androidApp/detekt.yml` and so on.
val detektConfigFile = rootProject.file("detekt/detekt.yml")

/*
 * Whether this invocation runs the linters at all. `-Ptto.lint=false` says it does not.
 *
 * Both plugins wire themselves into `check`, so every `build` runs them again — and CI already has
 * a `quality` job that runs both across every module before any of the build jobs finish. That is
 * one duplicate run per build job, on a workflow whose shared job takes eleven minutes.
 *
 * **`-x` cannot express this.** The ktlint plugin wires each `ktlint<SourceSet>SourceSetCheck` into
 * `check` individually rather than only through the `ktlintCheck` aggregate, so
 * `-x ktlintCheck` removes the aggregate and leaves all thirty-seven of them in the graph —
 * measured on `:shared:build`, which went from 222 tasks to 221. Hence a property.
 *
 * The default is on, and has to stay on: a flag that quietly became the normal way to build would
 * be a way to ship unformatted code without noticing. `quality` passes nothing and stays the job
 * that reports.
 *
 * **PowerShell needs it quoted** — `'-Ptto.lint=false'`. Unquoted, PowerShell breaks the token at
 * the dot and hands Gradle `-Ptto` and `.lint=false`, which fails with `Task '.lint=false' not
 * found`. Nothing to do with Gradle, and it bites `-Ptto.screenshots=1` in the README the same way;
 * CI runs bash on ubuntu, where the unquoted form in `build.yml` is correct as written.
 */
val lintEnabled = providers.gradleProperty("tto.lint").orNull != "false"

allprojects {
    apply(plugin = ktlintPluginId)
    apply(plugin = detektPluginId)

    if (!lintEnabled) {
        tasks.matching { it.name.startsWith("ktlint") || it.name.startsWith("runKtlintCheckOver") }
            .configureEach { enabled = false }
        tasks.matching { it.name == "detekt" }.configureEach { enabled = false }
    }

    // Formatting rules live in .editorconfig, which the IDE reads as well, so the
    // plugin needs no configuration beyond skipping generated sources.
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            // Compose resource accessors are generated into build/; they are not ours
            // to format.
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(detektConfigFile)
        // detekt defaults to the JVM `main`/`test` source sets, which in a KMP module
        // are empty — point it at every source set instead.
        source.setFrom(files("src"))
        parallel = true
    }
}
