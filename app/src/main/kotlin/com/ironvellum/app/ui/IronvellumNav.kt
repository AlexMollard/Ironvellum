package com.ironvellum.app.ui

import android.net.Uri

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.withStarted
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.onboarding.OnboardingScreen
import com.ironvellum.app.ui.onboarding.OnboardingViewModel
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.dashboard.DashboardScreen
import com.ironvellum.app.ui.settings.SettingsScreen
import com.ironvellum.app.ui.settings.SupportScreen
import com.ironvellum.app.ui.social.AccountSettingsScreen
import com.ironvellum.app.ui.social.SocialScreen
import com.ironvellum.app.ui.social.LifterScreen
import com.ironvellum.app.ui.social.CommentsScreen
import com.ironvellum.app.ui.theme.InkCircleShape
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.flow.first
import com.ironvellum.app.ui.train.WorkoutLogScreen
import com.ironvellum.app.ui.train.WorkoutDetailScreen
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.ui.stats.MeasurementDetailScreen
import com.ironvellum.app.ui.stats.StatsScreen
import com.ironvellum.app.ui.titles.TitlesScreen
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.train.ExerciseExplorerScreen
import com.ironvellum.app.ui.train.PresetsScreen
import com.ironvellum.app.ui.train.PresetEditorScreen
import com.ironvellum.app.ui.program.ProgramBuilderScreen
import com.ironvellum.app.ui.program.MuscleCoverageScreen
import com.ironvellum.app.ui.train.SessionScreen
import com.ironvellum.app.ui.idle.IdleScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val PRESETS = "presets"
    const val EXERCISES = "exercises"
    const val STATS = "stats"
    const val TITLES = "titles"
    const val IDLE = "idle"
    const val SETTINGS = "settings"
    const val SUPPORT = "support"
    const val SOCIAL = "social"
    const val ACCOUNT = "account"
    const val WORKOUT_LOG = "workout_log"
    const val MUSCLE_COVERAGE = "muscle_coverage"
    const val WORKOUT_DETAIL = "workout/{sessionId}"
    const val LIFTER = "hunter/{userId}?name={name}"
    const val COMMENTS = "comments/{sessionId}?owner={ownerId}&headline={headline}"
    const val MEASUREMENT = "measurement/{site}"
    const val PRESET_EDITOR = "preset_editor?presetId={presetId}"
    const val SESSION = "session/{sessionId}"
    const val PROGRAM_BUILDER = "program_builder?mode={mode}&presetId={presetId}"

    fun programBuilder(mode: String, presetId: Long? = null): String =
        if (presetId == null) "program_builder?mode=$mode" else "program_builder?mode=$mode&presetId=$presetId"

    fun presetEditor(presetId: Long? = null): String =
        if (presetId == null) "preset_editor" else "preset_editor?presetId=$presetId"

    fun session(sessionId: Long): String = "session/$sessionId"

    fun workoutDetail(sessionId: Long): String = "workout/$sessionId"

    /**
     * Display names only carry a length constraint server-side, so they can
     * hold spaces, '&' and '?' — raw interpolation would truncate or corrupt
     * the route. Navigation decodes the argument on the way out.
     */
    fun measurement(site: MeasurementSite): String = "measurement/${site.name}"

    fun lifter(userId: String, name: String): String =
        "hunter/${Uri.encode(userId)}?name=${Uri.encode(name)}"

    /** Same encoding rule as [lifter]: a workout title can hold '&', '?' and spaces. */
    fun comments(sessionId: String, ownerId: String, headline: String): String =
        "comments/${Uri.encode(sessionId)}?owner=${Uri.encode(ownerId)}&headline=${Uri.encode(headline)}"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun IronvellumRoot(inboxRequest: Int = 0, todayRequest: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // First-run gate, driven by state the profile already stores: a null
    // height means the lifter has never set up. Completing the profile step
    // writes the height; the flow then holds the gate open for the rest of
    // that process (OnboardingViewModel._flowActive) so the training questions
    // and the proposed week still run, and accepting or skipping releases it.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer {
                        val app = appContext.applicationContext
                        OnboardingViewModel(ironvellumRepository(), app)
                    } },
    )
    val needsSetup by onboardingViewModel.needsSetup.collectAsStateWithLifecycle()
    // Drives the dot on the Allies slot; CloudSync keeps it at 0 when signed out.
    val ironvellumApp = appContext.applicationContext as com.ironvellum.app.IronvellumApp
    val inboxUnread by ironvellumApp.cloudSync.inboxUnread.collectAsStateWithLifecycle()

    val destinations = listOf(
        BottomDestination(Routes.DASHBOARD, "Today", Icons.Outlined.Home),
        BottomDestination(Routes.PRESETS, "Rites", Icons.Outlined.FitnessCenter),
        BottomDestination(Routes.STATS, "Ledger", Icons.Outlined.BarChart),
        BottomDestination(Routes.TITLES, "Codex", Icons.Outlined.AutoStories),
        BottomDestination(Routes.SOCIAL, "Allies", Icons.Outlined.Groups),
        // The Garrison is reached from Today's footer, not a tab: five is the
        // most a bottom bar should carry, and it is a place visited now and then.
    )

    Box(
        Modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF0B0C0F), Color(0xFF0E1013), Color(0xFF0B0C0F))),
        ),
    ) {
        // Gate render, not a route: onboarding has no back stack, no bottom
        // bar and nothing to navigate back to.
        if (needsSetup == true) {
            OnboardingScreen(viewModel = onboardingViewModel)
        } else if (needsSetup != null) {
        // Leaving mid-workout (a stray back swipe, Android reclaiming the
        // process) used to land the lifter on Today with a Start button, as if
        // the trial were gone. Once per launch, a recent one reopens instead.
        // Saveable, so rotation or a restored task does not re-trigger it.
        val repo = (appContext.applicationContext as com.ironvellum.app.IronvellumApp).repository
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        var resumeChecked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (resumeChecked) return@LaunchedEffect
            resumeChecked = true
            val id = repo.resumableSessionId(System.currentTimeMillis()) ?: return@LaunchedEffect
            // Only on a started screen: navigating while the activity is being
            // torn down strands a back stack entry mid-lifecycle and crashes.
            lifecycle.withStarted {
                if (navController.currentDestination?.route != Routes.SESSION) navController.navigate(Routes.session(id))
            }
        }
        // One inbox read per launch once an account is live, so the Allies dot
        // is right before the tab is ever opened. The app restores the account
        // asynchronously, so wait for it rather than reading it once.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            ironvellumApp.accountRepository.account.first { it != null }
            ironvellumApp.cloudSync.inbox()
        }
        // A tapped ally notification lands on Allies with INBOX selected. Held
        // until setup is done (this branch), and counted in saveable state so a
        // rotation does not replay a request that was already served.
        var servedInboxRequest by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(inboxRequest) {
            if (inboxRequest <= servedInboxRequest) return@LaunchedEffect
            servedInboxRequest = inboxRequest
            lifecycle.withStarted {
                navController.navigate(Routes.SOCIAL) {
                    popUpTo(Routes.DASHBOARD) { saveState = false }
                    launchSingleTop = true
                    // Fresh entry: a restored one would keep whichever tab was open.
                    restoreState = false
                }
            }
        }
        // A tapped Summons lands on Today, where the scheduled rite begins.
        var servedTodayRequest by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(todayRequest) {
            if (todayRequest <= servedTodayRequest) return@LaunchedEffect
            servedTodayRequest = todayRequest
            lifecycle.withStarted {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.DASHBOARD) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute in destinations.map { it.route }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(IronvellumColors.Vault)
                            // The bar spans the screen, so its fill stays square -
                            // a wobbling full-bleed edge reads as a rendering fault.
                            // Its TOP edge is brushed instead, which is the only part
                            // that meets the page.
                            .drawBehind {
                                inkStroke(
                                    from = Offset(0f, 0f),
                                    to = Offset(size.width, 0f),
                                    color = IronvellumColors.Bracket,
                                    widthPx = 2.dp.toPx(),
                                    seed = 61,
                                    taperEnds = false,
                                )
                            }
                            .navigationBarsPadding()
                            // Five labels share the width. At 360dp - the most
                            // common modern phone - the old 10dp/8dp gaps left
                            // the longest label one glyph short and clipped.
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        destinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            // Theme shape, not a local cut corner: the nav is
                            // the one chrome element on every screen, so it has
                            // to carry the same hand-drawn edge as the panels.
                            val slotShape = MaterialTheme.shapes.small
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(slotShape)
                                    .then(
                                        if (selected) {
                                            Modifier.background(
                                                Brush.verticalGradient(listOf(Color(0xFF1E3A2C), Color(0xFF16281E))),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (selected) Modifier.inkBorder(IronvellumColors.Emerald, slotShape) else Modifier,
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        // Home is the graph start: saving and
                                        // restoring its state would restore the
                                        // stack pushed ON TOP of it (e.g. the
                                        // presets screen), stranding the user.
                                        val isHome = destination.route == Routes.DASHBOARD
                                        navController.navigate(destination.route) {
                                            popUpTo(Routes.DASHBOARD) {
                                                inclusive = isHome
                                                saveState = !isHome
                                            }
                                            launchSingleTop = true
                                            restoreState = !isHome
                                        }
                                    }
                                    // 48dp is the documented minimum touch
                                    // target; the icon and label together only
                                    // came to 31dp, and this is the one control
                                    // present on every screen.
                                    .heightIn(min = 48.dp)
                                    // Selection is drawn with a gradient and an
                                    // ink border, which says nothing to a screen
                                    // reader: without this it announces "Today"
                                    // whether you are on that screen or not.
                                    .semantics {
                                        role = Role.Tab
                                        this.selected = selected
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // The dot is the whole signal, so the slot's
                                // description carries the count for a screen
                                // reader; with nothing unread it stays plain
                                // "Allies", which is how tests find the tab.
                                val unreadHere = destination.route == Routes.SOCIAL && inboxUnread > 0
                                Box {
                                    Icon(
                                        destination.icon,
                                        contentDescription = if (unreadHere) {
                                            "${destination.label}, $inboxUnread unread"
                                        } else {
                                            destination.label
                                        },
                                        tint = if (selected) IronvellumColors.Emerald else IronvellumColors.InkMuted,
                                    )
                                    if (unreadHere) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .size(8.dp)
                                                .clip(InkCircleShape(7))
                                                .background(IronvellumColors.SovereignGold),
                                        )
                                    }
                                }
                                Text(
                                    destination.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = if (selected) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
                                    // Five slots share one screen width, so the
                                    // label must never wrap. It cannot grow
                                    // either: the app pins the text scale
                                    // (IronvellumTheme), so this row has one size
                                    // to fit rather than a range.
                                    //
                                    // No tracking here. labelMedium carries
                                    // 2sp, which on a six-glyph label is 12dp
                                    // of pure letter spacing - the reason an
                                    // old nav label lost its last glyph at
                                    // 360dp.
                                    letterSpacing = 0.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(padding),
                enterTransition = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                        androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { it / 8 }
                },
                exitTransition = {
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                },
                popEnterTransition = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
                },
                popExitTransition = {
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
                        androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 8 }
                },
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onStartSession = { id -> navController.navigate(Routes.session(id)) },
                        onOpenPresets = { navController.navigate(Routes.PRESETS) { launchSingleTop = true } },
                        onOpenForge = { navController.navigate(Routes.programBuilder("week", null)) },
                        onOpenWorkout = { id -> navController.navigate(Routes.workoutDetail(id)) },
                        onOpenCodex = {
                            // Same semantics as tapping the Codex tab: keep home
                            // on the stack so system-back returns to Today.
                            navController.navigate(Routes.TITLES) {
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenLedger = {
                            navController.navigate(Routes.STATS) {
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenGarrison = { navController.navigate(Routes.IDLE) { launchSingleTop = true } },
                    )
                }
                composable(Routes.PRESETS) {
                    PresetsScreen(
                        onEdit = { id -> navController.navigate(Routes.presetEditor(id)) },
                        onNew = { navController.navigate(Routes.presetEditor()) },
                        onGenerate = { mode, presetId ->
                            navController.navigate(Routes.programBuilder(mode, presetId))
                        },
                        onStartSession = { id -> navController.navigate(Routes.session(id)) },
                        onQuickSession = { id -> navController.navigate(Routes.session(id)) },
                        onOpenExercises = { navController.navigate(Routes.EXERCISES) },
                        onOpenLog = { navController.navigate(Routes.WORKOUT_LOG) },
                        onOpenWorkout = { id -> navController.navigate(Routes.workoutDetail(id)) },
                        onOpenCoverage = { navController.navigate(Routes.MUSCLE_COVERAGE) },
                    )
                }
                composable(Routes.MUSCLE_COVERAGE) {
                    MuscleCoverageScreen(
                        onBack = { navController.popBackStack() },
                        // "session" mode builds one workout aimed at the
                        // muscles this map just showed as neglected.
                        onGenerateSession = { navController.navigate(Routes.programBuilder("session")) },
                    )
                }
                composable(
                    Routes.PROGRAM_BUILDER,
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType },
                        navArgument("presetId") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { entry ->
                    ProgramBuilderScreen(
                        mode = entry.arguments?.getString("mode") ?: "week",
                        presetId = entry.arguments?.getLong("presetId")?.takeIf { it > 0 },
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(Routes.EXERCISES) {
                    ExerciseExplorerScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.PRESET_EDITOR,
                    arguments = listOf(navArgument("presetId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    PresetEditorScreen(
                        presetId = entry.arguments?.getLong("presetId")?.takeIf { it > 0 },
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.SESSION,
                    arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                ) { entry ->
                    SessionScreen(
                        sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                        onExit = { navController.popBackStack() },
                    )
                }
                composable(Routes.STATS) {
                    StatsScreen(
                        onOpenMeasurement = { site -> navController.navigate(Routes.measurement(site)) },
                        onOpenLog = { navController.navigate(Routes.WORKOUT_LOG) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.TITLES) { TitlesScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenSupport = { navController.navigate(Routes.SUPPORT) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.SUPPORT) { SupportScreen() }
                composable(
                    Routes.MEASUREMENT,
                    arguments = listOf(navArgument("site") { type = NavType.StringType }),
                ) { entry ->
                    val site = MeasurementSite.entries
                        .firstOrNull { it.name == entry.arguments?.getString("site") }
                    if (site == null) {
                        // Unknown site in a deep link: go back rather than crash.
                        navController.popBackStack()
                    } else {
                        MeasurementDetailScreen(site = site, onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.IDLE) { IdleScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.SOCIAL) {
                    SocialScreen(
                        onOpenLifter = { userId, name ->
                            navController.navigate(Routes.lifter(userId, name))
                        },
                        onOpenComments = { sessionId, ownerId, headline ->
                            navController.navigate(Routes.comments(sessionId, ownerId, headline))
                        },
                        onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                        inboxRequest = inboxRequest,
                    )
                }
                composable(Routes.ACCOUNT) {
                    AccountSettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.WORKOUT_LOG) {
                    WorkoutLogScreen(
                        onOpenWorkout = { id -> navController.navigate(Routes.workoutDetail(id)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.WORKOUT_DETAIL,
                    arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                ) { entry ->
                    WorkoutDetailScreen(
                        sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.LIFTER,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    LifterScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        displayName = entry.arguments?.getString("name").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.COMMENTS,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("ownerId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("headline") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    CommentsScreen(
                        sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                        ownerId = entry.arguments?.getString("ownerId").orEmpty(),
                        headline = entry.arguments?.getString("headline").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenLifter = { userId, name -> navController.navigate(Routes.lifter(userId, name)) },
                    )
                }
            }
        }
        }
    }
}
