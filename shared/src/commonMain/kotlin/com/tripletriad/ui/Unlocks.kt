package com.tripletriad.ui

import com.tripletriad.model.GameSave

/**
 * The levels at which the two ways of moving value *between* accounts open.
 *
 * Both are the client's opinion and neither is a defence on its own — a level is a number in a save
 * the server also holds, and only the server's copy can decide whether a table may be sat at or a
 * card put up for sale. This is here so the lobby can say "not yet" instead of offering a door that
 * answers with a refusal, which is the same division of labour as `Credentials.looksValid`.
 *
 * Why these two and nothing else: they are the only places a card or a purse crosses from one
 * account to another. A brand-new account that can reach neither is worth nothing to somebody
 * farming accounts, and that is the whole of what a level gate can buy.
 */
internal object Unlocks {
    /** Playing another person — for stakes, for trade rules, or for nothing at all. */
    const val MULTIPLAYER_LEVEL: Int = 5

    /** Selling to, and buying from, other players. */
    const val AUCTION_LEVEL: Int = 5

    fun multiplayer(profile: GameSave): Boolean = profile.level >= MULTIPLAYER_LEVEL

    fun auction(profile: GameSave): Boolean = profile.level >= AUCTION_LEVEL
}
