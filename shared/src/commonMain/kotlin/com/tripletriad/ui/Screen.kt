package com.tripletriad.ui

internal enum class Screen {
    SPLASH,
    TITLE,
    PROFILES,
    PROFILE_NEW,
    ACCOUNT,
    ACCOUNT_CONFIRM,
    PASSWORD_RESET,
    SERVERS,
    COLLECTION_CHOICE,
    DASHBOARD,
    OPPONENTS,
    MATCH,
    LESSONS,
    TUTORIAL,
    CAMPAIGN,
    CAMPAIGN_MATCH,
    STATS,
    QUESTS,
    PVP,
    PVP_MATCH,
    PVP_TABLE,
    PVP_CLAIM,
    AVATAR,
    CARDS,
    DECKS,
    INVENTORY,
    SHOP,
    AUCTION,
    HELP,
    ;

    val up: Screen
        get() = when (this) {
            SPLASH, TITLE -> this
            PROFILES, ACCOUNT, SERVERS -> TITLE
            // Both are entered from the account form and both are escapable — confirming
            // an address is optional until a gated door is reached, and a reset is
            // abandonable at either half. So back out of either lands on the form.
            ACCOUNT_CONFIRM, PASSWORD_RESET -> ACCOUNT
            PROFILE_NEW -> PROFILES
            DASHBOARD -> PROFILES
            // Back out of the collection step keeps `ff14_`, which is what the account already
            // has — so skipping it is a decision the player is allowed to make silently.
            COLLECTION_CHOICE -> DASHBOARD
            OPPONENTS, STATS, QUESTS, PVP, CARDS, DECKS, INVENTORY, SHOP, HELP -> DASHBOARD
            // Reached from the lobby's own banner and from nowhere else, so it goes back there —
            // even though it sits under the shop's tab, which is where it will *live*.
            AUCTION -> DASHBOARD
            // The course sits beside the rule book it ends at, and is reached from one place —
            // see `LessonsScreen`, which explains why it left the opponent list.
            LESSONS -> DASHBOARD
            AVATAR -> STATS
            MATCH, CAMPAIGN -> OPPONENTS
            TUTORIAL -> LESSONS
            PVP_MATCH, PVP_TABLE, PVP_CLAIM -> PVP
            CAMPAIGN_MATCH -> CAMPAIGN
        }

    val depth: Int
        get() {
            var steps = 0
            var here = this
            while (here.up != here) {
                here = here.up
                steps++
            }
            return steps
        }
}
