package com.monarch.app.ui

import android.net.Uri

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
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
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.dashboard.DashboardScreen
import com.monarch.app.ui.settings.SettingsScreen
import com.monarch.app.ui.social.SocialScreen
import com.monarch.app.ui.social.HunterScreen
import com.monarch.app.ui.train.WorkoutLogScreen
import com.monarch.app.ui.train.WorkoutDetailScreen
import com.monarch.app.domain.MeasurementSite
import com.monarch.app.ui.stats.MeasurementDetailScreen
import com.monarch.app.ui.stats.StatsScreen
import com.monarch.app.ui.titles.TitlesScreen
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.monarch.app.ui.theme.inkStroke
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.train.ExerciseExplorerScreen
import com.monarch.app.ui.train.PresetsScreen
import com.monarch.app.ui.train.PresetEditorScreen
import com.monarch.app.ui.train.SessionScreen
import com.monarch.app.ui.idle.IdleScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val PRESETS = "presets"
    const val EXERCISES = "exercises"
    const val STATS = "stats"
    const val TITLES = "titles"
    const val IDLE = "idle"
    const val SETTINGS = "settings"
    const val SOCIAL = "social"
    const val WORKOUT_LOG = "workout_log"
    const val WORKOUT_DETAIL = "workout/{sessionId}"
    const val HUNTER = "hunter/{userId}?name={name}"
    const val MEASUREMENT = "measurement/{site}"
    const val PRESET_EDITOR = "preset_editor?presetId={presetId}"
    const val SESSION = "session/{sessionId}"

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

    fun hunter(userId: String, name: String): String =
        "hunter/${Uri.encode(userId)}?name=${Uri.encode(name)}"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MonarchRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        BottomDestination(Routes.DASHBOARD, "Court", Icons.Outlined.Home),
        BottomDestination(Routes.PRESETS, "Train", Icons.Outlined.FitnessCenter),
        BottomDestination(Routes.STATS, "Stats", Icons.Outlined.BarChart),
        BottomDestination(Routes.TITLES, "Codex", Icons.Outlined.AutoStories),
        BottomDestination(Routes.SOCIAL, "Guild", Icons.Outlined.Groups),
        BottomDestination(Routes.IDLE, "Shadow", Icons.Outlined.Bedtime),
    )

    Box(
        Modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF0B0C0F), Color(0xFF0E1013), Color(0xFF0B0C0F))),
        ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute in destinations.map { it.route }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MonarchColors.Vault)
                            // The bar spans the screen, so its fill stays square -
                            // a wobbling full-bleed edge reads as a rendering fault.
                            // Its TOP edge is brushed instead, which is the only part
                            // that meets the page.
                            .drawBehind {
                                inkStroke(
                                    from = Offset(0f, 0f),
                                    to = Offset(size.width, 0f),
                                    color = MonarchColors.Bracket,
                                    widthPx = 2.dp.toPx(),
                                    seed = 61,
                                    taperEnds = false,
                                )
                            }
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        if (selected) Modifier.border(1.dp, MonarchColors.Emerald, slotShape) else Modifier,
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
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.label,
                                    tint = if (selected) MonarchColors.Emerald else MonarchColors.InkMuted,
                                )
                                Text(
                                    destination.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = if (selected) MonarchColors.EmeraldBright else MonarchColors.InkMuted,
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
                        onOpenCodex = {
                            // Same semantics as tapping the Codex tab: keep home
                            // on the stack so system-back returns to Court.
                            navController.navigate(Routes.TITLES) {
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.PRESETS) {
                    PresetsScreen(
                        onEdit = { id -> navController.navigate(Routes.presetEditor(id)) },
                        onNew = { navController.navigate(Routes.presetEditor()) },
                        onStartSession = { id -> navController.navigate(Routes.session(id)) },
                        onQuickSession = { id -> navController.navigate(Routes.session(id)) },
                        onOpenExercises = { navController.navigate(Routes.EXERCISES) },
                        onOpenLog = { navController.navigate(Routes.WORKOUT_LOG) },
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
                    )
                }
                composable(Routes.TITLES) { TitlesScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
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
                composable(Routes.IDLE) { IdleScreen() }
                composable(Routes.SOCIAL) {
                    SocialScreen(
                        onOpenHunter = { userId, name ->
                            navController.navigate(Routes.hunter(userId, name))
                        },
                    )
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
                    Routes.HUNTER,
                    arguments = listOf(
                        navArgument("userId") { type = NavType.StringType },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    HunterScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        displayName = entry.arguments?.getString("name").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
