package com.tripletriad.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tripletriad.log.Log

@Composable
actual fun rememberUrlOpener(): (String) -> Unit = remember {
    { url -> browse(url) }
}

/**
 * A new tab, and only for a web address.
 *
 * The scheme check is the one thing this host needs that the others do not. A desktop's `browse`
 * hands a `javascript:` URL to another program; here it would run **in the game's own origin**,
 * beside whatever the page keeps in `localStorage`. The addresses this is given come from a
 * server's answer and from GitHub's, and neither is a reason to execute anything.
 */
@Suppress("TooGenericExceptionCaught")
private fun browse(url: String) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
        Log.w(TAG) { "refused to open a link that is not a web address" }
        return
    }
    try {
        openInNewTab(url)
    } catch (failure: Throwable) {
        // A blocked pop-up does not throw — `window.open` answers null — so this is something
        // stranger. Neither is worth an error in front of a player who has been told where to go.
        Log.w(TAG, failure) { "could not open the link" }
    }
}

// `noopener`: the new tab gets no `window.opener`, so the page it lands on cannot navigate this
// one away.
@Suppress("UnusedParameter") // read by name inside `js()`, where detekt cannot see it
private fun openInNewTab(url: String): Unit = js("{ window.open(url, '_blank', 'noopener'); }")

private const val TAG = "OpenUrl"
