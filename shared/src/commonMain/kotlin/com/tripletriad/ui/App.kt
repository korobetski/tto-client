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
import com.tripletriad.data.PveMatches
import com.tripletriad.data.StarterCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.rememberStrings
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.model.questDayOf
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.ServerConnection
import com.tripletriad.protocol.ANY_DECK
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    // The pace the game ships at, unless a test asks for less of it. See [Pacing]: at the default
    // factor every scaled duration is the number its constant says, so this parameter changes
    // nothing for anyone but `:shared:desktopTest`.
    pacing: Pacing = Pacing.Default,
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
            /*
             * The opponent, refereed. Null without a server — and that is now what "offline" means
             * for the whole game rather than for multiplayer alone: a solo match is a row on the
             * server too, because the client that used to run it also held the opponent's five
             * cards and every move they were going to make. See [PveMatchScreen].
             *
             * The tutorial is the exception and stays local, because it settles nothing.
             */
            val pve = server?.let { connection ->
                rememberPveSession(
                    client = connection.pve,
                    // The profile after crediting, computed once on the server. Adopted rather
                    // than added up: two copies of a profile and a window in which they disagree
                    // is what a reward that never reaches the bag looks like from the inside.
                    onCredited = { account?.adopt(it) },
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

            HostedTableWatch(
                pvp = pvp,
                clock = clock,
                screen = screen,
                onOpened = { screen = Screen.PVP_MATCH },
            )

            MatchSettlement(
                reporter = reporter,
                account = account,
                queueKey = gate.queueKey,
                screen = screen,
            )

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
                LocalPacing provides pacing,
            ) {
                // Only a hairline of padding: the board and ten cards want every dp there is.
                //
                // ### Except where the camera is
                //
                // The Android host hides the system bars and turns decor fitting off — nine tiles
                // and two hands need every dp — so nothing reserves anything and content runs to
                // the physical edge of the glass. A
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
                                pve = pve,
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

@Composable
private fun HostedTableWatch(
    pvp: PvpSession?,
    clock: Clock,
    screen: Screen,
    onOpened: () -> Unit,
) {
    if (pvp == null) return
    val table = pvp.myTable
    var announced by remember(pvp) { mutableStateOf<String?>(null) }

    LaunchedEffect(pvp, table?.id) {
        val offer = table ?: return@LaunchedEffect
        while (pvp.match == null && clock.nowMillis() < offer.expiresAt) {
            delay(TABLE_POLL_MILLIS)
            pvp.poll()
        }
    }

    LaunchedEffect(pvp.match?.matchId, screen) {
        val id = pvp.match?.matchId ?: return@LaunchedEffect
        if (id == announced || screen in PLAYING_SCREENS) return@LaunchedEffect
        announced = id
        onOpened()
    }
}

private const val TABLE_POLL_MILLIS = 1_000L

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

@Composable
@Suppress("LongParameterList")
private fun Destination(
    destination: Screen,
    startup: StartupState,
    settings: SettingsHolder?,
    session: ProfileSession,
    account: AccountSession?,
    pvp: PvpSession?,
    pve: PveSession?,
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
        Screen.LESSONS,
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
                    pve = pve,
                    startup = startup,
                    gate = gate,
                    account = account,
                    chooser = chooser,
                    choice = choice,
                    clock = clock,
                    settings = settings,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

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

@Composable
@Suppress("LongParameterList")
private fun CharacterDestination(
    destination: Screen,
    profile: GameSave,
    pvp: PvpSession?,
    pve: PveSession?,
    startup: StartupState,
    gate: ProfileGate,
    account: AccountSession?,
    chooser: Screen,
    choice: Choice,
    clock: Clock,
    settings: SettingsHolder?,
    onNavigate: (Screen) -> Unit,
) {
    val toDashboard = { onNavigate(Screen.DASHBOARD) }
    // Loaded in the same startup phase as the card table, and this whole function is behind the
    // splash — so the empty fallback is unreachable rather than a degraded mode. It is here so the
    // two destinations that need it are not each a null check, which is what took this `when` past
    // the complexity detekt allows.
    val starters = startup.starters ?: StarterCatalog(emptyList())

    when (destination) {
        // The hub and the course, one arm rather than two for the reason [SocialDestination]
        // pairs its own: they read the same progress counter, and splitting them put this `when`
        // back over the complexity gate.
        Screen.DASHBOARD, Screen.LESSONS -> ProgressDestination(
            destination = destination,
            profile = profile,
            pvp = pvp,
            account = account,
            chooser = chooser,
            choice = choice,
            clock = clock,
            settings = settings,
            onNavigate = onNavigate,
        )

        Screen.OPPONENTS -> startup.opponents?.let { opponents ->
            // The format ordinary matches are played in: the widest one, which with `MODE` gone
            // is every released block. When a player picks a format this becomes their choice.
            val formatId = startup.formats?.default?.id ?: return@let

            /*
             * What the player is in the middle of, asked on the way in.
             *
             * Nothing else asks. [MatchDestination] resumes `against` one named opponent, which
             * answers "am I already playing *them*" and is deliberately blind to a match against
             * anybody else — so a match interrupted by a closed app was only ever findable by
             * walking back to the same opponent's board and hoping to pick the right row. This is
             * the question with no opponent in it, and `PveSession.resume` already takes null for
             * exactly that.
             *
             * Keyed on the session, so it runs once each time the roster is opened rather than on
             * every recomposition: the answer can change while the player is away from this
             * screen, and cannot change while they are on it.
             */
            LaunchedEffect(pve) { pve?.resume() }

            // **A live match, not a recent one.** `resume(null)` also answers with a match that
            // has just been settled — `PveStore.recentFor` keeps one findable for a couple of
            // minutes so a player killed between the last card and the result still sees it — and
            // offering to "resume" a match that is over would walk them into a result panel they
            // have already read. `isOver` is the whole of that distinction.
            val resumable = pve?.match
                ?.takeIf { pve.isOver.not() }
                ?.let { live -> opponents.npcs.firstOrNull { it.iconId == live.opponentIconId } }

            OpponentScreen(
                profile = profile,
                catalog = opponents,
                formatId = formatId,
                // The card table too, since a row now draws the cards its opponent can drop. An
                // empty map before the catalog has loaded, which costs the drop lines for the one
                // frame that state can reach this screen — the startup gate makes it unreachable
                // in practice, and an opponent list is worth more than a spinner in front of it.
                cards = startup.catalog?.all?.associateBy { it.id }.orEmpty(),
                sets = startup.catalog?.sets.orEmpty(),
                hour = clock.localHour(),
                onChallenge = {
                    choice.opponent = it
                    onNavigate(Screen.MATCH)
                },
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
                resumable = resumable,
                // The same door a challenge goes through, and that is the point: the board it
                // opens resumes `against` this opponent and finds the match already there, so
                // there is no second path into a match and nothing here that could deal one.
                onResume = {
                    choice.opponent = it
                    onNavigate(Screen.MATCH)
                },
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
            pvp = pvp,
            pve = pve,
            clock = clock,
            settings = settings,
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

        // The rules, and finding somebody to play. Paired to keep this `when` inside the complexity
        // gate; the lobby needs the catalogues as well now that sitting down asks for a deck.
        Screen.HELP, Screen.PVP, Screen.PVP_TABLE -> SocialDestination(
            destination = destination,
            profile = profile,
            pvp = pvp,
            catalog = startup.catalog,
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

@Composable
@Suppress("LongParameterList")
private fun SocialDestination(
    destination: Screen,
    profile: GameSave,
    pvp: PvpSession?,
    catalog: CardCatalog?,
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
                // Both catalogues, because sitting down at a table now asks which deck to bring
                // and that question needs the cards to draw and the table's format to admit them.
                catalog = catalog,
                formats = formats,
                // Ticking, not sampled — see [rememberNow]. This is what a table's "expires in
                // n min" counts against, and it used to be read once and never again.
                now = rememberNow(clock),
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
                    // The deck is part of the proposal now, and only the format the host picks
                    // can say which of their decks it admits. See `PvpTableScreen.DeckPicker`.
                    catalog = catalog ?: return@let,
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

@Composable
@Suppress("LongParameterList")
private fun MatchDestinations(
    destination: Screen,
    profile: GameSave,
    startup: StartupState,
    choice: Choice,
    gate: ProfileGate,
    // No `account` and no `reporter` any more, and both absences say the same thing: a board no
    // longer credits anybody or reports anything. The server settles the match and sends back the
    // profile it wrote — see `PveOutcome.player`.
    pvp: PvpSession?,
    pve: PveSession?,
    clock: Clock,
    settings: SettingsHolder?,
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
                    // The turn timer's other half. A deadline arriving correctly on every poll
                    // still reads as a frozen countdown when subtracted from a frozen `now` —
                    // see [rememberNow], which is the whole of why this screen had no clock.
                    now = rememberNow(clock),
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
                    // The claim deadline is the one the server settles *for* the player, so a
                    // countdown that does not count is the worst place to have one.
                    now = rememberNow(clock),
                    onDone = { onNavigate(Screen.PVP) },
                )
            }
        }

        Screen.CAMPAIGN, Screen.CAMPAIGN_MATCH -> CampaignDestination(
            destination = destination,
            campaign = choice.campaign,
            profile = profile,
            startup = startup,
            pve = pve,
            clock = clock,
            onIntent = gate.perform,
            onNavigate = onNavigate,
        )

        Screen.TUTORIAL -> TutorialDestination(
            profile = profile,
            startup = startup,
            from = choice.lesson,
            // `maxOf`, not an assignment: the list can start any lesson, so finishing lesson two
            // after lesson five would otherwise take the course backwards.
            onFinished = { done ->
                settings?.update { it.copy(lessonsDone = maxOf(it.lessonsDone, done)) }
            },
            onNavigate = onNavigate,
        )

        else -> MatchDestination(
            startup = startup,
            profile = profile,
            opponent = choice.opponent,
            pve = pve,
            onExit = { onNavigate(Screen.OPPONENTS) },
        )
    }
}

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

@Composable
@Suppress("LongParameterList")
private fun MatchDestination(
    startup: StartupState,
    profile: GameSave,
    opponent: Npc?,
    pve: PveSession?,
    onExit: () -> Unit,
) {
    val chosen = opponent ?: return
    val catalog = startup.catalog ?: return
    // The format this match is played under: the widest one, the same the roster was listed in.
    // Behind the splash, so a missing catalogue is unreachable rather than a degraded mode.
    val format = startup.formats?.default ?: return
    // No server, no match. The card that got here is dimmed without one — see the roster — so this
    // is the guard rather than the message, and it is the same guard multiplayer already had.
    val session = pve ?: return

    /*
     * Which deck to bring, decided **before** the match is asked for.
     *
     * It used to be asked on the way into a board that had already been dealt, because the deal was
     * this process's to redo. The referee deals once, from the request, so the question has to come
     * first — and the answer travels as a save slot (`PveMatchRequest.deck`) rather than as five
     * cards, which is what stops a client naming a card it does not own.
     *
     * Null is "not answered yet". [ANY_DECK] is an answer: it is what Random means here.
     */
    var deck by remember(chosen.iconId) { mutableStateOf<Int?>(null) }
    var resumed by remember(chosen.iconId) { mutableStateOf(false) }

    // Built before the early returns below, like everything else Compose has to keep state for:
    // `rememberCoroutineScope` inside it is a slot, and a slot that exists on some compositions
    // and not others is one Compose cannot track.
    val rematch = rematchExit(session) { deck = null }

    // First, and on its own: a match interrupted by a closed app comes back on its own board, and
    // asking that player which deck to bring would be offering to re-deal a game in progress. That
    // is the whole of what "a dropped connection is not an abandon" amounts to on this side.
    LaunchedEffect(session, chosen.iconId) {
        session.resume(against = chosen.iconId)
        resumed = true
    }
    if (!resumed) return

    // The **declared** rule, not the one the match ends up under: the roulette is drawn by the
    // referee and could add Random on top. That costs nothing — the request's deck is a preference
    // and the server has the last word — whereas asking the server first would put a round trip in
    // front of a question the opponent's own data already answers.
    if (session.match == null && deck == null && !chosen.gameRules().random) {
        DeckSelectorScreen(
            profile = profile,
            catalog = catalog,
            format = format,
            terms = MatchTerms(
                opponent = LocalStrings.current[chosen.nameKey],
                rules = chosen.gameRules(),
            ),
            onChoose = { deck = it },
            onBack = onExit,
        )
        return
    }

    LaunchedEffect(session, chosen.iconId, deck) {
        if (session.match != null) return@LaunchedEffect
        session.deck = deck ?: ANY_DECK
        session.open(chosen.iconId, format.id)
    }

    MatchArt(startup) {
        PveMatchScreen(
            session = session,
            catalog = catalog,
            npc = chosen,
            onExit = onExit,
            again = rematch,
        )
    }
}

/**
 * "Play again" in free play, which starts one screen earlier than the board.
 *
 * A second match against the same opponent is a second **deal**, and the deck is part of a deal:
 * Reverse or Fallen Ace turns a deck of aces into the wrong deck, and a player who has just watched
 * one lose is exactly the player who wants to bring another. So this puts the question back rather
 * than reusing the answer, which is what the board's own rematch control did — it called
 * `PveSession.open` with whatever [PveSession.deck] was still holding, and the selector could not
 * reappear because [MatchDestination] remembers its answer for as long as the opponent does not
 * change.
 *
 * A ladder rung is the opposite and says so itself — see [CampaignRung], where a drawn rung is
 * replayed with the five cards it was drawn with.
 *
 * Both writes are plain state, so the recomposition they cause sees both: `match == null` and
 * `deck == null` together is the selector's own condition, and the effect that opens a match is
 * below the early return it takes, so nothing is dealt behind it.
 */
@Composable
private fun rematchExit(
    session: PveSession,
    onAsk: () -> Unit,
): ScriptExit {
    val audio = LocalAudio.current
    val scope = rememberCoroutineScope()

    return ScriptExit(StringKeys.REMATCH) {
        // Acknowledged on the frame the tap happens on. Nothing else answers it: the deck screen
        // is silent, and the deal's own sound is a request and a round trip away.
        scope.launch { audio.play(Sound.NEW_MATCH) }
        session.clear()
        onAsk()
    }
}

@Composable
@Suppress("LongParameterList")
private fun CampaignDestination(
    destination: Screen,
    campaign: Campaign?,
    profile: GameSave,
    startup: StartupState,
    pve: PveSession?,
    clock: Clock,
    onIntent: suspend (Intent) -> IntentOutcome,
    onNavigate: (Screen) -> Unit,
) {
    val ladder = campaign ?: return
    val scope = rememberCoroutineScope()
    val toOpponents = { onNavigate(Screen.OPPONENTS) }
    val catalog = startup.catalog
    // **The ladder's own** format, and not the one free matches use. A ladder names the format
    // its rungs are played under — that is what `Campaign.format` is for — so the FFXIV Cup is
    // FFXIV cards under the FFXIV pool however wide the free-play format happens to be.
    val format = startup.formats?.get(ladder.format)

    /*
     * Whether the profile can field a hand this ladder's format admits at all.
     *
     * The rung itself asks *which* deck — see [CampaignRung] — but the entry fee is taken one
     * screen earlier, and a run bought for a format the player owns no deck for is four rungs the
     * referee refuses to deal. So the question is asked twice, at two different altitudes: "is
     * there one" before the money, "which one" before each board.
     */
    val hasDeck = remember(profile.decks, profile.cards, catalog, format) {
        catalog != null && format != null &&
            PveMatches.playableDecks(profile, catalog, format).isNotEmpty()
    }

    if (destination == Screen.CAMPAIGN) {
        CampaignScreen(
            campaign = ladder,
            profile = profile,
            cards = startup.catalog?.all?.associateBy { it.id }.orEmpty(),
            // Today, by the same 00:00 UTC boundary the daily quests roll on — one entry per
            // ladder per day. The screen only *shows* the limit; `CampaignRewards.enter` applies
            // it on the side that holds the profile, and this clock is not the one that decides.
            today = questDayOf(clock.nowMillis()),
            // False shuts the ladder with a reason rather than letting the fee be taken for a run
            // whose every rung the referee would refuse to deal.
            hasDeck = hasDeck,
            // The fee is taken here, on the way in, and never given back: `startCampaign`'s
            // handler does `Game.PROFILE_DATAS.MGP -= 500` and then opens the ladder. A defeat
            // costs another 500 to try again, which is the whole of what makes a ladder a stake.
            //
            // Charged by whoever holds the profile rather than deducted here — the amount is the
            // server's, and a client that applied its own could apply none. See
            // `EnterCampaignRequest`.
            //
            // Awaited before the board opens, and that is the whole of the ordering: the first
            // rung's match names this ladder, and `PveMatchRequest.campaignKey` is checked against
            // the run the server holds. Navigating while the entry was still in flight opened the
            // match against a run that did not exist yet and was refused `NOT_ON_THAT_RUNG` — a
            // race a loopback link always won and a phone on a real network always lost.
            onStart = {
                scope.launch {
                    // `REFUSED` still goes through: an entry the server declines because a run on
                    // this ladder is already open is exactly the resuming case, and the button
                    // said `CONTINUE` for it. Only "we never got an answer" stays put.
                    if (onIntent(Intent.EnterCampaign(ladder.key, ladder.fee)) !=
                        IntentOutcome.UNREACHABLE
                    ) {
                        onNavigate(Screen.CAMPAIGN_MATCH)
                    }
                }
            },
            onBack = toOpponents,
        )
    } else {
        if (catalog != null && format != null && pve != null) {
            MatchArt(startup) {
                CampaignMatchScreen(
                    campaign = ladder,
                    catalog = catalog,
                    format = format,
                    pve = pve,
                    profile = profile,
                    onFinished = toOpponents,
                    // Only the run on *this* ladder resumes it; one left open on the other is
                    // somebody else's business and must not seed this board's rung.
                    resumedStep = profile.campaignRun
                        ?.takeIf { it.campaignKey == ladder.key }
                        ?.step,
                )
            }
        }
    }
}

@Composable
private fun MatchArt(startup: StartupState, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCardArt provides startup.art,
        LocalBannerArt provides rememberBannerArt(LocalStrings.current.locale),
        content = content,
    )
}

@Stable
internal class Choice {
    var opponent: Npc? by mutableStateOf(null)
    var campaign: Campaign? by mutableStateOf(null)

    var invitee: String? by mutableStateOf(null)

    var lesson: Int by mutableStateOf(0)
}

internal val PLAYING_SCREENS =
    setOf(Screen.MATCH, Screen.TUTORIAL, Screen.CAMPAIGN_MATCH, Screen.PVP_MATCH)

@Composable
@Suppress("LongParameterList")
private fun TutorialDestination(
    profile: GameSave,
    startup: StartupState,
    from: Int,
    onFinished: (Int) -> Unit,
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
            onHelp = { onNavigate(Screen.HELP) },
            onExit = { onNavigate(Screen.LESSONS) },
            from = from,
            onFinished = onFinished,
        )
    }
}

/*
 * `queueKeyFor` used to live here: the submission queue a finished match was reported into, keyed
 * by server and username so two accounts on one device could not read each other's backlog.
 *
 * It is gone with its only caller. A match is settled by the server as it is played, so there is
 * nothing left to report afterwards and nothing to queue while offline — a match that cannot reach
 * the server is not played rather than played and posted later. `MatchSettlement` still drains the
 * queue for transcripts written by an earlier version; that path goes with `MatchTranscript`.
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
