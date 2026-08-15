package com.tripletriad.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.SilentAudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.data.Campaign
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.FormatCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.StarterCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.rememberStrings
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.accountQueueKey
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UserSettings
import com.tripletriad.settings.UserSettingsRepository
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.Clock
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.LocalTtoColors
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.launch

/**
 * The whole app: splash while it loads, then the menu, then a match or the options.
 *
 * @param store where `UserSettings.json` lives. Supplied by the host, because `:shared` has no
 *   platform file access of its own — see `SettingsStore`. Defaults to an in-memory store so a
 *   preview or a test needs no filesystem, and so that a test can pin the language by handing in
 *   `InMemorySettingsStore("""{"language":"en_US"}""")` rather than inheriting whatever locale the
 *   machine running it happens to be set to.
 * @param documents where the `.sav` profiles live, for the same reason and from the same host. The
 *   in-memory default means a test gets a working, empty profile list rather than the machine's
 *   real saves — and that a preview cannot delete anybody's character.
 * @param clock the wall clock. Injected so a test can pin both the save timestamps and the hour
 *   that decides which opponents are available. **Defaults to a stopped clock**, not a real one:
 *   `:shared` has no `SystemClock` — see `Clock` — and a frozen 2026-01-01T12:00 is a working,
 *   obvious default for a preview or a test, the same bargain `InMemorySettingsStore` and
 *   `SilentAudioPlayer` make. Both real hosts pass one.
 * @param audio plays the sounds. Silent by default, which is also what the desktop host installs —
 *   see `AudioPlayer`.
 * @param onQuit what the Quit action does. Nothing, by default: a host that cannot express quitting
 *   (iOS) or does not want to (a preview) is a legitimate host, and the button being inert is
 *   better than `:shared` guessing.
 * @param server the connection to the account server, or null for an offline build. **Null is a
 *   supported configuration and not a degraded one**: without a server the game plays exactly as it
 *   did before accounts existed, off local `.sav` profiles, and every preview, screenshot and UI
 *   test gets that for free. With one, the character comes from the server instead and the local
 *   profile list is not reachable — see [ProfileGate]. The base URL and the HTTP engine are the
 *   host's business, as the file paths are.
 */
// `BackHandler` is still `@ExperimentalComposeUiApi` in Compose 1.9.3. Opted into here rather than
// project-wide, so the day it moves or changes shape there is exactly one call site to fix.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    store: SettingsStore = InMemorySettingsStore(),
    documents: DocumentStore = InMemoryDocumentStore(),
    clock: Clock = FixedClock(),
    audio: AudioPlayer = SilentAudioPlayer,
    onQuit: () -> Unit = {},
    server: ServerConnection? = null,
) {
    TripleTriadTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalTtoColors.current.backdrop) {
            val startup = rememberStartup(store)
            val settings = rememberSettingsHolder(store, startup.settings)
            // Before the settings file has been read there is no language to render in, so the
            // splash's first frames use the fallback bundle. It says "reading settings…" in
            // English for a few milliseconds; the alternative is a blank screen for the same few
            // milliseconds. The holder is preferred over `startup` once it exists, because from
            // then on the options screen owns the language.
            val locale = settings?.value?.locale ?: startup.settings?.locale ?: AppLocale.Default
            val strings = rememberStrings(locale)

            // The player is told the volumes rather than reading the settings itself, so
            // nothing under `AudioPlayer` knows what a `UserSettings` is. Keyed on the values so a
            // slider drag reaches the running music immediately.
            val settingsValue = settings?.value
            LaunchedEffect(audio, settingsValue?.backgroundVolume, settingsValue?.noiseVolume) {
                settingsValue?.let { audio.volumes(it.backgroundVolume, it.noiseVolume) }
            }

            val session = rememberProfileSession(documents, clock)
            val account = server?.let { rememberAccountSession(it, clock) }
            // Null without a server, which is what makes the Multiplayer card dim: playing another
            // person is the one thing here that cannot happen offline. See [PvpClient].
            val pvp = server?.let { connection ->
                rememberPvpSession(
                    client = connection.pvp,
                    // So the lobby can tell this player's own table from everybody else's.
                    hostName = account?.player?.save?.username.orEmpty(),
                    // The server owns the profile in a refereed match, so a settlement lands
                    // somewhere this client cannot see. Nothing re-read it before: `PvpOutcome`
                    // carried the payout and every field of it was ignored.
                    onSettled = { account?.refresh() },
                ) {
                    connection.session.load(connection.server.id, clock.nowMillis())?.token
                }
            }
            val connectivity = server?.let { rememberConnectivity(it) }
            var screen by remember { mutableStateOf(Screen.SPLASH) }
            val choice = remember { Choice() }

            val gate = rememberGate(session, account, startup.catalog, clock)
            val reporter = server?.reporter ?: MatchReporter.None

            StartupEffects(startup, server, account, session, pvp) {
                if (screen == Screen.SPLASH) screen = Screen.MENU
            }

            // Draining "at launch" means, in practice, when a character is in play: the queue is
            // per character, so before there is one there is nothing to drain and no key to drain
            // it under. Keyed on the key rather than on the save, because `persist` replaces the
            // save object after every match and re-running the effect then would put a network call
            // at the end of each one — which is the thing the queue exists to avoid.
            LaunchedEffect(gate.queueKey, reporter) {
                gate.queueKey?.let { reporter.drain(it) }
            }

            // Android's system back gesture, which would otherwise finish the activity mid-match
            // — the app would appear to quit from the middle of a game. `BackHandler` is
            // multiplatform (`androidx.compose.ui.backhandler`), so this needs no Android-only
            // source set; on desktop it simply never fires. Disabled on the menu so back there
            // still leaves the app, which is what a main menu should do.
            //
            // **Deprecated at Compose 1.11 for `NavigationEventHandler`, and kept anyway.** The
            // replacement is not a drop-in: it lives in a dependency this project does not have
            // (`androidx.navigationevent:navigationevent-compose`), it takes a
            // `NavigationEventState` from `rememberNavigationEventState` rather than a boolean, and
            // it `checkNotNull`s a `LocalNavigationEventDispatcherOwner` that something above it
            // has to provide. That is a navigation framework, and this app's whole navigation is
            // `Screen.up` and a `when` — see `Screen`. Adopting one to satisfy a deprecation would
            // be the tail wagging the dog. Revisit if a real navigation need appears, or when the
            // old API is removed rather than deprecated.
            @Suppress("DEPRECATION")
            BackHandler(enabled = screen != screen.up) {
                screen = screen.up
            }

            // The music belongs to the match, as in `BaseMatchScreen.as:114` — it starts when a
            // match opens and stops when it is left. Nothing plays on the splash or the menu, which
            // is also the original's behavior: `MenuScreen` never called `shuffleLoop`.
            LaunchedEffect(screen, audio) {
                val playing = screen in PLAYING_SCREENS
                if (playing) audio.play(Sound.MATCH_MUSIC) else audio.stopMusic()
            }

            // `LocalUiArt` is provided for the whole tree rather than per screen, unlike
            // `LocalCardArt`: avatars, portraits, thumbnails and bag icons are wanted on nearly
            // every screen behind the dashboard, and threading it through each would be the
            // parameter list this composition local exists to avoid.
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalAudio provides audio,
                LocalUiArt provides startup.ui,
            ) {
                // Only a hairline of padding: the board and ten cards want every dp there is.
                //
                // ### Except where the camera is
                //
                // The Android host hides the system bars and turns decor fitting off — nine tiles
                // and two hands need every dp, and the AS3 original was `fullScreen` too — so
                // nothing reserves anything and content runs to the physical edge of the glass. A
                // hidden status bar leaves no gap; a **punch-hole camera is still there**, and on
                // the phones that have one it was sitting on top of the score during a match.
                //
                // `displayCutout` and not `safeDrawing`: the bars are hidden deliberately, and
                // reserving their space would hand back the room that hiding them bought. The
                // inset is empty on desktop and on a phone without a cutout, so this costs
                // nothing where there is nothing to avoid — and it is applied once, here, rather
                // than by each of twenty-five screens.
                //
                // Inside the `Surface` rather than on it, so the backdrop still paints edge to
                // edge and the cutout sits on the game's own colour instead of on black.
                //
                // `BoxWithConstraints` so the whole tree knows whether it has a phone's width or a
                // window's — measured once, here, rather than per screen: the rail and the panes
                // have to agree, and a screen measuring itself would answer differently depending
                // on what is padding it. See [LocalWideLayout] for why this is a comparison rather
                // than a dependency on `material3-adaptive`.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(4.dp),
                ) {
                    val isWide = maxWidth >= WideLayoutThreshold

                    // A shared axis rather than a crossfade: going into a screen and coming back
                    // out of it used to look identical, so the animation carried no information.
                    // See [ScreenTransition], which also honours reduced motion.
                    ScreenTransition(screen) { destination ->
                        CompositionLocalProvider(LocalWideLayout provides isWide) {
                            Destination(
                                destination = destination,
                                pvp = pvp,
                                startup = startup,
                                settings = settings,
                                session = session,
                                account = account,
                                connectivity = connectivity,
                                gate = gate,
                                choice = choice,
                                clock = clock,
                                reporter = reporter,
                                onNavigate = { screen = it },
                                onQuit = onQuit,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The three things that have to happen once, in order, before the menu is shown.
 *
 * Separate from [App] because they are a sequence with a reason — the server, then the session on
 * it, then the local profiles if there is no session — and because [App] is otherwise the shell:
 * theme, locale, audio, back gesture. A reader asking why the splash ends when it does should not
 * have to find three effects among the volume plumbing.
 *
 * @param onReady called when every phase has completed and a stored session, if any, has been
 *   tried. A callback and not a returned flag because the destination is [App]'s decision: from
 *   then on the *user* decides where they are and startup must stop having an opinion.
 */
@Composable
@Suppress("LongParameterList")
private fun StartupEffects(
    startup: StartupState,
    server: ServerConnection?,
    account: AccountSession?,
    session: ProfileSession,
    pvp: PvpSession?,
    onReady: () -> Unit,
) {
    // The chosen server first, then the session on it — in that order and in one effect, because a
    // session is stored per server and restoring one before knowing which server we are on would
    // read the wrong key. Runs once: `restore` sets `isRestored` whatever it finds, and re-running
    // it on every recomposition would be a request per frame.
    LaunchedEffect(server, account) {
        server?.directory?.restore()
        account?.restore()
        // After the session, because the stock is keyed on the account and there is no account to
        // key it on until `restore` has run. Reads disk first and tops up only if it can, so a
        // launch with no network still has whatever the last online one left — which is the whole
        // reason the seeds are written down. See `AccountSession.loadTickets`.
        account?.loadTickets()
    }

    // Read once, when the app is ready — not on entering the profile list, so the menu can already
    // name the character and so the list is never briefly empty on arrival. Skipped entirely with a
    // server: the local profiles are not reachable then, and reading them would be a disk scan for
    // a list nothing renders.
    LaunchedEffect(startup.isReady, account) {
        if (startup.isReady && account == null) session.refresh()
    }

    // "Am I in a match, and am I owed anything?" — the first two questions this client asks the
    // server, and until now it asked neither at launch. `PvpSession.resume` has always been written
    // and has never had a caller outside its own tests, so a match survived the app being killed
    // only in the sense that the *server* remembered it: the player found out by wandering into the
    // multiplayer screen.
    //
    // It matters more now than it did. A won match can owe its winner a card, and that choice has a
    // deadline the server settles for them — so a prize nobody knows about is a prize somebody
    // else's algorithm picks. See `PvpMatchRow.CLAIM_MILLIS`.
    //
    // **After the session is restored, not before.** `resume` sets `isResumed` whatever it finds,
    // so running it against a token that has not been read yet would record "nothing to resume"
    // permanently and never look again.
    val isRestored = account?.isRestored ?: true
    LaunchedEffect(isRestored, pvp) {
        if (isRestored) pvp?.resume()
    }

    // The stored session is waited for as well, so a returning player is never shown the menu's
    // "Play" leading to a sign-in form they did not need.
    LaunchedEffect(startup.isReady, isRestored) {
        if (startup.isReady && isRestored) onReady()
    }
}

/**
 * Where the character in play comes from.
 *
 * The one place in the app that answers "account or local profile", so that everything below it —
 * the routing table, the thirteen screens, the drain — is written against [ProfileGate] and does
 * not know which. A function rather than two lines inside [App] because it is the decision, not
 * plumbing: naming it is what makes it findable.
 */
@Composable
private fun rememberGate(
    session: ProfileSession,
    account: AccountSession?,
    cards: CardCatalog?,
    clock: Clock,
): ProfileGate =
    if (account != null) {
        rememberAccountGate(account)
    } else {
        // The card table, for the prices a local profile has to work out for itself — an account
        // asks the server instead. Empty before startup has read it, which is a gate nobody can
        // spend through yet because there is no character either.
        rememberLocalGate(session, cards?.byId.orEmpty(), clock)
    }

/**
 * The account the menu should offer to resume, or null when there is nothing to offer.
 *
 * Null on an offline build — no account, nothing to remember — and null once the app has looked and
 * found no name. [AccountSession.lastUsername] is the whole test: it is written by a successful
 * sign-in, survives the token expiring, and is cleared by signing out. So the card appears exactly
 * when the app knows who the player is, whether or not it can still prove it.
 *
 * The three states are read from the session rather than stored: [AccountSession.player] is set
 * when a token was accepted, [AccountSession.isBusy] is true while the round trip is out, and what
 * is left is a name with no usable token.
 *
 * `onSwitch` navigates *before* signing out, deliberately. Signing out clears `lastUsername`, which
 * removes this card — and a player who tapped it and watched the menu quietly rearrange itself
 * would have no idea whether anything happened.
 */
@Composable
private fun rememberedAccount(
    account: AccountSession?,
    onNavigate: (Screen) -> Unit,
): RememberedAccount? {
    // Before the early return, so the scope is remembered in the same slot whether or not there is
    // an account to remember — and so this reads as one exit rather than two.
    val scope = rememberCoroutineScope()
    val username = account?.lastUsername ?: return null
    val state = when {
        account.player != null -> SessionState.RESTORED
        account.isBusy || !account.isRestored -> SessionState.CONNECTING
        else -> SessionState.LAPSED
    }

    return RememberedAccount(
        username = username,
        state = state,
        // Continue lands where Play lands, which is the point of the card: it is the shortest path
        // to the character, not a second way of signing in.
        onGo = {
            onNavigate(if (state == SessionState.RESTORED) Screen.DASHBOARD else Screen.ACCOUNT)
        },
        onSwitch = {
            onNavigate(Screen.ACCOUNT)
            scope.launch { account.signOut() }
        },
    )
}

/**
 * One screen.
 *
 * Split out of [App] so that `App` is the shell — theme, startup, locale, audio, back gesture — and
 * this is the routing table: where "Play" goes should not be found by scrolling past the volume
 * plumbing.
 *
 * @param onNavigate where a screen asks to go next. A single callback rather than one per
 *   destination: the transitions are `screen = x` and nothing else, and seven lambdas that each
 *   assign a constant would be seven places for a wrong constant to hide.
 */
@Composable
@Suppress("LongParameterList")
private fun Destination(
    destination: Screen,
    startup: StartupState,
    settings: SettingsHolder?,
    session: ProfileSession,
    account: AccountSession?,
    pvp: PvpSession?,
    connectivity: Connectivity?,
    gate: ProfileGate,
    choice: Choice,
    clock: Clock,
    reporter: MatchReporter,
    onNavigate: (Screen) -> Unit,
    onQuit: () -> Unit,
) {
    // Where "choose a character" leads. The account screen with a server, the local profile list
    // without one — the one place the two flows differ, named once so the four call sites below do
    // not each decide it again.
    val chooser = if (account != null) Screen.ACCOUNT else Screen.PROFILES

    when (destination) {
        Screen.SPLASH -> SplashScreen(startup)

        Screen.MENU -> MainMenuScreen(
            active = gate.profile,
            remembered = rememberedAccount(account, onNavigate),
            connectivity = connectivity,
            // Play goes straight to the dashboard when a character is loaded and to the chooser
            // when none is — the original's Continue and Load Game behind one button, chosen by
            // what is actually loaded rather than by asking which of the two the player meant.
            onPlay = { onNavigate(if (gate.profile == null) chooser else Screen.DASHBOARD) },
            onProfiles = { onNavigate(chooser) },
            onServers = { onNavigate(Screen.SERVERS) },
            onOptions = { onNavigate(Screen.OPTIONS) },
            onQuit = onQuit,
        )

        // The two screens that exist only on a build with a server. Grouped so the routing table
        // has one arm for "the account flow" rather than two that each re-derive whether there is
        // an account to have a flow about.
        Screen.ACCOUNT, Screen.SERVERS -> AccountDestination(
            destination = destination,
            account = account,
            connectivity = connectivity,
            onNavigate = onNavigate,
        )

        Screen.PROFILES -> ProfileListScreen(
            session = session,
            // A deleted profile takes its unjudged matches with it. Not merely tidiness: keys are
            // derived from the username and creation date, so a profile created again with the same
            // name on the same day would inherit the old one's queue and submit somebody else's
            // matches under its own progression.
            onDeleted = { reporter.forget(it) },
            onSelected = { onNavigate(Screen.DASHBOARD) },
            onNew = { onNavigate(Screen.PROFILE_NEW) },
            onBack = { onNavigate(Screen.MENU) },
        )

        Screen.PROFILE_NEW -> ProfileCreateScreen(
            session = session,
            // Behind the splash, so the empty fallback is unreachable — see `CharacterDestination`.
            starters = startup.starters ?: StarterCatalog(emptyList()),
            onCreated = { onNavigate(Screen.DASHBOARD) },
            onBack = { onNavigate(Screen.PROFILES) },
        )

        Screen.OPTIONS -> settings?.let {
            OptionsScreen(
                settings = it,
                onBack = { onNavigate(Screen.MENU) },
                // Null in local-profile mode, which hides the account group — see `OptionsScreen`.
                // The session clears itself on success, so there is nothing to sign out of here.
                account = account,
                // The same destination `onLogout` goes to, and for a stronger version of its
                // reason: every screen behind this one is about a character that no longer exists.
                onDeleted = { onNavigate(chooser) },
            )
        }

        // Everything behind the dashboard needs a character, and a missing one is a state the flow
        // cannot reach: the dashboard is only entered from the list or from creation, both of which
        // select one. Rendering nothing is the honest answer, rather than half a screen or a
        // placeholder claiming something is wrong.
        //
        // Grouped rather than delegated behind an `else`, so that adding a fourteenth screen is a
        // compile error here instead of a destination that silently renders blank.
        Screen.DASHBOARD, Screen.OPPONENTS, Screen.MATCH, Screen.TUTORIAL, Screen.STATS,
        Screen.QUESTS, Screen.PVP, Screen.PVP_MATCH, Screen.PVP_TABLE, Screen.PVP_CLAIM,
        Screen.CAMPAIGN, Screen.CAMPAIGN_MATCH, Screen.AVATAR, Screen.COLLECTION_CHOICE,
        Screen.CARDS, Screen.DECKS, Screen.INVENTORY, Screen.SHOP, Screen.HELP,
        -> gate.profile?.let { profile ->
            // The navigation bar, for the screens that have one. Provided here rather than passed
            // down because eleven screens would otherwise carry two parameters that four of them
            // never read — see [Navigation]. **Null on a match**, which is what keeps the board
            // immersive: `destination.tab` answers null there, so no bar is drawn and none of the
            // three match screens has to know a bar exists.
            val navigation = destination.tab?.let { tab ->
                Navigation(current = tab) { onNavigate(it.root) }
            }

            CompositionLocalProvider(LocalNavigation provides navigation) {
                CharacterDestination(
                    destination = destination,
                    profile = profile,
                    pvp = pvp,
                    startup = startup,
                    gate = gate,
                    account = account,
                    chooser = chooser,
                    choice = choice,
                    clock = clock,
                    reporter = reporter,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

/**
 * The sign-in form and the server list.
 *
 * Both need an [AccountSession] and a [Connectivity], and both come from the same `server`, so
 * neither can be present without the other — the `?.let` pair is Kotlin's requirement rather than a
 * state the app can reach. Rendering nothing if it ever were is the honest answer.
 */
@Composable
private fun AccountDestination(
    destination: Screen,
    account: AccountSession?,
    connectivity: Connectivity?,
    onNavigate: (Screen) -> Unit,
) {
    val session = account ?: return
    val state = connectivity ?: return

    when (destination) {
        Screen.SERVERS -> ServersScreen(
            connectivity = state,
            // The switch first, then the fresh reading. Probing after rather than before means the
            // row the player just chose shows what it is *now*, on a connection that has already
            // been through a sign-out and a restore.
            onSelect = { entry -> if (session.useServer(entry)) state.refreshAll() },
            onBack = { onNavigate(Screen.MENU) },
        )

        else -> AccountScreen(
            session = session,
            update = state.update,
            // A new account goes through the collection step first — the one moment it can be
            // asked, since registration does not carry one and no match has been played yet.
            onSignedIn = { isNew ->
                onNavigate(if (isNew) Screen.COLLECTION_CHOICE else Screen.DASHBOARD)
            },
            onBack = { onNavigate(Screen.MENU) },
        )
    }
}

/**
 * One of the nine screens behind the dashboard.
 *
 * Split from [Destination] because they share a prerequisite — a loaded character — and checking it
 * once is what keeps the eight call sites from each writing their own `?.let`. It is also what
 * keeps either function under the complexity detekt rejects: the two together are the routing table
 * the original spread across a `gotoScreen` string switch in `Game.as`.
 *
 * The card catalog is the second prerequisite and is *not* hoisted the same way: the dashboard, the
 * statistics and the help screen do not need it, and gating them on it would leave them blank while
 * `cards.json` loads.
 */
@Composable
@Suppress("LongParameterList")
private fun CharacterDestination(
    destination: Screen,
    profile: GameSave,
    pvp: PvpSession?,
    startup: StartupState,
    gate: ProfileGate,
    account: AccountSession?,
    chooser: Screen,
    choice: Choice,
    clock: Clock,
    reporter: MatchReporter,
    onNavigate: (Screen) -> Unit,
) {
    val toDashboard = { onNavigate(Screen.DASHBOARD) }
    val scope = rememberCoroutineScope()
    // Loaded in the same startup phase as the card table, and this whole function is behind the
    // splash — so the empty fallback is unreachable rather than a degraded mode. It is here so the
    // two destinations that need it are not each a null check, which is what took this `when` past
    // the complexity detekt allows.
    val starters = startup.starters ?: StarterCatalog(emptyList())

    when (destination) {
        Screen.DASHBOARD -> DashboardScreen(
            profile = profile,
            // For the quest badge, and read here rather than inside the screen so the dashboard
            // and [QuestsScreen] cannot disagree about what day it is.
            at = clock.nowMillis(),
            onPlay = { onNavigate(Screen.OPPONENTS) },
            onStats = { onNavigate(Screen.STATS) },
            onQuests = { onNavigate(Screen.QUESTS) },
            onPvp = pvp?.let { { onNavigate(Screen.PVP) } },
            pvpBadge = pvpBadge(pvp),
            // The collection and the shelf are the navigation bar's own two entries and are not
            // repeated here; these two open the *other* tab of each — see [DashboardScreen].
            onDecks = { onNavigate(Screen.DECKS) },
            onInventory = { onNavigate(Screen.INVENTORY) },
            onHelp = { onNavigate(Screen.HELP) },
            // With a server, Logout means *sign out*: the token is dropped and the session ended,
            // not merely the screen changed. That is the distinction the original never made — its
            // Logout navigated away and left `Game.PROFILE_DATAS` loaded — and here it matters,
            // because leaving the token behind on a shared device would leave the account behind.
            onLogout = {
                if (account != null) scope.launch { account.signOut() }
                onNavigate(chooser)
            },
        )

        Screen.OPPONENTS -> startup.opponents?.let { opponents ->
            // The format ordinary matches are played in: the widest one, which with `MODE` gone
            // is every released block. When a player picks a format this becomes their choice.
            val formatId = startup.formats?.default?.id ?: return@let

            OpponentScreen(
                profile = profile,
                catalog = opponents,
                formatId = formatId,
                // The card table too, since a row now draws the cards its opponent can drop. An
                // empty map before the catalog has loaded, which costs the drop lines for the one
                // frame that state can reach this screen — the startup gate makes it unreachable
                // in practice, and an opponent list is worth more than a spinner in front of it.
                cards = startup.catalog?.all?.associateBy { it.id }.orEmpty(),
                hour = clock.localHour(),
                onChallenge = {
                    choice.opponent = it
                    onNavigate(Screen.MATCH)
                },
                onTutorial = { onNavigate(Screen.TUTORIAL) },
                // **Every** ladder, not the ones playing this format. A ladder *is* a format plus
                // a list of opponents — the FFXIV Cup is played with FFXIV cards under the FFXIV
                // pool — so filtering them by the format the free matches use would hide all of
                // them, which is exactly what happened when that format became the union. Entering
                // a ladder switches to the ladder's own format; see [CampaignDestination].
                campaigns = startup.campaigns?.all.orEmpty(),
                onCampaign = {
                    choice.campaign = it
                    onNavigate(Screen.CAMPAIGN)
                },
                onBack = toDashboard,
            )
        }

        // Everything that *is* a match: the ordinary one, the tutorial, and a ladder step. Grouped
        // because they are one subject — a board with a scripted or chosen opponent behind it — and
        // because the three arms together were what pushed this function past the complexity
        // detekt allows.
        Screen.MATCH, Screen.TUTORIAL, Screen.CAMPAIGN, Screen.CAMPAIGN_MATCH,
        Screen.PVP_MATCH, Screen.PVP_CLAIM,
        -> MatchDestinations(
            destination = destination,
            profile = profile,
            startup = startup,
            choice = choice,
            gate = gate,
            account = account,
            pvp = pvp,
            clock = clock,
            reporter = reporter,
            onNavigate = onNavigate,
        )

        Screen.STATS, Screen.QUESTS, Screen.AVATAR, Screen.COLLECTION_CHOICE -> RecordDestination(
            destination = destination,
            profile = profile,
            starters = starters,
            at = clock.nowMillis(),
            opponents = startup.opponents,
            formatId = startup.formats?.default?.id,
            gate = gate,
            onNavigate = onNavigate,
        )

        // The two that need nothing but the profile: the rules, and finding somebody to play. Both
        // are one call each, and pairing them keeps this `when` inside the complexity gate.
        Screen.HELP, Screen.PVP, Screen.PVP_TABLE -> SocialDestination(
            destination = destination,
            profile = profile,
            pvp = pvp,
            formats = startup.formats,
            clock = clock,
            invitee = choice.invitee,
            onMatch = { onNavigate(Screen.PVP_MATCH) },
            onHost = {
                choice.invitee = null
                onNavigate(Screen.PVP_TABLE)
            },
            onInvite = { name ->
                choice.invitee = name
                onNavigate(Screen.PVP_TABLE)
            },
            onClaim = { onNavigate(Screen.PVP_CLAIM) },
            onBack = toDashboard,
        )

        // The four that browse the card table. Grouped for the same reason the character-bearing
        // screens are grouped one level up: they share a prerequisite, and checking it four times
        // is four places for one of them to forget.
        Screen.CARDS, Screen.DECKS, Screen.INVENTORY, Screen.SHOP,
        -> startup.catalog?.let { catalog ->
            CompositionLocalProvider(LocalCardArt provides startup.art) {
                CollectionDestination(
                    destination = destination,
                    profile = profile,
                    catalog = catalog,
                    starters = starters,
                    startup = startup,
                    // The whole gate rather than `gate.persist`: the bag needs a second thing from
                    // it, and threading them one at a time is how a screen ends up knowing which
                    // source is live — the exact thing `ProfileGate` exists to hide.
                    gate = gate,
                    onBack = toDashboard,
                )
            }
        }

        // The seven screens ahead of a loaded character. [Destination] routes those itself and
        // never calls this with one; the branch exists because Kotlin requires a complete `when`.
        Screen.SPLASH, Screen.MENU, Screen.PROFILES, Screen.PROFILE_NEW,
        Screen.ACCOUNT, Screen.SERVERS, Screen.OPTIONS,
        -> Unit
    }
}

/**
 * The rules, and the lobby.
 *
 * An odd couple on the face of it, and a real one underneath: both need nothing but the character,
 * both return to the dashboard, and neither reads the card table. Together they are one arm of
 * [CharacterDestination] instead of two, which is what keeps it under the complexity gate.
 */
@Composable
@Suppress("LongParameterList")
private fun SocialDestination(
    destination: Screen,
    profile: GameSave,
    pvp: PvpSession?,
    formats: FormatCatalog?,
    clock: Clock,
    invitee: String?,
    onMatch: () -> Unit,
    onHost: () -> Unit,
    onInvite: (String) -> Unit,
    onClaim: () -> Unit,
    onBack: () -> Unit,
) {
    when (destination) {
        // Null without a server, and unreachable then: the dashboard card is dim, so this is the
        // belt to that braces rather than a state the flow can produce.
        Screen.PVP -> pvp?.let { session ->
            PvpScreen(
                profile = profile,
                session = session,
                now = clock.nowMillis(),
                onMatch = onMatch,
                onHost = onHost,
                onInvite = onInvite,
                onClaim = onClaim,
                onBack = onBack,
            )
        }

        Screen.PVP_TABLE -> pvp?.let { session ->
            // The whole catalogue, not one format: `PvpReferee.open` used to assume everybody
            // played `FormatCatalog.default` because nobody could say otherwise, which left two
            // authored formats unreachable. The host chooses now.
            formats?.let { catalogue ->
                PvpTableScreen(
                    profile = profile,
                    formats = catalogue,
                    session = session,
                    invitee = invitee,
                    onOpened = onBack,
                    onBack = onBack,
                )
            }
        }

        else -> HelpScreen(profile = profile, onBack = onBack)
    }
}

/**
 * The four screens that are a board.
 *
 * A pass-through, and worth its existence for two reasons: it names the group — an ordinary match,
 * the tutorial, a ladder step and a match against a person are one board with different opponents
 * behind it — and it keeps
 * [CharacterDestination] under the complexity gate, which is the same trade [RecordDestination]
 * makes one function below.
 */
@Composable
@Suppress("LongParameterList")
private fun MatchDestinations(
    destination: Screen,
    profile: GameSave,
    startup: StartupState,
    choice: Choice,
    gate: ProfileGate,
    account: AccountSession?,
    pvp: PvpSession?,
    clock: Clock,
    reporter: MatchReporter,
    onNavigate: (Screen) -> Unit,
) {
    when (destination) {
        // The fourth kind of board, and the only one this client does not run itself. `MatchArt`
        // wraps it for the same reason it wraps the other three: without `LocalCardArt` every
        // `CardFace` is a bare colour quad — no picture, no digits, no stars — because a null
        // bitmap leaves its layer empty by design. See `CardView.Layer`.
        Screen.PVP_MATCH -> pvp?.let { session ->
            MatchArt(startup) {
                PvpMatchScreen(
                    session = session,
                    // The whole table rather than the mode's: a PvP opponent plays their own
                    // collection, and while `MODE` still exists the two could differ.
                    cards = startup.catalog?.all?.associateBy { it.id }.orEmpty(),
                    now = clock.nowMillis(),
                    onExit = {
                        // Straight to the prize when one is owed. The alternative is a lobby with
                        // a banner on it, which is one more tap between winning a card and being
                        // given one by a deadline.
                        val owed = (session.match?.outcome?.picksOwed ?: 0) > 0
                        onNavigate(if (owed) Screen.PVP_CLAIM else Screen.PVP)
                    },
                )
            }
        }

        Screen.PVP_CLAIM -> pvp?.let { session ->
            MatchArt(startup) {
                PvpClaimScreen(
                    session = session,
                    cards = startup.catalog?.all?.associateBy { it.id }.orEmpty(),
                    now = clock.nowMillis(),
                    onDone = { onNavigate(Screen.PVP) },
                )
            }
        }

        Screen.CAMPAIGN, Screen.CAMPAIGN_MATCH -> CampaignDestination(
            destination = destination,
            campaign = choice.campaign,
            profile = profile,
            startup = startup,
            clock = clock,
            nextSeed = gate.nextSeed,
            onPersist = gate.persist,
            onIntent = gate.perform,
            onNavigate = onNavigate,
        )

        Screen.TUTORIAL -> TutorialDestination(
            profile = profile,
            startup = startup,
            clock = clock,
            nextSeed = gate.nextSeed,
            onPersist = gate.persist,
            onNavigate = onNavigate,
        )

        else -> MatchDestination(
            profile = profile,
            startup = startup,
            opponent = choice.opponent,
            clock = clock,
            nextSeed = gate.nextSeed,
            onPersist = gate.persist,
            // The key is derived from the profile the *match* was played with, not from the gate,
            // whose profile the credit has already replaced by the time this runs. Both name the
            // same queue — neither key is built from anything a match changes — and using the one
            // in hand says so rather than relying on it.
            onTranscript = { reporter.report(queueKeyFor(profile, account), it) },
            onExit = { onNavigate(Screen.OPPONENTS) },
        )
    }
}

/**
 * The record, the day's quests, and the two screens that edit the character they describe.
 *
 * Grouped because they are one subject — who this character *is* and how they are doing, as opposed
 * to what they own or who they play — and because the arms together were what pushed
 * [CharacterDestination] past the complexity detekt allows.
 *
 * Both edits go through [ProfileGate.persist], so neither knows whether it is writing a local
 * `.sav` or an account the server holds. The quests screen writes nothing at all: a match credits
 * them, and this only reads.
 *
 * @param at the instant the quest day is read from, passed down rather than read here so the
 *   dashboard's badge and the screen cannot disagree about what day it is.
 * @param opponents only to name the opponent a `BeatOpponent` quest asks for. Behind the splash, so
 *   null is unreachable rather than a degraded mode; the label falls back to the icon id.
 */
@Composable
@Suppress("LongParameterList")
private fun RecordDestination(
    destination: Screen,
    profile: GameSave,
    starters: StarterCatalog,
    at: Long,
    opponents: NpcCatalog?,
    formatId: String?,
    gate: ProfileGate,
    onNavigate: (Screen) -> Unit,
) {
    when (destination) {
        Screen.AVATAR -> AvatarScreen(
            profile = profile,
            onChoose = gate.persist,
            onBack = { onNavigate(Screen.STATS) },
        )

        Screen.QUESTS -> QuestsScreen(
            profile = profile,
            at = at,
            opponents = opponents,
            // Only to name the opponent a `BeatOpponent` quest asks for, so an unresolvable format
            // costs the name and not the screen — the label falls back to the icon id.
            formatId = formatId.orEmpty(),
            onBack = { onNavigate(Screen.DASHBOARD) },
        )

        Screen.COLLECTION_CHOICE -> StarterChoiceScreen(
            profile = profile,
            starters = starters,
            onChosen = { chosen ->
                gate.persist(chosen)
                onNavigate(Screen.DASHBOARD)
            },
            onBack = { onNavigate(Screen.DASHBOARD) },
        )

        else -> StatsScreen(
            profile = profile,
            onAvatar = { onNavigate(Screen.AVATAR) },
            onBack = { onNavigate(Screen.DASHBOARD) },
        )
    }
}

/**
 * An ordinary PvE match.
 *
 * Its own function for the same reason the two scripted ones are: a `?.let` on the chosen opponent
 * nested inside one on the card table is what pushed [CharacterDestination] past the complexity
 * detekt allows, and the three match screens read better side by side than as three arms of a
 * `when`.
 */
@Composable
@Suppress("LongParameterList")
private fun MatchDestination(
    profile: GameSave,
    startup: StartupState,
    opponent: Npc?,
    clock: Clock,
    nextSeed: () -> Int?,
    onPersist: suspend (GameSave) -> Unit,
    onTranscript: suspend (MatchTranscript) -> Unit,
    onExit: () -> Unit,
) {
    val chosen = opponent ?: return
    val catalog = startup.catalog ?: return
    // The format this match is played under: the widest one, the same the roster was listed in.
    // Behind the splash, so a missing catalogue is unreachable rather than a degraded mode.
    val format = startup.formats?.default ?: return

    MatchArt(startup) {
        MatchScreen(
            catalog = catalog,
            profile = profile,
            npc = chosen,
            format = format,
            clock = clock,
            nextSeed = nextSeed,
            onPersist = onPersist,
            onExit = onExit,
            onTranscript = onTranscript,
        )
    }
}

/**
 * A ladder: its entry screen, then its rungs.
 *
 * Both destinations in one function because they share the ladder that neither has without the
 * other — and because they are one screen in the original too, `CCGroupScreen` dispatching straight
 * into `CCGroupMatchScreen`.
 *
 * The transcript is not reported from here. Every rung is a [MatchScript], and [MatchScreen]
 * refuses to submit a scripted match — the script changes the deal and the opening, neither of
 * which the seed carries, so a server could not replay it.
 */
@Composable
@Suppress("LongParameterList")
private fun CampaignDestination(
    destination: Screen,
    campaign: Campaign?,
    profile: GameSave,
    startup: StartupState,
    clock: Clock,
    nextSeed: () -> Int?,
    onPersist: suspend (GameSave) -> Unit,
    onIntent: suspend (Intent) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val ladder = campaign ?: return
    val scope = rememberCoroutineScope()
    val toOpponents = { onNavigate(Screen.OPPONENTS) }

    if (destination == Screen.CAMPAIGN) {
        CampaignScreen(
            campaign = ladder,
            profile = profile,
            // The fee is taken here, on the way in, and never given back: `startCampaign`'s
            // handler does `Game.PROFILE_DATAS.MGP -= 500` and then opens the ladder. A defeat
            // costs another 500 to try again, which is the whole of what makes a ladder a stake.
            //
            // Charged by whoever holds the profile rather than deducted here — the amount is the
            // server's, and a client that applied its own could apply none. See
            // `EnterCampaignRequest`.
            onStart = {
                scope.launch { onIntent(Intent.EnterCampaign(ladder.key, ladder.fee)) }
                onNavigate(Screen.CAMPAIGN_MATCH)
            },
            onBack = toOpponents,
        )
    } else {
        val catalog = startup.catalog
        // **The ladder's own** format, and not the one free matches use. A ladder names the format
        // its rungs are played under — that is what `Campaign.format` is for — so the FFXIV Cup is
        // FFXIV cards under the FFXIV pool however wide the free-play format happens to be.
        val format = startup.formats?.get(ladder.format)
        if (catalog != null && format != null) {
            MatchArt(startup) {
                CampaignMatchScreen(
                    campaign = ladder,
                    catalog = catalog,
                    profile = profile,
                    format = format,
                    clock = clock,
                    nextSeed = nextSeed,
                    onPersist = onPersist,
                    onFinished = toOpponents,
                )
            }
        }
    }
}

/**
 * The two composition locals every match screen wants.
 *
 * [LocalCardArt] is provided even when null: a card composes correctly with no textures at all —
 * flat colour quad, empty layers — so a failed art load costs appearance, not playability.
 *
 * [LocalBannerArt] is built here rather than in [rememberStartup] because it loads nothing until a
 * caption is asked for, and because it is keyed on the language — a rule caption is a picture of a
 * word. The locale comes from [LocalStrings] rather than from the settings holder, so the captions
 * cannot disagree with the text on screen: both read the same value. See `BannerArt`.
 */
@Composable
private fun MatchArt(startup: StartupState, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCardArt provides startup.art,
        LocalBannerArt provides rememberBannerArt(LocalStrings.current.locale),
        content = content,
    )
}

/**
 * What the player has picked to play: an opponent, or a ladder.
 *
 * One holder rather than two `remember`ed values threaded through [Destination] and
 * [CharacterDestination] as four parameters. Neither field is cleared on the way out: the next
 * choice overwrites it, and the screen that reads one is only reachable by having just made it.
 */
@Stable
internal class Choice {
    var opponent: Npc? by mutableStateOf(null)
    var campaign: Campaign? by mutableStateOf(null)

    /**
     * Who an invitation is being written for, or null when the terms screen is opening a table.
     *
     * Here rather than as a `Screen` parameter for the reason this class exists at all: the
     * navigation is an enum and a `when`, so a screen that needs an argument needs somewhere to
     * leave it. Not cleared on the way out — the next choice overwrites it, and the screen that
     * reads it is only reachable by having just made one.
     */
    var invitee: String? by mutableStateOf(null)
}

/**
 * What is waiting behind the Multiplayer card, or null when nothing is.
 *
 * Read from the session rather than fetched: `StartupEffects` has already asked at launch, and this
 * is the one place a player who is not thinking about multiplayer will still be told it wants them.
 * That matters because an uncollected prize has a deadline the server settles for them.
 */
private fun pvpBadge(pvp: PvpSession?): String? = when {
    pvp == null -> null
    pvp.claims.isNotEmpty() -> "${pvp.claims.size}"
    pvp.match != null -> DOT_SEPARATOR
    else -> null
}

/** Where the match music plays — `BaseMatchScreen`, and every screen that is one. */
private val PLAYING_SCREENS =
    setOf(Screen.MATCH, Screen.TUTORIAL, Screen.CAMPAIGN_MATCH, Screen.PVP_MATCH)

/**
 * The lesson, which needs three things at once: the card table, the opponent table, and an opponent
 * in it to teach.
 *
 * Its own function rather than another arm of [CharacterDestination]'s `when`, because three nested
 * `?.let` on top of that one pushed it past both the complexity and the nesting depth detekt
 * allows — and because the lesson **picks its own opponent**. It deliberately does not go through
 * [Choice]: nothing here may leave the tutor sitting in the chosen-opponent slot, where the next
 * ordinary match would find it.
 */
@Composable
private fun TutorialDestination(
    profile: GameSave,
    startup: StartupState,
    clock: Clock,
    nextSeed: () -> Int?,
    onPersist: suspend (GameSave) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val catalog = startup.catalog ?: return
    val format = startup.formats?.default ?: return
    val tutor = startup.opponents?.let { tutorFor(it, format.id) } ?: return

    MatchArt(startup) {
        TutorialScreen(
            catalog = catalog,
            profile = profile,
            tutor = tutor,
            format = format,
            clock = clock,
            nextSeed = nextSeed,
            onPersist = onPersist,
            onHelp = { onNavigate(Screen.HELP) },
            onExit = { onNavigate(Screen.OPPONENTS) },
        )
    }
}

/**
 * Which queue a finished match belongs in.
 *
 * The two sources key it differently and both are stable across a match — the local one by name and
 * creation date, the account one by the server and the account name — so this is a choice of
 * *which* stable key, not a computation. Written as a function rather than read off the gate so the
 * call site can pass the profile the match was actually played with; see its comment.
 */
private fun queueKeyFor(
    profile: GameSave,
    account: AccountSession?,
): String =
    if (account != null) {
        accountQueueKey(account.serverId, profile.username)
    } else {
        SaveRepository.keyFor(profile)
    }

/**
 * The four destinations that read the card table — which are **two screens**.
 *
 * [Screen.CARDS] and [Screen.DECKS] are the two tabs of [CollectionScreen]; [Screen.SHOP] and
 * [Screen.INVENTORY] are the two tabs of [StoreScreen]. The enum still has four entries because
 * four things are still openable by name, and a caller that wants the bag should be able to say so
 * — what changed is that arriving at one of a pair now puts the other one tab away instead of two
 * screens and a dashboard away. See [CollectionScreen] for why these four were the ones to pair.
 */
@Composable
@Suppress("LongParameterList")
private fun CollectionDestination(
    destination: Screen,
    profile: GameSave,
    catalog: CardCatalog,
    starters: StarterCatalog,
    startup: StartupState,
    gate: ProfileGate,
    onBack: () -> Unit,
) {
    // The shelf is a property of the format, not of the character — see `ShopCatalog.offers`.
    // Resolved here rather than one level up so the browse arm stays a single `?.let`.
    val format = startup.formats?.default ?: return

    when (destination) {
        Screen.CARDS, Screen.DECKS -> CollectionScreen(
            profile = profile,
            catalog = catalog,
            format = format,
            initial = if (destination == Screen.DECKS) {
                CollectionTab.DECKS
            } else {
                CollectionTab.CARDS
            },
            onPersist = gate.persist,
            onIntent = gate.perform,
            onBack = onBack,
        )

        Screen.SHOP, Screen.INVENTORY -> StoreScreen(
            profile = profile,
            catalog = catalog,
            starters = starters,
            format = format,
            initial = if (destination == Screen.INVENTORY) StoreTab.BAG else StoreTab.SHOP,
            // No `onPersist`: every write this screen makes moves something of value, so all of
            // them are intents now — the shop, the bag, and the starter box.
            onUseItem = gate.useItem,
            onIntent = gate.perform,
            onBack = onBack,
        )

        else -> Unit
    }
}

/**
 * Wraps the loaded settings in something the options screen can mutate.
 *
 * Null until startup has read the file. Keyed on nothing but the store, so the holder — and any
 * change the user has since made — survives recomposition; `initial` seeds it once.
 */
@Composable
private fun rememberSettingsHolder(
    store: SettingsStore,
    initial: UserSettings?,
): SettingsHolder? {
    val scope = rememberCoroutineScope()
    val repository = remember(store) { UserSettingsRepository(store) }
    var holder by remember(store) { mutableStateOf<SettingsHolder?>(null) }
    LaunchedEffect(store, initial) {
        if (holder == null && initial != null) {
            holder = SettingsHolder(initial) { updated ->
                scope.launch { repository.save(updated) }
            }
        }
    }
    return holder
}
