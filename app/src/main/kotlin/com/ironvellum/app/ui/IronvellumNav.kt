package com.ironvellum.app.ui

import android.net.Uri

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.ironvellum.app.ui.theme.InkPressIndication
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import com.ironvellum.app.ui.settings.SettingsSection
import com.ironvellum.app.ui.settings.SettingsSectionScreen
import com.ironvellum.app.ui.settings.settingsViewModel
import com.ironvellum.app.ui.settings.SupportScreen
import com.ironvellum.app.ui.social.AccountScreen
import com.ironvellum.app.ui.social.AccountSettingsScreen
import com.ironvellum.app.ui.social.SocialScreen
import com.ironvellum.app.ui.social.LifterScreen
import com.ironvellum.app.ui.social.CommentsScreen
import com.ironvellum.app.ui.theme.DotShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.flow.first
import com.ironvellum.app.ui.train.WorkoutLogScreen
import com.ironvellum.app.ui.train.WorkoutDetailScreen
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.ui.stats.MeasurementDetailScreen
import com.ironvellum.app.ui.stats.StatsScreen
import com.ironvellum.app.ui.titles.TitlesScreen
import androidx.compose.ui.draw.drawBehind
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.train.ExerciseExplorerScreen
import com.ironvellum.app.ui.train.PresetsScreen
import com.ironvellum.app.ui.train.RiteDetailScreen
import com.ironvellum.app.ui.train.PresetEditorScreen
import com.ironvellum.app.ui.program.ProgramBuilderScreen
import com.ironvellum.app.ui.program.MuscleCoverageScreen
import com.ironvellum.app.ui.train.SessionScreen
import com.ironvellum.app.ui.idle.CrestCollectionScreen
import com.ironvellum.app.ui.idle.IdleScreen
import com.ironvellum.app.ui.idle.RelicVaultScreen

/** Ledger back stack flag: open the weigh-in on arrival. */
private const val LOG_WEIGHT = "log_weight"

object Routes {
    const val DASHBOARD = "dashboard"
    const val PRESETS = "presets"
    const val EXERCISES = "exercises?name={name}"
    const val STATS = "stats"
    const val TITLES = "titles"
    const val IDLE = "idle"
    const val VAULT = "idle/vault"
    const val CRESTS = "idle/crests"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    const val SUPPORT = "support"
    const val SOCIAL = "social"
    const val ACCOUNT = "account"
    const val SIGN_IN = "sign_in"
    const val WORKOUT_LOG = "workout_log"
    const val MUSCLE_COVERAGE = "muscle_coverage"
    const val WORKOUT_DETAIL = "workout/{sessionId}"
    const val LIFTER = "hunter/{userId}?name={name}"
    const val COMMENTS = "comments/{sessionId}?owner={ownerId}&headline={headline}"
    const val MEASUREMENT = "measurement/{site}"
    const val PRESET_EDITOR = "preset_editor?presetId={presetId}"
    const val RITE_DETAIL = "rite/{presetId}"
    const val SESSION = "session/{sessionId}"
    const val PROGRAM_BUILDER = "program_builder?mode={mode}&presetId={presetId}"

    fun programBuilder(mode: String, presetId: Long? = null): String =
        if (presetId == null) "program_builder?mode=$mode" else "program_builder?mode=$mode&presetId=$presetId"

    fun presetEditor(presetId: Long? = null): String =
        if (presetId == null) "preset_editor" else "preset_editor?presetId=$presetId"

    fun session(sessionId: Long): String = "session/$sessionId"

    /** Exercises, opened on one lift when [name] is given. */
    fun exercises(name: String? = null): String = if (name == null) "exercises" else "exercises?name=${Uri.encode(name)}"

    fun riteDetail(presetId: Long): String = "rite/$presetId"

    fun settingsSection(section: SettingsSection): String = "settings/${section.key}"

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

/**
 * A tap on the live trial's notification. [serial] grows per tap so two taps
 * in a row both land; [seal] asks the trial to open with its seal prompt.
 */
data class TrialRequest(val serial: Int, val sessionId: Long, val seal: Boolean)

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val raised: Boolean = false,
)

@Composable
fun IronvellumRoot(inboxRequest: Int = 0, todayRequest: Int = 0, trialRequest: TrialRequest? = null) {
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
        BottomDestination(Routes.STATS, "Ledger", Icons.Outlined.BarChart),
        // Training is what the app is for, so its tab takes the middle slot,
        // under the thumb, and stands proud of the bar.
        BottomDestination(Routes.PRESETS, "Train", DumbbellIcon, raised = true),
        BottomDestination(Routes.TITLES, "Codex", Icons.Outlined.AutoStories),
        BottomDestination(Routes.SOCIAL, "Allies", Icons.Outlined.Person),
        // The Garrison is reached from Today's footer, not a tab: five is the
        // most a bottom bar should carry, and it is a place visited now and then.
    )

    Box(Modifier.background(IronvellumColors.Abyss)) {
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
        // A tapped Summons lands on Train, where the scheduled rite begins.
        var servedTodayRequest by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(todayRequest) {
            if (todayRequest <= servedTodayRequest) return@LaunchedEffect
            servedTodayRequest = todayRequest
            lifecycle.withStarted {
                // The same options as tapping the tab, so Back returns to Today.
                navController.navigate(Routes.PRESETS) {
                    popUpTo(Routes.DASHBOARD) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        // A tapped trial notification reopens the live trial; its Seal action
        // also asks the trial for its seal prompt. The serial the trial has
        // served is saveable, so a rotation does not reopen the prompt.
        var servedTrialRequest by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        var sealFor by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
        var sealSerial by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(trialRequest?.serial) {
            val request = trialRequest ?: return@LaunchedEffect
            if (request.serial <= servedTrialRequest) return@LaunchedEffect
            servedTrialRequest = request.serial
            if (request.seal) {
                sealFor = request.sessionId
                sealSerial++
            }
            lifecycle.withStarted {
                // Already on the trial (or the launch resume put it there): stay.
                if (navController.currentDestination?.route != Routes.SESSION) {
                    navController.navigate(Routes.session(request.sessionId)) { launchSingleTop = true }
                }
            }
        }
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute in destinations.map { it.route }) {
                    IronvellumBottomBar(destinations, currentRoute, inboxUnread, navController)
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
                        onOpenRite = { id -> navController.navigate(Routes.riteDetail(id)) },
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
                        onSetHeight = {
                            // Hub first, so BACK from Profile lands on Settings
                            // and the sub-screen finds the hub's view model.
                            navController.navigate(Routes.SETTINGS)
                            navController.navigate(Routes.settingsSection(SettingsSection.PROFILE))
                        },
                        onOpenLedger = {
                            navController.navigate(Routes.STATS) {
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            // Today offers it to log a weight: open the weigh-in too.
                            navController.getBackStackEntry(Routes.STATS).savedStateHandle[LOG_WEIGHT] = true
                        },
                        onOpenGarrison = { navController.navigate(Routes.IDLE) { launchSingleTop = true } },
                        onOpenLift = { name -> navController.navigate(Routes.exercises(name)) },
                    )
                }
                composable(Routes.PRESETS) {
                    PresetsScreen(
                        onOpenRite = { id -> navController.navigate(Routes.riteDetail(id)) },
                        onNew = { navController.navigate(Routes.presetEditor()) },
                        onGenerate = { mode, presetId ->
                            navController.navigate(Routes.programBuilder(mode, presetId))
                        },
                        onStartSession = { id -> navController.navigate(Routes.session(id)) },
                        onQuickSession = { id -> navController.navigate(Routes.session(id)) },
                        onOpenExercises = { navController.navigate(Routes.exercises()) },
                        onOpenLog = { navController.navigate(Routes.WORKOUT_LOG) },
                        onOpenCoverage = { navController.navigate(Routes.MUSCLE_COVERAGE) },
                        onOpenWorkout = { id -> navController.navigate(Routes.workoutDetail(id)) },
                    )
                }
                composable(
                    Routes.RITE_DETAIL,
                    arguments = listOf(navArgument("presetId") { type = NavType.LongType }),
                ) { entry ->
                    RiteDetailScreen(
                        presetId = entry.arguments?.getLong("presetId") ?: 0L,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.presetEditor(id)) },
                        onStartSession = { id -> navController.navigate(Routes.session(id)) },
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
                composable(
                    Routes.EXERCISES,
                    arguments = listOf(navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    ExerciseExplorerScreen(
                        onBack = { navController.popBackStack() },
                        initialName = entry.arguments?.getString("name"),
                        onOpenChronicle = { navController.navigate(Routes.WORKOUT_LOG) },
                    )
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
                    val id = entry.arguments?.getLong("sessionId") ?: 0L
                    SessionScreen(
                        sessionId = id,
                        onExit = { navController.popBackStack() },
                        onSealed = {
                            // Opened from a notification, Today may not be under it.
                            if (!navController.popBackStack(Routes.DASHBOARD, inclusive = false)) {
                                navController.navigate(Routes.DASHBOARD) { popUpTo(0) }
                            }
                        },
                        sealRequest = if (sealFor == id) sealSerial else 0,
                        // Served once: leaving and reopening the trial (a new
                        // back stack entry, with its own served count) must
                        // not raise the prompt again.
                        onSealRequestServed = { sealFor = 0L },
                    )
                }
                composable(Routes.STATS) { entry ->
                    val logWeight by entry.savedStateHandle.getStateFlow(LOG_WEIGHT, false).collectAsState()
                    StatsScreen(
                        logWeightRequested = logWeight,
                        onLogWeightServed = { entry.savedStateHandle[LOG_WEIGHT] = false },
                        onOpenMeasurement = { site -> navController.navigate(Routes.measurement(site)) },
                        onOpenWorkout = { id -> navController.navigate(Routes.workoutDetail(id)) },
                        onOpenLift = { name -> navController.navigate(Routes.exercises(name)) },
                        onOpenSettings = { navController.navigate(Routes.settingsSection(it)) },
                    )
                }
                composable(Routes.TITLES) { TitlesScreen() }
                composable(Routes.SETTINGS) { entry ->
                    SettingsScreen(
                        viewModel = settingsViewModel(entry),
                        onOpenSection = { navController.navigate(Routes.settingsSection(it)) },
                        onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                        // Signed out, the sign-in form is pushed over Settings so BACK returns here.
                        onOpenSignIn = { navController.navigate(Routes.SIGN_IN) },
                        onOpenSupport = { navController.navigate(Routes.SUPPORT) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.SETTINGS_SECTION,
                    arguments = listOf(navArgument("section") { type = NavType.StringType }),
                ) { entry ->
                    val section = SettingsSection.fromKey(entry.arguments?.getString("section"))
                    // Sub-screens share the hub's view model, so the hub is always
                    // pushed first (Today's SET HEIGHT does so too) and is on the stack.
                    val hub = remember(entry) {
                        runCatching { navController.getBackStackEntry(Routes.SETTINGS) }.getOrDefault(entry)
                    }
                    if (section == null) {
                        navController.popBackStack()
                    } else {
                        SettingsSectionScreen(
                            section = section,
                            onBack = { navController.popBackStack() },
                            viewModel = settingsViewModel(hub),
                        )
                    }
                }
                composable(Routes.SUPPORT) { SupportScreen(onBack = { navController.popBackStack() }) }
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
                composable(Routes.VAULT) {
                    RelicVaultScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.CRESTS) {
                    CrestCollectionScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.IDLE) {
                    IdleScreen(
                        onBack = { navController.popBackStack() },
                        onOpenVault = { navController.navigate(Routes.VAULT) { launchSingleTop = true } },
                        onOpenCrests = { navController.navigate(Routes.CRESTS) { launchSingleTop = true } },
                        onOpenCircle = {
                            navController.navigate(Routes.SOCIAL) {
                                popUpTo(Routes.DASHBOARD) { saveState = false }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                    )
                }
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
                composable(Routes.SIGN_IN) {
                    val signedIn by ironvellumApp.accountRepository.account.collectAsStateWithLifecycle()
                    // Signing in here lands on Allies, where the account now lives.
                    LaunchedEffect(signedIn != null) {
                        if (signedIn != null) {
                            navController.navigate(Routes.SOCIAL) {
                                popUpTo(Routes.DASHBOARD) { saveState = false }
                                launchSingleTop = true
                            }
                        }
                    }
                    AccountScreen(onBack = { navController.popBackStack() }, pushed = true)
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
                        onOpenTrial = { sessionId, ownerId, headline ->
                            navController.navigate(Routes.comments(sessionId, ownerId, headline))
                        },
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

private val BarHeight = 64.dp

/** How far Train's round button stands above the bar's top rule. */
private val TrainRise = 9.dp

/**
 * The mockup's dumbbell: a 2dp round-capped stroke on a 24 grid, tinted by the
 * caller like the Material icons beside it.
 */
private val DumbbellIcon: ImageVector = ImageVector.Builder(
    name = "Dumbbell",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString("M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12").toNodes(),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
).build()

/**
 * Five items spread evenly on a 64dp VaultHigh bar with a 1dp Rune rule. The
 * selected item is an Emerald icon over an Ink label; the rest are InkMuted.
 * Train, in the middle, sits in a round button raised above the bar. The
 * bar's own height includes that rise, so screens above it still clear the
 * button and nothing is clipped.
 */
@Composable
private fun IronvellumBottomBar(
    destinations: List<BottomDestination>,
    currentRoute: String?,
    inboxUnread: Int,
    navController: NavHostController,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(BarHeight + TrainRise)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BarHeight)
                    .background(IronvellumColors.VaultHigh)
                    .drawBehind {
                        drawRect(IronvellumColors.Rune, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                    },
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom,
            ) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val tint = if (selected) IronvellumColors.Emerald else IronvellumColors.InkMuted
                    // The dot is the whole signal, so the slot's description
                    // carries the count for a screen reader; with nothing
                    // unread it stays plain "Allies", which is how tests find
                    // the tab.
                    val unreadHere = destination.route == Routes.SOCIAL && inboxUnread > 0
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .height(BarHeight)
                            .then(if (destination.raised) Modifier.offset(y = -TrainRise) else Modifier)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                interactionSource = interaction,
                                // The raised Train slot presses its round button,
                                // not the square slot around it.
                                indication = if (destination.raised) null else InkPressIndication,
                                onClick = {
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
                                },
                            )
                            // Selection is an Emerald icon and an Ink label,
                            // which says nothing to a screen reader.
                            .semantics {
                                role = Role.Tab
                                this.selected = selected
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val glyph: @Composable () -> Unit = {
                            Box(Modifier.size(20.dp)) {
                                Icon(
                                    destination.icon,
                                    contentDescription = if (unreadHere) {
                                        "${destination.label}, $inboxUnread unread"
                                    } else {
                                        destination.label
                                    },
                                    tint = tint,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (unreadHere) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-3).dp)
                                            .size(8.dp)
                                            .clip(DotShape)
                                            .background(IronvellumColors.Ink),
                                    )
                                }
                            }
                        }
                        if (destination.raised) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(DotShape)
                                    .background(IronvellumColors.Vault)
                                    .indication(interaction, InkPressIndication)
                                    .inkBorder(IronvellumColors.Rune, DotShape),
                                contentAlignment = Alignment.Center,
                            ) { glyph() }
                        } else {
                            glyph()
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
                            // Five slots share one screen width, so the label
                            // never wraps and carries no tracking (labelMedium's
                            // 2sp once clipped the last glyph at 360dp).
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        // The system navigation bar's inset, in the bar's own colour.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(IronvellumColors.VaultHigh),
        )
    }
}
