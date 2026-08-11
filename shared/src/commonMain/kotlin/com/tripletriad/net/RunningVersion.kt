package com.tripletriad.net

import com.tripletriad.CLIENT_VERSION
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.ClientRelease

/**
 * Which build is running, and whether a published one is newer than it.
 *
 * ### Why this is not the protocol version, and why that mattered
 *
 * `tto-core`'s `docs/RELEASING.md` § 7 parks this as an open question, and it was a real defect
 * rather than a nicety: `Connectivity` compared a deployment's announced `ClientRelease.version`
 * against `CURRENT_VERSION` — the **protocol** version, which moves only on a replay-affecting
 * break and sat at 1.0.0 while the app shipped 1.0.3. So a deployment that set
 * `TTO_CLIENT_VERSION` to the app's real number showed "update available" to every client forever,
 * *including one already running it*. The documented workaround was to put the protocol version in
 * that variable instead, which made the notice unable to announce an app release at all.
 *
 * The fix the document asks for is an `expect val clientAppVersion` in `:shared` fed by
 * `BuildConfig`. It turned out not to need one: `:shared:buildVersion` generates [CLIENT_VERSION]
 * from the single `clientVersion` in `gradle.properties`, so there is one implementation rather
 * than one per host.
 *
 * ### Its own file, because both sources ask it
 *
 * It began inside [GithubReleaseClient], where it read as a property of that endpoint. It is not:
 * "what build am I" is the app's own question, and the deployment's advice and the releases page's
 * are now decided by the same predicate. A reader should not have to open the GitHub client to
 * find out what the server comparison compares against.
 */

/**
 * The release number this build was compiled with, or null if it is not a version.
 *
 * Null is unreachable while `:androidApp` validates the same property at configuration time, and
 * is handled rather than asserted because the alternative is an app that refuses to start over a
 * string it only wanted to print.
 */
val runningVersion: AppVersion? by lazy { AppVersion.parse(CLIENT_VERSION) }

/**
 * Whether [this] is worth telling the player about.
 *
 * A *newer* release only. Equal is the ordinary case and older happens during a rollout — an APK
 * built from a tag that has not been published yet, or a hand-built one — and neither is an update.
 * Unknown ([runningVersion] null) is treated as up to date: a build that cannot say what it is has
 * no business claiming it is behind.
 */
fun ClientRelease.isNewerThanRunning(): Boolean {
    val running = runningVersion ?: return false
    return version > running
}
