package com.ironvellum.app.ui.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.data.cloud.Account
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.SessionAudience
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.IronvellumColors

/** This preview is an approximation from local metadata, not an impersonated server read. */
internal fun ownProfileSessions(account: Account?, friendView: Boolean, sessions: List<WorkoutSession>): List<WorkoutSession> {
    if (account == null || !account.profileLoaded) return emptyList()
    if (friendView && account.visibility != "public" && account.visibility != "friends") return emptyList()
    return sessions.asSequence()
        .filter { it.completedAtMs != null }
        .filter { !friendView || (!it.imported && it.audience != SessionAudience.PRIVATE) }
        .take(5)
        .toList()
}

@Composable
internal fun OwnSocialProfileScreen(
    friendView: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onViewAsFriend: () -> Unit,
    onWorkout: (Long) -> Unit,
) {
    val app = LocalContext.current.applicationContext as IronvellumApp
    val account by app.accountRepository.account.collectAsStateWithLifecycle()
    val profile by remember(app) { app.repository.observeProfile() }.collectAsStateWithLifecycle(initialValue = null)
    val crest by remember(app) { app.repository.observeEquippedFrame() }.collectAsStateWithLifecycle(initialValue = null)
    val sessions by remember(app) { app.repository.observeSocialProfileSessions() }.collectAsStateWithLifecycle(initialValue = null)
    OwnSocialProfileContent(account, profile, crest, sessions, friendView, onBack, onEdit, onSettings, onViewAsFriend, onWorkout)
}

@Composable
internal fun OwnSocialProfileContent(
    current: Account?,
    profile: PlayerProfile?,
    crest: String?,
    sessions: List<WorkoutSession>?,
    friendView: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onViewAsFriend: () -> Unit,
    onWorkout: (Long) -> Unit,
) {
    val visible = current != null && current.profileLoaded &&
        (!friendView || current.visibility == "public" || current.visibility == "friends")
    val selected = remember(current, friendView, sessions) { ownProfileSessions(current, friendView, sessions.orEmpty()) }
    val previewTitle = UiText.themed(R.string.own_social_friend_preview, R.string.own_social_friend_preview__t).asString()

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        PushedHeader(
            if (friendView) previewTitle else stringResource(R.string.own_social_profile),
            onBack,
        ) {
            if (!friendView && visible) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.acct_settings_description), tint = IronvellumColors.InkMuted)
                }
            }
        }
        if (!visible) {
            ListRow(
                label = if (friendView) UiText.themed(R.string.own_social_preview_unavailable, R.string.own_social_preview_unavailable__t).asString()
                else stringResource(R.string.own_social_account_unavailable),
                icon = Icons.Outlined.Lock,
            )
        } else {
            InkPanel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                IdentityRow(
                    displayName = current.displayName,
                    userId = current.userId,
                    wornTitle = if (friendView) null else Titles.displayName(profile?.currentTitleId),
                    level = if (friendView) null else profile?.let { Xp.levelFor(it.totalXp) },
                    size = IdentitySize.Hero,
                    frameId = if (friendView) null else crest,
                    ring = if (friendView) AvatarRing.Iron else AvatarRing.Own,
                )
                if (!friendView) {
                    profile?.let {
                        ListRow(label = stringResource(R.string.own_social_total_xp), value = it.totalXp.toString(), icon = Icons.Outlined.Star)
                    }
                    ListRow(
                        label = stringResource(R.string.own_social_visibility),
                        value = when (current.visibility) {
                            "public" -> stringResource(R.string.acct_visibility_public)
                            "friends" -> UiText.themed(R.string.acct_visibility_friends, R.string.acct_visibility_friends__t).asString()
                            "private" -> stringResource(R.string.acct_visibility_private)
                            else -> stringResource(R.string.own_social_visibility_unknown)
                        },
                        icon = Icons.Outlined.Visibility,
                    )
                }
            }
            if (!friendView) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileAction(stringResource(R.string.own_social_edit), Icons.Outlined.Edit, onEdit, Modifier.weight(1f))
                    ProfileAction(previewTitle, Icons.Outlined.Visibility, onViewAsFriend, Modifier.weight(1f))
                }
            } else {
                Text(
                    UiText.themed(R.string.own_social_preview_explanation, R.string.own_social_preview_explanation__t).asString(),
                    style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 16.dp),
                )
            }
            SectionHeader(UiText.themed(R.string.own_social_recent_workouts, R.string.own_social_recent_workouts__t).asString())
            if (sessions == null) {
                Text(UiText.themed(R.string.own_social_loading, R.string.own_social_loading__t).asString(), color = IronvellumColors.InkMuted)
            } else if (selected.isEmpty()) {
                Text(UiText.themed(R.string.own_social_no_workouts, R.string.own_social_no_workouts__t).asString(), color = IronvellumColors.InkMuted)
            } else {
                selected.forEach { session ->
                    ListRow(
                        label = session.title.ifBlank { session.label },
                        subline = formatDate(checkNotNull(session.completedAtMs)),
                        value = stringResource(R.string.own_social_workout_xp, session.xpAwarded),
                        icon = if (session.audience == SessionAudience.PRIVATE || current.visibility == "private") Icons.Outlined.Lock else Icons.Outlined.FitnessCenter,
                        onClick = if (friendView) null else ({ onWorkout(session.id) }),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = IronvellumColors.EmeraldBright)
            Text(label, color = IronvellumColors.Ink)
        }
    }
}
