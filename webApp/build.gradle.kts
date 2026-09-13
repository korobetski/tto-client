// The browser host: `:shared` compiled to wasm, drawn into one canvas, and nothing else.
//
// It is the counterpart of `:desktopApp` and `:androidApp` rather than a website — the pages around
// it are `tto-web`'s, and so is the deployment. What this module produces is a directory of static
// files, `build/dist/wasmJs/productionExecutable/`, which `tto-web` serves on its own host. See
// `tto-server/docs/web-platform.md` § The browser game for why the game is built here and delivered
// there.
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/*
 * The API's top-level prefixes that the game calls, for the development server to forward.
 *
 * The same list Caddy proxies on the game's host in production, minus nothing and plus nothing:
 * `/health`, `/metrics` and `/admin` are refused there and have no reason to be reachable here.
 * A route the server adds under a new prefix has to be added to both — until it is, the page gets
 * the dev server's own 404, which is loud and immediate.
 */
val apiPrefixes = listOf(
    "/server",
    "/accounts",
    "/sessions",
    "/me",
    "/matches",
    "/pve",
    "/pvp",
    "/auctions",
)

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        // `outputModuleName` is the name of the loader script `index.html` asks for, so the two are
        // stated together here rather than left to the project name.
        outputModuleName = "tto"
        browser {
            commonWebpackConfig {
                outputFileName = "tto.js"
                // `wasmJsBrowserDevelopmentRun` only. The API is reached at the page's own origin
                // in production — Caddy proxies it on the game's host — so in development the dev
                // server has to play that part and forward the API's routes to a local server.
                //
                // On 8082: the local server holds 8080, which is also webpack's default, and
                // `tto-server`'s development Caddy (`--profile web`) holds 8081.
                devServer = (devServer ?: DevServer()).apply {
                    port = 8082
                    proxy = mutableListOf(
                        DevServer.Proxy(
                            context = apiPrefixes.toMutableList(),
                            target = providers.gradleProperty("tto.apiServer")
                                .getOrElse("http://127.0.0.1:8080"),
                        ),
                    )
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        // `js("…")` is the whole of this module's contact with the page — the origin, the clock —
        // and Kotlin still marks it experimental. Opted into once, for the module, rather than on
        // each of the three one-line functions that are nothing but that call.
        all { languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop") }
        getByName("wasmJsMain").dependencies {
            implementation(project(":shared"))
        }
    }
}
