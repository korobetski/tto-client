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
    CAMPAIGNS,
    CAMPAIGN,
    CAMPAIGN_MATCH,
    STATS,
    HISTORY,
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
            // Every root of a tab, and every sibling that shares one, backs out to the lobby.
            // `AUCTION` is in the list rather than under `SHOP` for the same reason `DECKS` is not
            // under `CARDS`: sibling tabs of one root are peers, and stepping back from a peer to
            // a peer would make the chevron undo a tab press.
            OPPONENTS, STATS, QUESTS, PVP, CAMPAIGNS, CARDS, DECKS, INVENTORY, SHOP, HELP,
            AUCTION, LESSONS, HISTORY,
            -> DASHBOARD
            // The one screen the profile still opens *over* itself: choosing a face is a decision
            // to make and leave, not a fourth tab.
            AVATAR -> STATS
            MATCH -> OPPONENTS
            // A ladder is opened from the tournaments tab and backs out to it, not to the solo
            // roster it used to be a carousel inside.
            CAMPAIGN -> CAMPAIGNS
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
