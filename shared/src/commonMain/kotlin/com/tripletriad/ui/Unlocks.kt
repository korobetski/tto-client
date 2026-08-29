package com.tripletriad.ui

import androidx.compose.runtime.compositionLocalOf
import com.tripletriad.protocol.Unlocks

/**
 * The levels at which the two ways of moving value *between* accounts open, as this server states
 * them.
 *
 * ### Where the rule lives, and why the numbers do not live with it
 *
 * `Unlocks` is in `:core`, so `allowsMultiplayer` is one function both ends call and there is no
 * second implementation to drift out of agreement. The *numbers* arrive in `ServerInfo` on every
 * probe, because a threshold is a deployment's decision rather than a property of the code:
 * raising one is an environment variable and a restart on the server, and a client that has not
 * been updated asks the same question and gets the new answer.
 *
 * The default is `:core`'s own, which is also the right answer with no server at all — the local
 * `.sav` mode has no lobby and no auction house, so nothing consults it there.
 *
 * ### This is still not a defence
 *
 * A level is a number in a save the server also holds, and only the server's copy decides whether
 * a table may be sat at. The server enforces both doors on every endpoint that starts refereed
 * play — see its `PvpUnlock.kt` — and this is here so the lobby can say *not yet* instead of
 * offering a door that answers with a refusal. It is the same division of labour as
 * `Credentials.looksValid`.
 *
 * ### Why these two and nothing else
 *
 * They are the only places a card or a purse crosses from one account to another. A brand-new
 * account that can reach neither is worth nothing to somebody farming accounts, and that is the
 * whole of what a level gate can buy — it makes a rigged match expensive rather than impossible.
 */
internal val LocalUnlocks = compositionLocalOf { Unlocks() }
