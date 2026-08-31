package com.tripletriad.i18n

object StringKeys {
    const val NEXT_MATCH: String = "STR_NEXT_MATCH"

    const val YOU_WIN: String = "STR_YOU_WIN"
    const val YOU_LOSE: String = "STR_YOU_LOSE"

    const val DRAW: String = "STR_DRAW"

    const val SUDDEN_DEATH: String = "RULE_SUDDEN_DEATH"

    const val LOADING_CARDS: String = "APP_LOADING_CARDS"
    const val SIDE_BLUE: String = "APP_SIDE_BLUE"
    const val SIDE_RED: String = "APP_SIDE_RED"

    const val TURN_PICK_CARD: String = "APP_TURN_PICK_CARD"

    const val TURN_PICK_CELL: String = "APP_TURN_PICK_CELL"

    // ---- Main menu. All three come from the legacy bundles, so all four languages have them.
    // This app shows three of them for now.
    const val PLAY: String = "STR_PLAY"

    const val SETTINGS: String = "STR_SETTINGS"
    const val QUIT: String = "STR_QUIT"

    // ---- Options screen.
    const val GENERAL_SETTINGS: String = "STR_GENERAL_SETTINGS"
    const val AUDIO_SETTINGS: String = "STR_AUDIO_SETTINGS"
    const val LANGUAGE: String = "STR_LANGUAGE"
    const val BACKGROUND_VOLUME: String = "STR_BACKGROUND_VOLUME"
    const val NOISE_VOLUME: String = "STR_NOISE_VOLUME"

    const val BACK: String = "APP_BACK"

    const val AUDIO_PENDING: String = "APP_AUDIO_PENDING"

    // ---- Splash. One key per `StartupPhase`, in the same order.
    const val STARTUP_SETTINGS: String = "APP_STARTUP_SETTINGS"
    const val STARTUP_ART: String = "APP_STARTUP_ART"
    const val STARTUP_OPPONENTS: String = "APP_STARTUP_OPPONENTS"
    const val STARTUP_READY: String = "APP_STARTUP_READY"

    // ---- Profiles. `STR_PROFILE` is "Character" in en_US, which is the original's word for a
    // save: `LoadScreen` lists characters, not files. Kept, rather than substituting "profile".
    const val PROFILE: String = "STR_PROFILE"
    const val PROFILES: String = "STR_LOAD_GAME"
    const val NEW_PROFILE: String = "STR_NEW_GAME"
    const val USERNAME: String = "STR_USERNAME"

    const val COLLECTION: String = "STR_MODE"
    const val LEVEL: String = "STR_LEVEL"
    const val MGP: String = "STR_MGP"
    const val WINS: String = "STR_WINS"
    const val DEFEATS: String = "STR_DEFEATS"
    const val DRAWS: String = "STR_DRAWS"
    const val DELETE: String = "STR_DELETE"

    const val DELETE_CONFIRM: String = "STR_DELETE_SAVE_CONFIRMATION_MESSAGE"
    const val CANCEL: String = "STR_CANCEL"
    const val START: String = "STR_START"

    const val NO_PROFILE: String = "APP_NO_PROFILE"

    const val AVATAR: String = "APP_AVATAR"

    // ---- Opponents.
    const val OPPONENTS: String = "STR_OPPONENTS"
    const val RULES: String = "STR_RULES"
    const val MATCH_FEE: String = "STR_MATCH_FEE"
    const val REWARDS: String = "STR_REWARDS"

    const val CHALLENGE: String = "STR_REGISTER_MATCH"

    const val NO_OPPONENT: String = "APP_NO_OPPONENT"

    const val RANDOM_OPPONENT: String = "APP_RANDOM_OPPONENT"

    const val XP: String = "APP_XP"

    const val DIFFICULTY: String = "APP_DIFFICULTY"

    // ---- Match.
    const val OPPONENT_TURN: String = "APP_OPPONENT_TURN"

    const val ACHIEVEMENT_EARNED: String = "APP_ACHIEVEMENT_EARNED"

    const val REMATCH: String = "STR_REMATCH"

    // ---- Dashboard.
    const val MULTIPLAYER: String = "STR_MULTIPLAYER"
    const val CARD_LIST: String = "STR_CARD_LIST"

    const val CARDS: String = "APP_CARDS"

    const val HOME: String = "APP_HOME"
    const val CARD_DECKS: String = "STR_CARD_DECKS"
    const val INVENTORY: String = "STR_INVENTORY"
    const val SHOP: String = "STR_SHOP"
    const val HELP: String = "STR_HELP"

    const val LOGOUT: String = "STR_LOGOUT"

    // ---- Card list and card detail.
    const val CARD_INFOS: String = "STR_CARD_INFOS"
    const val TOTAL: String = "STR_TOTAL"
    const val SIDES: String = "STR_SIDES"
    const val RARITY: String = "STR_RARITY"
    const val CARD_TYPE: String = "STR_CARD_TYPE"

    const val PICK_CARD: String = "APP_PICK_CARD"

    const val OWNED: String = "APP_OWNED"

    // ---- Decks.
    const val DECK: String = "STR_DECK"
    const val DECK_POWER: String = "STR_DECK_POWER"
    const val RESET_DECK: String = "STR_RESET_DECK"
    const val SAVE: String = "STR_SAVE"

    const val DECK_MISSING_CARDS: String = "APP_DECK_MISSING_CARDS"

    /** The star-rank caps, as a label in front of the live per-rank counters. `DeckLimits`. */
    const val DECK_LIMITS: String = "APP_DECK_LIMITS"

    /** One broken cap: how many of the rank the deck names, the rank, and how many are allowed. */
    const val DECK_OVER_LIMIT: String = "APP_DECK_OVER_LIMIT"

    const val CHOOSE_DECK: String = "STR_CHOOSE_DECK"

    const val RANDOM_DECK: String = "RULE_RANDOM"

    const val NO_FULL_DECK: String = "APP_NO_FULL_DECK"

    // ---- Inventory and shop.
    const val USE: String = "STR_USE"
    const val SELL: String = "STR_SELL"
    const val DISCARD: String = "STR_DISCARD"
    const val BUY: String = "STR_BUY"

    const val CARD_SHOP: String = "STR_CARD_SHOP"

    const val EMPTY_BAG: String = "APP_EMPTY_BAG"

    const val OBTAINED: String = "APP_OBTAINED"

    const val ALREADY_OWNED: String = "APP_ALREADY_OWNED"

    const val ITEM_REFUSED: String = "APP_ITEM_REFUSED"

    const val NOTHING_HAPPENED: String = "APP_NOTHING_HAPPENED"

    const val ACTION_FAILED: String = "APP_ACTION_FAILED"

    const val UNKNOWN_ITEM: String = "APP_UNKNOWN_ITEM"

    const val STARTER_PACK: String = "APP_STARTER_PACK"
    const val STARTER_PACK_DESC: String = "APP_STARTER_PACK_DESC"

    const val PACK_SEALED: String = "APP_PACK_SEALED"
    const val PACK_BREAK_SEAL: String = "APP_PACK_BREAK_SEAL"
    const val PACK_SPENT: String = "APP_PACK_SPENT"
    const val PACK_REVEAL: String = "APP_PACK_REVEAL"
    const val PACK_TO_COLLECTION: String = "APP_PACK_TO_COLLECTION"

    const val PACK_ODDS: String = "APP_PACK_ODDS"

    const val ALL: String = "APP_ALL"

    const val FREE: String = "APP_FREE"

    const val CLAIM: String = "APP_CLAIM"

    // ---- Character statistics.
    const val ACHIEVEMENTS_LIST: String = "STR_ACHIEVEMENTS_LIST"
    const val FORFEITS: String = "STR_FORFEITS"

    const val MATCHES: String = "APP_MATCHES"

    const val WIN_RATE: String = "APP_WIN_RATE"

    const val BOONS: String = "APP_BOONS"
    const val BOOSTERS: String = "APP_BOOSTERS"

    const val NO_ACHIEVEMENT: String = "APP_NO_ACHIEVEMENT"

    const val NEXT_TIER: String = "APP_NEXT_TIER"

    // ---- The daily quests, which the original had no equivalent of at all.
    const val QUESTS: String = "APP_QUESTS"
    const val QUESTS_RESET: String = "APP_QUESTS_RESET"
    const val QUEST_DONE: String = "APP_QUEST_DONE"
    const val NO_QUEST: String = "APP_NO_QUEST"

    const val QUEST_PLAY_3: String = "APP_QUEST_PLAY_3"
    const val QUEST_WIN_1: String = "APP_QUEST_WIN_1"
    const val QUEST_WIN_3: String = "APP_QUEST_WIN_3"
    const val QUEST_BEAT: String = "APP_QUEST_BEAT"
    const val QUEST_RULE: String = "APP_QUEST_RULE"
    const val QUEST_PVP_1: String = "APP_QUEST_PVP_1"

    // ---- Playing another person. All `APP_`: the legacy bundles have no key for any of this.
    const val PVP_TABLES: String = "APP_PVP_TABLES"
    const val PVP_NO_TABLE: String = "APP_PVP_NO_TABLE"
    const val PVP_HOST: String = "APP_PVP_HOST"
    const val PVP_HOST_OPEN: String = "APP_PVP_HOST_OPEN"
    const val PVP_HOST_CANCEL: String = "APP_PVP_HOST_CANCEL"
    const val PVP_JOIN: String = "APP_PVP_JOIN"

    const val PVP_TABLE_BY: String = "APP_PVP_TABLE_BY"
    const val PVP_TABLE_MINE: String = "APP_PVP_TABLE_MINE"
    const val PVP_TABLE_FREE: String = "APP_PVP_TABLE_FREE"

    /** Shown on a table whose wager is a large share of *the reader's* purse. */
    const val PVP_TABLE_HEAVY: String = "APP_PVP_TABLE_HEAVY"

    /** Shown on one the reader's level does not allow them to sit at. */
    const val PVP_TABLE_OVER_LIMIT: String = "APP_PVP_TABLE_OVER_LIMIT"

    /** The second press on a heavy table. See `PvpScreen.TableRow`. */
    const val PVP_JOIN_CONFIRM: String = "APP_PVP_JOIN_CONFIRM"

    const val PVP_TABLE_EXPIRES: String = "APP_PVP_TABLE_EXPIRES"

    const val PVP_DECK: String = "APP_PVP_DECK"
    const val PVP_DECK_ANY: String = "APP_PVP_DECK_ANY"

    const val PVP_STAKE: String = "APP_PVP_STAKE"
    const val PVP_STAKE_MGP: String = "APP_PVP_STAKE_MGP"

    /** Under the stake field: the most this player may wager, purse and level considered. */
    const val PVP_STAKE_LIMIT: String = "APP_PVP_STAKE_LIMIT"
    const val PVP_STAKE_OVER_LIMIT: String = "APP_PVP_STAKE_OVER_LIMIT"
    const val PVP_STAKE_OVER_PURSE: String = "APP_PVP_STAKE_OVER_PURSE"
    const val PVP_STAKE_MAX: String = "APP_PVP_STAKE_MAX"
    const val PVP_STAKE_HEAVY: String = "APP_PVP_STAKE_HEAVY"
    const val PVP_TRADE: String = "APP_PVP_TRADE"
    const val PVP_TRADE_NONE: String = "APP_PVP_TRADE_NONE"
    const val PVP_TRADE_ONE: String = "APP_PVP_TRADE_ONE"
    const val PVP_TRADE_DIFF: String = "APP_PVP_TRADE_DIFF"
    const val PVP_TRADE_DIRECT: String = "APP_PVP_TRADE_DIRECT"
    const val PVP_TRADE_ALL: String = "APP_PVP_TRADE_ALL"

    const val PVP_FORMAT: String = "APP_PVP_FORMAT"

    const val PVP_RULES_PICK: String = "APP_PVP_RULES_PICK"
    const val PVP_ROULETTE: String = "APP_PVP_ROULETTE"
    const val PVP_ROULETTE_HINT: String = "APP_PVP_ROULETTE_HINT"

    const val PVP_STAKE_WON: String = "APP_PVP_STAKE_WON"
    const val PVP_STAKE_LOST: String = "APP_PVP_STAKE_LOST"
    const val PVP_WON_CARDS: String = "APP_PVP_WON_CARDS"
    const val PVP_LOST_CARDS: String = "APP_PVP_LOST_CARDS"

    const val PVP_ERROR_AFFORD: String = "APP_PVP_ERROR_AFFORD"
    const val PVP_ERROR_STAKE: String = "APP_PVP_ERROR_STAKE"
    const val PVP_ERROR_TABLE_GONE: String = "APP_PVP_ERROR_TABLE_GONE"
    const val PVP_ERROR_RULES: String = "APP_PVP_ERROR_RULES"
    const val PVP_ERROR_OWN_TABLE: String = "APP_PVP_ERROR_OWN_TABLE"
    const val PVP_ERROR_IN_MATCH: String = "APP_PVP_ERROR_IN_MATCH"
    const val PVP_ERROR_NO_PLAYER: String = "APP_PVP_ERROR_NO_PLAYER"
    const val PVP_ERROR_YOURSELF: String = "APP_PVP_ERROR_YOURSELF"
    const val PVP_ERROR_NOTHING_OWED: String = "APP_PVP_ERROR_NOTHING_OWED"

    const val PVP_CLAIM: String = "APP_PVP_CLAIM"
    const val PVP_CLAIM_TITLE: String = "APP_PVP_CLAIM_TITLE"
    const val PVP_CLAIM_PROMPT: String = "APP_PVP_CLAIM_PROMPT"
    const val PVP_CLAIM_CONFIRM: String = "APP_PVP_CLAIM_CONFIRM"
    const val PVP_CLAIM_PENDING: String = "APP_PVP_CLAIM_PENDING"
    const val PVP_CLAIM_NONE: String = "APP_PVP_CLAIM_NONE"

    const val PVP_CLAIM_WAIT: String = "APP_PVP_CLAIM_WAIT"

    const val PVP_CHALLENGE: String = "APP_PVP_CHALLENGE"
    const val PVP_INVITE: String = "APP_PVP_INVITE"

    const val PVP_INVITE_TO: String = "APP_PVP_INVITE_TO"
    const val PVP_FROM: String = "APP_PVP_FROM"
    const val PVP_SENT_TO: String = "APP_PVP_SENT_TO"
    const val PVP_ACCEPT: String = "APP_PVP_ACCEPT"
    const val PVP_DECLINE: String = "APP_PVP_DECLINE"
    const val PVP_NO_CHALLENGE: String = "APP_PVP_NO_CHALLENGE"

    const val PVP_FORFEIT: String = "APP_PVP_FORFEIT"
    const val PVP_YOU_LEFT: String = "APP_PVP_YOU_LEFT"
    const val PVP_THEY_LEFT: String = "APP_PVP_THEY_LEFT"
    const val PVP_OVER: String = "APP_PVP_OVER"
    const val PVP_NO_MATCH: String = "APP_PVP_NO_MATCH"

    // ---- The tutorial's nine lines.
    const val TUTORIAL_1: String = "APP_TUTORIAL_1"
    const val TUTORIAL_2: String = "APP_TUTORIAL_2"
    const val TUTORIAL_3: String = "APP_TUTORIAL_3"
    const val TUTORIAL_4: String = "APP_TUTORIAL_4"
    const val TUTORIAL_5: String = "APP_TUTORIAL_5"
    const val TUTORIAL_6: String = "APP_TUTORIAL_6"
    const val TUTORIAL_7: String = "APP_TUTORIAL_7"
    const val TUTORIAL_8: String = "APP_TUTORIAL_8"
    const val TUTORIAL_9: String = "APP_TUTORIAL_9"

    const val TUTORIAL: String = "APP_TUTORIAL"

    const val LESSONS: String = "APP_LESSONS"
    const val LESSONS_BLURB: String = "APP_LESSONS_BLURB"
    const val LESSON_DONE: String = "APP_LESSON_DONE"
    const val LESSON_NEXT: String = "APP_LESSON_NEXT"

    const val LESSON_COMPLETE: String = "APP_LESSON_COMPLETE"

    const val LESSONS_ALL_DONE: String = "APP_LESSONS_ALL_DONE"

    const val LESSON_TO_RULES: String = "APP_LESSON_TO_RULES"

    const val LESSON_TITLE_BASICS: String = "APP_LESSON_TITLE_BASICS"
    const val LESSON_TITLE_SAME: String = "APP_LESSON_TITLE_SAME"
    const val LESSON_TITLE_PLUS: String = "APP_LESSON_TITLE_PLUS"
    const val LESSON_TITLE_COMBO: String = "APP_LESSON_TITLE_COMBO"
    const val LESSON_TITLE_SAME_WALL: String = "APP_LESSON_TITLE_SAME_WALL"
    const val LESSON_TITLE_REVERSE: String = "APP_LESSON_TITLE_REVERSE"
    const val LESSON_TITLE_FALLEN_ACE: String = "APP_LESSON_TITLE_FALLEN_ACE"
    const val LESSON_TITLE_REVERSE_FALLEN_ACE: String = "APP_LESSON_TITLE_REVERSE_FALLEN_ACE"
    const val LESSON_TITLE_ELEMENTAL: String = "APP_LESSON_TITLE_ELEMENTAL"
    const val LESSON_TITLE_BONUS: String = "APP_LESSON_TITLE_BONUS"
    const val LESSON_TITLE_ORDER: String = "APP_LESSON_TITLE_ORDER"
    const val LESSON_TITLE_EXAM: String = "APP_LESSON_TITLE_EXAM"

    const val LESSON_BASICS_WIN: String = "APP_LESSON_BASICS_WIN"
    const val LESSON_BASICS_LOSE: String = "APP_LESSON_BASICS_LOSE"
    const val LESSON_BASICS_DRAW: String = "APP_LESSON_BASICS_DRAW"
    const val LESSON_SAME_1: String = "APP_LESSON_SAME_1"
    const val LESSON_SAME_2: String = "APP_LESSON_SAME_2"
    const val LESSON_SAME_DONE: String = "APP_LESSON_SAME_DONE"
    const val LESSON_PLUS_1: String = "APP_LESSON_PLUS_1"
    const val LESSON_PLUS_2: String = "APP_LESSON_PLUS_2"
    const val LESSON_PLUS_DONE: String = "APP_LESSON_PLUS_DONE"
    const val LESSON_COMBO_1: String = "APP_LESSON_COMBO_1"
    const val LESSON_COMBO_2: String = "APP_LESSON_COMBO_2"
    const val LESSON_COMBO_DONE: String = "APP_LESSON_COMBO_DONE"
    const val LESSON_SAME_WALL_1: String = "APP_LESSON_SAME_WALL_1"
    const val LESSON_SAME_WALL_2: String = "APP_LESSON_SAME_WALL_2"
    const val LESSON_SAME_WALL_DONE: String = "APP_LESSON_SAME_WALL_DONE"
    const val LESSON_REVERSE_1: String = "APP_LESSON_REVERSE_1"
    const val LESSON_REVERSE_2: String = "APP_LESSON_REVERSE_2"
    const val LESSON_REVERSE_DONE: String = "APP_LESSON_REVERSE_DONE"
    const val LESSON_FALLEN_ACE_1: String = "APP_LESSON_FALLEN_ACE_1"
    const val LESSON_FALLEN_ACE_2: String = "APP_LESSON_FALLEN_ACE_2"
    const val LESSON_FALLEN_ACE_DONE: String = "APP_LESSON_FALLEN_ACE_DONE"
    const val LESSON_REVERSE_FALLEN_ACE_1: String = "APP_LESSON_REVERSE_FALLEN_ACE_1"
    const val LESSON_REVERSE_FALLEN_ACE_2: String = "APP_LESSON_REVERSE_FALLEN_ACE_2"
    const val LESSON_REVERSE_FALLEN_ACE_DONE: String = "APP_LESSON_REVERSE_FALLEN_ACE_DONE"
    const val LESSON_ELEMENTAL_1: String = "APP_LESSON_ELEMENTAL_1"
    const val LESSON_ELEMENTAL_2: String = "APP_LESSON_ELEMENTAL_2"
    const val LESSON_ELEMENTAL_DONE: String = "APP_LESSON_ELEMENTAL_DONE"

    const val LESSON_BONUS_1: String = "APP_LESSON_BONUS_1"
    const val LESSON_BONUS_2: String = "APP_LESSON_BONUS_2"
    const val LESSON_BONUS_3: String = "APP_LESSON_BONUS_3"
    const val LESSON_BONUS_DONE: String = "APP_LESSON_BONUS_DONE"
    const val LESSON_ORDER_1: String = "APP_LESSON_ORDER_1"
    const val LESSON_ORDER_2: String = "APP_LESSON_ORDER_2"
    const val LESSON_ORDER_3: String = "APP_LESSON_ORDER_3"
    const val LESSON_ORDER_DONE: String = "APP_LESSON_ORDER_DONE"

    const val LESSON_EXAM_START: String = "APP_LESSON_EXAM_START"
    const val LESSON_EXAM_WIN: String = "APP_LESSON_EXAM_WIN"
    const val LESSON_EXAM_LOSE: String = "APP_LESSON_EXAM_LOSE"
    const val LESSON_EXAM_DRAW: String = "APP_LESSON_EXAM_DRAW"

    // ---- The three tournament ladders.
    const val CAMPAIGNS: String = "STR_CAMPAIGNS"
    const val CAMPAIGN_FINAL_REWARD: String = "APP_CAMPAIGN_FINAL_REWARD"

    // `balamb`, unlike `cc`/`gs`, has no name of its own in the imported bundles --
    // see `authored_ladder()` in tools/extract_campaigns.py. Its *opponents* are the
    // catalogue's own and keep their imported `STR_NPC_*` names.
    const val CAMPAIGN_BALAMB: String = "APP_CAMPAIGN_BALAMB"

    const val CAMPAIGN_STEP: String = "APP_CAMPAIGN_STEP"

    // ---- The bilan a tournament run ends on, win or lose.
    const val CAMPAIGN_RESULTS: String = "APP_CAMPAIGN_RESULTS"

    const val CAMPAIGN_COMPLETE: String = "APP_CAMPAIGN_COMPLETE"

    const val CAMPAIGN_ELIMINATED: String = "APP_CAMPAIGN_ELIMINATED"

    const val CAMPAIGN_NOT_REACHED: String = "APP_CAMPAIGN_NOT_REACHED"

    const val CAMPAIGN_LOCKED: String = "APP_CAMPAIGN_LOCKED"

    const val CAMPAIGN_ENTERED_TODAY: String = "APP_CAMPAIGN_ENTERED_TODAY"

    // The tournament tile's subtitle: "5 rounds", read off `Campaign.steps.size`. Distinct from
    // `CAMPAIGN_STEP` ("Match 3 of 5"), which is a position inside a run under way rather than
    // a fact about the ladder shown before entering one.
    const val CAMPAIGN_ROUNDS: String = "APP_CAMPAIGN_ROUNDS"

    /** No deck this ladder's own format admits. The one gate the player fixes in the editor. */
    const val CAMPAIGN_NO_DECK: String = "APP_CAMPAIGN_NO_DECK"

    // ---- The account the menu remembers.
    const val SERVERS: String = "APP_SERVERS"

    const val CONTINUE: String = "STR_CONTINUE"

    const val SESSION_RESTORED: String = "APP_SESSION_RESTORED"

    const val SESSION_CONNECTING: String = "APP_SESSION_CONNECTING"

    const val SESSION_LAPSED: String = "APP_SESSION_LAPSED"

    const val SIGN_IN_AGAIN: String = "APP_SIGN_IN_AGAIN"

    const val SWITCH_ACCOUNT: String = "APP_SWITCH_ACCOUNT"

    // ---- The sign-in form, which was the last screen written in hard-coded English.
    const val SIGN_IN: String = "APP_SIGN_IN"

    const val CREATE_ACCOUNT: String = "APP_CREATE_ACCOUNT"

    const val PASSWORD: String = "APP_PASSWORD"

    const val ACCOUNT_BLURB: String = "APP_ACCOUNT_BLURB"

    const val ACCOUNT_TO_REGISTER: String = "APP_ACCOUNT_TO_REGISTER"
    const val ACCOUNT_TO_SIGN_IN: String = "APP_ACCOUNT_TO_SIGN_IN"

    const val UPDATE_NEEDED: String = "APP_UPDATE_NEEDED"

    // ---- What a refused request is told to the player. See `AccountResult.message`.
    const val ERROR_OFFLINE: String = "APP_ERROR_OFFLINE"
    const val ERROR_UPDATE: String = "APP_ERROR_UPDATE"

    const val ERROR_STATUS: String = "APP_ERROR_STATUS"

    /** One line for every `PveRefusal` but `UNDEALABLE`: the board on screen is out of date. */
    const val ERROR_STALE_MATCH: String = "APP_ERROR_STALE_MATCH"

    /** `PveRefusal.UNDEALABLE`, which retrying never fixes — the deck is the wrong pool. */
    const val ERROR_UNDEALABLE: String = "APP_ERROR_UNDEALABLE"

    const val LOADING: String = "APP_LOADING"

    const val RETRY: String = "APP_RETRY"

    const val CARD_FACE_DOWN: String = "APP_CARD_FACE_DOWN"

    const val PROFILE_LOCAL_NOTE: String = "APP_PROFILE_LOCAL_NOTE"

    const val NO_SEEDS: String = "APP_NO_SEEDS"

    const val ERROR_THROTTLED: String = "APP_ERROR_THROTTLED"
    const val ERROR_THROTTLED_IN: String = "APP_ERROR_THROTTLED_IN"
    const val ERROR_NAME_TAKEN: String = "APP_ERROR_NAME_TAKEN"
    const val ERROR_BAD_CREDENTIALS: String = "APP_ERROR_BAD_CREDENTIALS"
    const val ERROR_EXPIRED: String = "APP_ERROR_EXPIRED"

    const val REWARD_CARDS: String = "APP_REWARD_CARDS"

    const val HELP_FAMILY_SIGHT: String = "APP_HELP_FAMILY_SIGHT"
    const val HELP_FAMILY_PLAY: String = "APP_HELP_FAMILY_PLAY"
    const val HELP_FAMILY_CAPTURE: String = "APP_HELP_FAMILY_CAPTURE"
    const val HELP_FAMILY_ELEMENTS: String = "APP_HELP_FAMILY_ELEMENTS"

    const val OPPONENTS_LOCKED: String = "APP_OPPONENTS_LOCKED"

    const val OPPONENTS_UNEARNED: String = "APP_OPPONENTS_UNEARNED"

    /** The three shelves above the roster — see `OpponentShelves`. */
    const val OPPONENTS_NEW: String = "APP_OPPONENTS_NEW"
    const val OPPONENTS_WANTED: String = "APP_OPPONENTS_WANTED"
    const val OPPONENTS_TIMED: String = "APP_OPPONENTS_TIMED"

    // ---- The server list, which had no translated string on it at all.
    const val SERVERS_BLURB: String = "APP_SERVERS_BLURB"

    const val SERVERS_CHECK: String = "APP_SERVERS_CHECK"
    const val SERVERS_CHECKING: String = "APP_SERVERS_CHECKING"

    const val SERVER_UNKNOWN: String = "APP_SERVER_UNKNOWN"
    const val SERVER_CHECKING: String = "APP_SERVER_CHECKING"
    const val SERVER_ONLINE: String = "APP_SERVER_ONLINE"
    const val SERVER_DEGRADED: String = "APP_SERVER_DEGRADED"
    const val SERVER_OUTDATED: String = "APP_SERVER_OUTDATED"
    const val SERVER_UNREACHABLE: String = "APP_SERVER_UNREACHABLE"
    const val SERVER_UNUSABLE: String = "APP_SERVER_UNUSABLE"

    const val UPDATE_REQUIRED: String = "APP_UPDATE_REQUIRED"
    const val UPDATE_REQUIRED_BODY: String = "APP_UPDATE_REQUIRED_BODY"
    const val UPDATE_AVAILABLE: String = "APP_UPDATE_AVAILABLE"
    const val UPDATE_AVAILABLE_BODY: String = "APP_UPDATE_AVAILABLE_BODY"
    const val UPDATE_GET: String = "APP_UPDATE_GET"

    const val SELL_ALL: String = "APP_SELL_ALL"

    const val ACCOUNT_SETTINGS: String = "APP_ACCOUNT_SETTINGS"

    const val DELETE_ACCOUNT: String = "APP_DELETE_ACCOUNT"
    const val DELETE_ACCOUNT_BODY: String = "APP_DELETE_ACCOUNT_BODY"

    const val DELETE_ACCOUNT_CONFIRM: String = "APP_DELETE_ACCOUNT_CONFIRM"

    /** The way back into a match the server still has open. Takes the opponent's name. */
    const val MATCH_RESUME: String = "APP_MATCH_RESUME"

    /** The title screen's invitation, when one tap is all it takes. */
    const val TITLE_CONTINUE: String = "APP_TITLE_CONTINUE"

    /** The title screen's invitation on a device with local characters but none loaded. */
    const val TITLE_CHOOSE: String = "APP_TITLE_CHOOSE"

    const val LOBBY_RESUME: String = "APP_LOBBY_RESUME"
    const val LOBBY_TODAY: String = "APP_LOBBY_TODAY"
    const val LOBBY_MORE: String = "APP_LOBBY_MORE"
    const val LOBBY_SOON: String = "APP_LOBBY_SOON"

    const val AUCTION: String = "APP_AUCTION"
    const val AUCTION_BLURB: String = "APP_AUCTION_BLURB"

    // ---- The auction house ----

    /*
     * Every key below is `APP_`-owned and translated in all four bundles, which is what
     * `StringsBundleTest` enforces: the house moves real money between real players, and a screen
     * that falls back to English for one of them is a screen somebody agrees to without reading.
     */

    const val AUCTION_ROOM: String = "APP_AUCTION_ROOM"
    const val AUCTION_MINE: String = "APP_AUCTION_MINE"
    const val AUCTION_SELL: String = "APP_AUCTION_SELL"

    const val AUCTION_EMPTY: String = "APP_AUCTION_EMPTY"
    const val AUCTION_MINE_EMPTY: String = "APP_AUCTION_MINE_EMPTY"
    const val AUCTION_FAILED: String = "APP_AUCTION_FAILED"

    /** Why the house is shut on a character with no account behind it. */
    const val AUCTION_NEEDS_SERVER: String = "APP_AUCTION_NEEDS_SERVER"

    /** The desk with nothing on it, on a wide screen where the pane is always there. */
    const val AUCTION_PICK_LOT: String = "APP_AUCTION_PICK_LOT"

    const val AUCTION_SELLER: String = "APP_AUCTION_SELLER"
    const val AUCTION_START_PRICE: String = "APP_AUCTION_START_PRICE"
    const val AUCTION_CURRENT_PRICE: String = "APP_AUCTION_CURRENT_PRICE"
    const val AUCTION_BID_COUNT: String = "APP_AUCTION_BID_COUNT"
    const val AUCTION_RESERVE: String = "APP_AUCTION_RESERVE"

    /** Said to a bidder, who is never told the reserve itself — see `AuctionDesk`. */
    const val AUCTION_RESERVE_MET: String = "APP_AUCTION_RESERVE_MET"
    const val AUCTION_RESERVE_NOT_MET: String = "APP_AUCTION_RESERVE_NOT_MET"

    const val AUCTION_YOUR_BID: String = "APP_AUCTION_YOUR_BID"
    const val AUCTION_ENDS: String = "APP_AUCTION_ENDS"

    /** Takes a countdown already formatted by `countdownText`. */
    const val AUCTION_ENDS_IN: String = "APP_AUCTION_ENDS_IN"
    const val AUCTION_ENDED: String = "APP_AUCTION_ENDED"

    const val AUCTION_NO_BIDS: String = "APP_AUCTION_NO_BIDS"

    /** Takes how many bids have been placed. */
    const val AUCTION_BIDS: String = "APP_AUCTION_BIDS"

    const val AUCTION_YOUR_LOT: String = "APP_AUCTION_YOUR_LOT"
    const val AUCTION_YOU_LEAD: String = "APP_AUCTION_YOU_LEAD"
    const val AUCTION_OUTBID: String = "APP_AUCTION_OUTBID"

    /** Takes the anti-snipe window in seconds - `AuctionPolicy.antiSnipeSeconds`. */
    const val AUCTION_ANTI_SNIPE: String = "APP_AUCTION_ANTI_SNIPE"

    /** Takes hours, then the minutes left over. */
    const val AUCTION_LEFT_HOURS: String = "APP_AUCTION_LEFT_HOURS"
    const val AUCTION_LEFT_MINUTES: String = "APP_AUCTION_LEFT_MINUTES"
    const val AUCTION_LEFT_SECONDS: String = "APP_AUCTION_LEFT_SECONDS"

    const val AUCTION_STATUS_AWAITING: String = "APP_AUCTION_STATUS_AWAITING"

    /** Takes what it went for. */
    const val AUCTION_STATUS_SOLD: String = "APP_AUCTION_STATUS_SOLD"
    const val AUCTION_STATUS_UNSOLD: String = "APP_AUCTION_STATUS_UNSOLD"
    const val AUCTION_STATUS_CANCELLED: String = "APP_AUCTION_STATUS_CANCELLED"

    const val AUCTION_BID: String = "APP_AUCTION_BID"
    const val AUCTION_BID_AMOUNT: String = "APP_AUCTION_BID_AMOUNT"

    /** Takes the least this bid may be. */
    const val AUCTION_MINIMUM: String = "APP_AUCTION_MINIMUM"

    /** The label on the total-due line. Takes the 3% the buyer pays on top of their bid. */
    const val AUCTION_BUYER_FEE: String = "APP_AUCTION_BUYER_FEE"

    const val AUCTION_SELL_CARD: String = "APP_AUCTION_SELL_CARD"

    /** Why there is nothing to consign: every copy owned is spoken for by a saved deck. */
    const val AUCTION_SELL_EMPTY: String = "APP_AUCTION_SELL_EMPTY"

    /** Takes the floor, which is the shop's own price for the card. */
    const val AUCTION_FLOOR_HINT: String = "APP_AUCTION_FLOOR_HINT"

    /** Takes the ceiling - see `AuctionRules.ceilingPriceOf` for why there is one. */
    const val AUCTION_RESERVE_HINT: String = "APP_AUCTION_RESERVE_HINT"

    const val AUCTION_DURATION: String = "APP_AUCTION_DURATION"

    /** Takes a number of hours. */
    const val AUCTION_HOURS: String = "APP_AUCTION_HOURS"

    const val AUCTION_LISTING_FEE: String = "APP_AUCTION_LISTING_FEE"
    const val AUCTION_LISTING_FEE_NOTE: String = "APP_AUCTION_LISTING_FEE_NOTE"
    const val AUCTION_OPEN_LOTS: String = "APP_AUCTION_OPEN_LOTS"
    const val AUCTION_LIST: String = "APP_AUCTION_LIST"

    const val AUCTION_WITHDRAW: String = "APP_AUCTION_WITHDRAW"
    const val AUCTION_WITHDRAW_LOCKED: String = "APP_AUCTION_WITHDRAW_LOCKED"

    /** The question put to a seller whose reserve was missed. Takes the standing bid. */
    const val AUCTION_DECIDE: String = "APP_AUCTION_DECIDE"
    const val AUCTION_ACCEPT: String = "APP_AUCTION_ACCEPT"
    const val AUCTION_DECLINE: String = "APP_AUCTION_DECLINE"

    /** Where the proceeds of a finished lot actually are: in the bag, as a pouch to open. */
    const val AUCTION_SETTLED_NOTE: String = "APP_AUCTION_SETTLED_NOTE"

    /*
     * Why the house said no, one sentence each.
     *
     * Thirteen of them rather than one "that did not work", because every one of these is a
     * different thing for the player to do next - type a bigger number, wait, close a lot, sell
     * something first. `refusalText` maps them and is exhaustive, so a new refusal on the server
     * cannot reach a screen without a sentence.
     */

    const val AUCTION_REFUSED_LOCKED: String = "APP_AUCTION_REFUSED_LOCKED"
    const val AUCTION_REFUSED_LOT_GONE: String = "APP_AUCTION_REFUSED_LOT_GONE"
    const val AUCTION_REFUSED_NOT_YOURS: String = "APP_AUCTION_REFUSED_NOT_YOURS"
    const val AUCTION_REFUSED_TOO_MANY_LOTS: String = "APP_AUCTION_REFUSED_TOO_MANY_LOTS"
    const val AUCTION_REFUSED_BELOW_FLOOR: String = "APP_AUCTION_REFUSED_BELOW_FLOOR"
    const val AUCTION_REFUSED_RESERVE_BELOW_START: String =
        "APP_AUCTION_REFUSED_RESERVE_BELOW_START"
    const val AUCTION_REFUSED_ABOVE_CEILING: String = "APP_AUCTION_REFUSED_ABOVE_CEILING"
    const val AUCTION_REFUSED_CANNOT_AFFORD: String = "APP_AUCTION_REFUSED_CANNOT_AFFORD"
    const val AUCTION_REFUSED_BID_TOO_LOW: String = "APP_AUCTION_REFUSED_BID_TOO_LOW"
    const val AUCTION_REFUSED_YOUR_OWN_LOT: String = "APP_AUCTION_REFUSED_YOUR_OWN_LOT"
    const val AUCTION_REFUSED_ALREADY_LEADING: String = "APP_AUCTION_REFUSED_ALREADY_LEADING"
    const val AUCTION_REFUSED_ALREADY_BID: String = "APP_AUCTION_REFUSED_ALREADY_BID"
    const val AUCTION_REFUSED_NOT_YOUR_DECISION: String = "APP_AUCTION_REFUSED_NOT_YOUR_DECISION"

    /** A settled lot's takings, in the bag. Takes the card that was sold. */
    const val POUCH_ITEM: String = "APP_POUCH_ITEM"

    /** What opening one paid out. Takes the sum, then the card. */
    const val POUCH_OPENED: String = "APP_POUCH_OPENED"

    /** Why a door is shut. Takes the level it opens at — see `Unlocks`. */
    const val LOCKED_LEVEL: String = "APP_LOCKED_LEVEL"

    // ---- The address on an account, and getting back in without a password ----

    const val EMAIL: String = "APP_EMAIL"

    /** Why registration asks for one. Says what it is for, and what it is not for. */
    const val EMAIL_HINT: String = "APP_EMAIL_HINT"

    const val CONFIRM_EMAIL: String = "APP_CONFIRM_EMAIL"

    /** Takes the address the code went to, so the player can see a typo in it. */
    const val CONFIRM_EMAIL_BLURB: String = "APP_CONFIRM_EMAIL_BLURB"

    const val CONFIRM_LATER: String = "APP_CONFIRM_LATER"

    /** The badge on a door shut for want of a confirmation rather than for want of levels. */
    const val CONFIRM_NEEDED: String = "APP_CONFIRM_NEEDED"

    const val EMAIL_CONFIRMED: String = "APP_EMAIL_CONFIRMED"

    const val CODE: String = "APP_CODE"
    const val CODE_SUBMIT: String = "APP_CODE_SUBMIT"
    const val CODE_RESEND: String = "APP_CODE_RESEND"

    /** Said after a resend. Deliberately about the *request*, not about a mail arriving. */
    const val CODE_SENT: String = "APP_CODE_SENT"

    const val FORGOT_PASSWORD: String = "APP_FORGOT_PASSWORD"
    const val RESET_PASSWORD: String = "APP_RESET_PASSWORD"
    const val RESET_BLURB: String = "APP_RESET_BLURB"
    const val RESET_SEND: String = "APP_RESET_SEND"

    /** *If that account exists* — the server will not say whether it does, and neither may this. */
    const val RESET_SENT: String = "APP_RESET_SENT"

    const val NEW_PASSWORD: String = "APP_NEW_PASSWORD"
    const val RESET_SUBMIT: String = "APP_RESET_SUBMIT"
    const val RESET_DONE: String = "APP_RESET_DONE"

    const val ERROR_EMAIL_TAKEN: String = "APP_ERROR_EMAIL_TAKEN"
    const val ERROR_BAD_EMAIL: String = "APP_ERROR_BAD_EMAIL"
    const val ERROR_UNVERIFIED: String = "APP_ERROR_UNVERIFIED"
    const val ERROR_BAD_CODE: String = "APP_ERROR_BAD_CODE"

    val all: List<String> = listOf(
        NEXT_MATCH, YOU_WIN, YOU_LOSE, DRAW, SUDDEN_DEATH,
        LOADING_CARDS, SIDE_BLUE, SIDE_RED, TURN_PICK_CARD, TURN_PICK_CELL,
        PLAY, SETTINGS, QUIT,
        GENERAL_SETTINGS, AUDIO_SETTINGS, LANGUAGE, BACKGROUND_VOLUME, NOISE_VOLUME,
        BACK, AUDIO_PENDING,
        STARTUP_SETTINGS, STARTUP_ART, STARTUP_OPPONENTS, STARTUP_READY,
        PROFILE, PROFILES, NEW_PROFILE, USERNAME, COLLECTION,
        LEVEL, MGP, WINS, DEFEATS, DRAWS,
        DELETE, DELETE_CONFIRM, CANCEL, START, NO_PROFILE, AVATAR,
        OPPONENTS, RULES, MATCH_FEE, REWARDS, CHALLENGE, NO_OPPONENT, RANDOM_OPPONENT,
        XP, DIFFICULTY, OPPONENT_TURN, ACHIEVEMENT_EARNED, REMATCH,
        MULTIPLAYER, CARD_LIST, CARDS, HOME, CARD_DECKS, INVENTORY, SHOP, HELP, LOGOUT,
        CARD_INFOS, TOTAL, SIDES, RARITY, CARD_TYPE, PICK_CARD, OWNED,
        DECK, DECK_POWER, RESET_DECK, SAVE, CHOOSE_DECK, RANDOM_DECK, NO_FULL_DECK,
        DECK_MISSING_CARDS, DECK_LIMITS, DECK_OVER_LIMIT,
        USE, SELL, DISCARD, BUY, CARD_SHOP, EMPTY_BAG, OBTAINED, ALREADY_OWNED, UNKNOWN_ITEM,
        ITEM_REFUSED, NOTHING_HAPPENED, ACTION_FAILED,
        STARTER_PACK, STARTER_PACK_DESC, FREE, CLAIM, ALL,
        PACK_SEALED, PACK_BREAK_SEAL, PACK_SPENT, PACK_REVEAL, PACK_TO_COLLECTION,
        PACK_ODDS, BOOSTERS,
        ACHIEVEMENTS_LIST, FORFEITS, MATCHES, WIN_RATE, BOONS, NO_ACHIEVEMENT, NEXT_TIER,
        QUESTS, QUESTS_RESET, QUEST_DONE, NO_QUEST,
        QUEST_PLAY_3, QUEST_WIN_1, QUEST_WIN_3, QUEST_BEAT, QUEST_RULE, QUEST_PVP_1,
        PVP_CHALLENGE, PVP_INVITE, PVP_INVITE_TO, PVP_FROM, PVP_SENT_TO,
        PVP_ACCEPT, PVP_DECLINE, PVP_NO_CHALLENGE,
        PVP_TABLES, PVP_NO_TABLE, PVP_HOST, PVP_HOST_OPEN, PVP_HOST_CANCEL, PVP_JOIN,
        PVP_TABLE_BY, PVP_TABLE_MINE, PVP_TABLE_FREE, PVP_TABLE_EXPIRES,
        PVP_TABLE_HEAVY, PVP_TABLE_OVER_LIMIT, PVP_JOIN_CONFIRM,
        PVP_DECK, PVP_DECK_ANY,
        PVP_STAKE, PVP_STAKE_MGP, PVP_TRADE,
        PVP_STAKE_LIMIT, PVP_STAKE_OVER_LIMIT, PVP_STAKE_OVER_PURSE,
        PVP_STAKE_MAX, PVP_STAKE_HEAVY,
        PVP_TRADE_NONE, PVP_TRADE_ONE, PVP_TRADE_DIFF, PVP_TRADE_DIRECT, PVP_TRADE_ALL,
        PVP_RULES_PICK, PVP_ROULETTE, PVP_ROULETTE_HINT, PVP_FORMAT,
        PVP_STAKE_WON, PVP_STAKE_LOST, PVP_WON_CARDS, PVP_LOST_CARDS,
        PVP_CLAIM, PVP_CLAIM_TITLE, PVP_CLAIM_PROMPT, PVP_CLAIM_CONFIRM,
        PVP_CLAIM_PENDING, PVP_CLAIM_NONE,
        PVP_ERROR_AFFORD, PVP_ERROR_STAKE, PVP_ERROR_TABLE_GONE, PVP_ERROR_RULES,
        PVP_ERROR_OWN_TABLE,
        PVP_ERROR_IN_MATCH, PVP_ERROR_NO_PLAYER, PVP_ERROR_YOURSELF, PVP_ERROR_NOTHING_OWED,
        PVP_FORFEIT, PVP_YOU_LEFT, PVP_THEY_LEFT, PVP_OVER, PVP_NO_MATCH,
        TUTORIAL, TUTORIAL_1, TUTORIAL_2, TUTORIAL_3, TUTORIAL_4, TUTORIAL_5,
        TUTORIAL_6, TUTORIAL_7, TUTORIAL_8, TUTORIAL_9,
        LESSONS, LESSONS_BLURB, LESSONS_ALL_DONE, LESSON_DONE, LESSON_NEXT,
        LESSON_COMPLETE, LESSON_TO_RULES,
        LESSON_TITLE_BASICS, LESSON_TITLE_SAME, LESSON_TITLE_PLUS, LESSON_TITLE_COMBO,
        LESSON_TITLE_SAME_WALL, LESSON_TITLE_REVERSE, LESSON_TITLE_FALLEN_ACE,
        LESSON_TITLE_REVERSE_FALLEN_ACE, LESSON_TITLE_ELEMENTAL,
        LESSON_TITLE_BONUS, LESSON_TITLE_ORDER, LESSON_TITLE_EXAM,
        LESSON_BASICS_WIN, LESSON_BASICS_LOSE, LESSON_BASICS_DRAW,
        LESSON_SAME_1, LESSON_SAME_2, LESSON_SAME_DONE,
        LESSON_PLUS_1, LESSON_PLUS_2, LESSON_PLUS_DONE,
        LESSON_COMBO_1, LESSON_COMBO_2, LESSON_COMBO_DONE,
        LESSON_SAME_WALL_1, LESSON_SAME_WALL_2, LESSON_SAME_WALL_DONE,
        LESSON_REVERSE_1, LESSON_REVERSE_2, LESSON_REVERSE_DONE,
        LESSON_FALLEN_ACE_1, LESSON_FALLEN_ACE_2, LESSON_FALLEN_ACE_DONE,
        LESSON_REVERSE_FALLEN_ACE_1, LESSON_REVERSE_FALLEN_ACE_2,
        LESSON_REVERSE_FALLEN_ACE_DONE,
        LESSON_ELEMENTAL_1, LESSON_ELEMENTAL_2, LESSON_ELEMENTAL_DONE,
        LESSON_BONUS_1, LESSON_BONUS_2, LESSON_BONUS_3, LESSON_BONUS_DONE,
        LESSON_ORDER_1, LESSON_ORDER_2, LESSON_ORDER_3, LESSON_ORDER_DONE,
        LESSON_EXAM_START, LESSON_EXAM_WIN, LESSON_EXAM_LOSE, LESSON_EXAM_DRAW,
        CAMPAIGNS, CAMPAIGN_FINAL_REWARD, CAMPAIGN_BALAMB, CAMPAIGN_STEP,
        CAMPAIGN_RESULTS, CAMPAIGN_COMPLETE, CAMPAIGN_ELIMINATED, CAMPAIGN_NOT_REACHED,
        CAMPAIGN_LOCKED,
        CAMPAIGN_ENTERED_TODAY, CAMPAIGN_NO_DECK, CAMPAIGN_ROUNDS,
        SERVERS, CONTINUE, SESSION_RESTORED, SESSION_CONNECTING, SESSION_LAPSED,
        SIGN_IN_AGAIN, SWITCH_ACCOUNT,
        SIGN_IN, CREATE_ACCOUNT, PASSWORD, ACCOUNT_BLURB,
        ACCOUNT_TO_REGISTER, ACCOUNT_TO_SIGN_IN, UPDATE_NEEDED,
        ERROR_OFFLINE, ERROR_UPDATE, ERROR_STATUS, ERROR_STALE_MATCH, ERROR_UNDEALABLE,
        ERROR_THROTTLED, ERROR_THROTTLED_IN,
        NO_SEEDS, LOADING, RETRY, CARD_FACE_DOWN, PROFILE_LOCAL_NOTE,
        ERROR_NAME_TAKEN, ERROR_BAD_CREDENTIALS, ERROR_EXPIRED,
        OPPONENTS_LOCKED, OPPONENTS_UNEARNED, REWARD_CARDS,
        OPPONENTS_NEW, OPPONENTS_WANTED, OPPONENTS_TIMED,
        HELP_FAMILY_SIGHT, HELP_FAMILY_PLAY, HELP_FAMILY_CAPTURE, HELP_FAMILY_ELEMENTS,
        SERVERS_BLURB, SERVERS_CHECK, SERVERS_CHECKING,
        SERVER_UNKNOWN, SERVER_CHECKING, SERVER_ONLINE, SERVER_DEGRADED,
        SERVER_OUTDATED, SERVER_UNREACHABLE, SERVER_UNUSABLE,
        UPDATE_REQUIRED, UPDATE_REQUIRED_BODY, UPDATE_AVAILABLE, UPDATE_AVAILABLE_BODY, UPDATE_GET,
        SELL_ALL,
        ACCOUNT_SETTINGS, DELETE_ACCOUNT, DELETE_ACCOUNT_BODY, DELETE_ACCOUNT_CONFIRM,
        MATCH_RESUME,
        TITLE_CONTINUE, TITLE_CHOOSE,
        LOBBY_RESUME, LOBBY_TODAY, LOBBY_MORE, LOBBY_SOON,
        AUCTION, AUCTION_BLURB, LOCKED_LEVEL,
        AUCTION_ROOM, AUCTION_MINE, AUCTION_SELL,
        AUCTION_EMPTY, AUCTION_MINE_EMPTY, AUCTION_FAILED, AUCTION_NEEDS_SERVER,
        AUCTION_PICK_LOT,
        AUCTION_SELLER, AUCTION_START_PRICE, AUCTION_CURRENT_PRICE, AUCTION_BID_COUNT,
        AUCTION_RESERVE, AUCTION_RESERVE_MET, AUCTION_RESERVE_NOT_MET,
        AUCTION_YOUR_BID, AUCTION_ENDS, AUCTION_ENDS_IN, AUCTION_ENDED,
        AUCTION_NO_BIDS, AUCTION_BIDS,
        AUCTION_YOUR_LOT, AUCTION_YOU_LEAD, AUCTION_OUTBID, AUCTION_ANTI_SNIPE,
        AUCTION_LEFT_HOURS, AUCTION_LEFT_MINUTES, AUCTION_LEFT_SECONDS,
        AUCTION_STATUS_AWAITING, AUCTION_STATUS_SOLD, AUCTION_STATUS_UNSOLD,
        AUCTION_STATUS_CANCELLED,
        AUCTION_BID, AUCTION_BID_AMOUNT, AUCTION_MINIMUM, AUCTION_BUYER_FEE,
        AUCTION_SELL_CARD, AUCTION_SELL_EMPTY, AUCTION_FLOOR_HINT, AUCTION_RESERVE_HINT,
        AUCTION_DURATION, AUCTION_HOURS,
        AUCTION_LISTING_FEE, AUCTION_LISTING_FEE_NOTE, AUCTION_OPEN_LOTS, AUCTION_LIST,
        AUCTION_WITHDRAW, AUCTION_WITHDRAW_LOCKED,
        AUCTION_DECIDE, AUCTION_ACCEPT, AUCTION_DECLINE, AUCTION_SETTLED_NOTE,
        AUCTION_REFUSED_LOCKED, AUCTION_REFUSED_LOT_GONE, AUCTION_REFUSED_NOT_YOURS,
        AUCTION_REFUSED_TOO_MANY_LOTS, AUCTION_REFUSED_BELOW_FLOOR,
        AUCTION_REFUSED_RESERVE_BELOW_START, AUCTION_REFUSED_ABOVE_CEILING,
        AUCTION_REFUSED_CANNOT_AFFORD, AUCTION_REFUSED_BID_TOO_LOW,
        AUCTION_REFUSED_YOUR_OWN_LOT, AUCTION_REFUSED_ALREADY_LEADING,
        AUCTION_REFUSED_ALREADY_BID, AUCTION_REFUSED_NOT_YOUR_DECISION,
        POUCH_ITEM, POUCH_OPENED,
        EMAIL, EMAIL_HINT, CONFIRM_EMAIL, CONFIRM_EMAIL_BLURB, CONFIRM_LATER,
        CONFIRM_NEEDED, EMAIL_CONFIRMED, CODE, CODE_SUBMIT, CODE_RESEND, CODE_SENT,
        FORGOT_PASSWORD, RESET_PASSWORD, RESET_BLURB, RESET_SEND, RESET_SENT,
        NEW_PASSWORD, RESET_SUBMIT, RESET_DONE,
        ERROR_EMAIL_TAKEN, ERROR_BAD_EMAIL, ERROR_UNVERIFIED, ERROR_BAD_CODE,
    )

    val appOwned: List<String> = all.filter { it.startsWith("APP_") }
}
