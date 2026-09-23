package com.ironvellum.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import com.ironvellum.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.Account
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.SyncOutcome
import com.ironvellum.app.data.cloud.isUnclaimedHandle
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.NavChip
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CloudDownload
import java.text.DateFormat
import java.util.Date
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.domain.Titles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton

/** One honest snapshot of the account state: which panel to show and why. */
data class AccountUi(
    val configured: Boolean = Cloud.configured,
    val account: Account? = null,
    val busy: Boolean = false,
    /** The real server/network reason, verbatim — never a generic "failed". */
    val error: String? = null,
    val lastSync: SyncOutcome? = null,
    val friends: List<FriendRow> = emptyList(),
    val friendsLoading: Boolean = false,
    /** Inline failure of the last CLAIM attempt — house copy, never a trace. */
    val claimError: String? = null,
    val claimBusy: Boolean = false,
    /** Skip lives only in the ViewModel: no persisted nag-flag, one skip per session. */
    val claimSkipped: Boolean = false,
    /** Null means the cloud holds no archive yet — shown honestly, not as a fake "backed up". */
    val lastBackup: CloudSync.BackupInfo? = null,
    /** Populated only by an actual restore, so the lifter sees what came back. */
    val lastRestore: Repository.ImportResult? = null,
    /**
     * Backup/restore failures render INSIDE the backup block. The shared
     * [error] slot sits below the allies list, several screens down: a failed
     * BACK UP NOW looked like a button that did nothing at all.
     */
    val backupError: String? = null,
)

class AccountViewModel(
    private val accountRepo: AccountRepository,
    private val cloudSync: CloudSync,
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountUi(account = accountRepo.account.value))
    val ui = _ui.asStateFlow()

    init {
        // Resume a stored session (if any) without blocking the first frame.
        viewModelScope.launch {
            setBusy(true)
            // A failed restore must not look like "signed out": surface the
            // real reason or the panel sits blank with no explanation.
            accountRepo.restore().onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
        // Keep the UI in step with the repository's own session state.
        viewModelScope.launch {
            accountRepo.account.collect { acct ->
                _ui.value = _ui.value.copy(account = acct)
                if (acct != null) {
                    refreshFriends()
                    // The panel must show backup freshness the moment it
                    // appears, not only after a first manual backup.
                    refreshBackup()
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        _ui.value = _ui.value.copy(busy = busy)
    }

    /** Extract the genuine reason from a Result failure — shown verbatim. */
    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            accountRepo.signIn(email, password)
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            accountRepo.signUp(email, password, displayName)
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }


    /** Google sign-in: the credential layer already exchanged the ID token; hand it and the raw nonce to the repository. */
    fun signInWithGoogle(idToken: String, rawNonce: String) {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            accountRepo.signInWithGoogle(idToken, rawNonce)
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            accountRepo.signOut()
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
                // Local UI state is wiped only after the server actually
                // severed the session — clearing on failure left the user
                // signed in behind a friends-less, signed-out-looking UI.
                .onSuccess {
                    _ui.value = _ui.value.copy(lastSync = null, friends = emptyList())
                }
            setBusy(false)
        }
    }

    fun deleteCloudData() {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            accountRepo.deleteCloudData()
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
                // Same rule as signOut: clear local social state only once the
                // server confirmed the rows are gone. The watermark must go
                // too - it claims the cloud already holds these sessions, and
                // leaving it behind meant signing back in never re-uploaded
                // anything.
                .onSuccess {
                    cloudSync.forgetPushedState()
                    _ui.value = _ui.value.copy(lastSync = null, friends = emptyList())
                }
            setBusy(false)
        }
    }

    fun setVisibility(visibility: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(error = null)
            accountRepo.setVisibility(visibility)
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
        }
    }

    fun claimName(raw: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(claimBusy = true, claimError = null)
            accountRepo.updateDisplayName(raw)
                // Local state update happens inside the repository, so every
                // surface flips to the claimed name with the cloud write.
                .onFailure { _ui.value = _ui.value.copy(claimError = it.reason()) }
            _ui.value = _ui.value.copy(claimBusy = false)
        }
    }

    fun skipClaim() {
        _ui.value = _ui.value.copy(claimSkipped = true)
    }

    fun syncNow() {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            cloudSync.push()
                .onSuccess { _ui.value = _ui.value.copy(lastSync = it) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }

    /**
     * Re-uploads every completed session, ignoring what the device believes
     * the cloud already holds. The ordinary sync skips anything whose
     * fingerprint matches, which is right until the server loses rows - then
     * the match is a lie and only this can repair it.
     */
    fun reuploadEverything() {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null)
            cloudSync.forgetPushedState()
            cloudSync.push()
                .onSuccess { _ui.value = _ui.value.copy(lastSync = it) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }

    /**
     * Uploads the whole archive to the lifter's own cloud row. The archive
     * omits private notes — the device promise holds even in a full backup.
     */
    fun backUpNow() {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(backupError = null)
            cloudSync.backupArchive()
                .onSuccess { _ui.value = _ui.value.copy(lastBackup = it, backupError = null) }
                .onFailure { _ui.value = _ui.value.copy(backupError = it.reason()) }
            setBusy(false)
        }
    }

    /**
     * Destructive: replaces local training data with the cloud archive. The
     * confirmation lives in the panel; by the time this runs the lifter has
     * already named the consequence.
     */
    fun restoreFromCloud() {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(backupError = null)
            cloudSync.restoreArchive()
                .onSuccess { _ui.value = _ui.value.copy(lastRestore = it, backupError = null) }
                .onFailure { _ui.value = _ui.value.copy(backupError = it.reason()) }
            setBusy(false)
        }
    }

    /** Null result is real information: no archive exists yet in the cloud. */
    private fun refreshBackup() {
        viewModelScope.launch {
            cloudSync.latestBackup()
                .onSuccess { _ui.value = _ui.value.copy(lastBackup = it) }
                .onFailure { _ui.value = _ui.value.copy(backupError = it.reason()) }
        }
    }

    fun refreshFriends() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(friendsLoading = true)
            cloudSync.friends()
                .onSuccess { _ui.value = _ui.value.copy(friends = it) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(friendsLoading = false)
        }
    }

    fun acceptFriend(userId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(error = null)
            cloudSync.acceptFriend(userId)
                .onSuccess { refreshFriends() }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
        }
    }

    fun requestFriend(displayName: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(error = null)
            cloudSync.requestFriend(displayName)
                .onSuccess { refreshFriends() }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
        }
    }
}

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    // No-op default keeps existing call sites compiling; the parent wires the
    // real lifter route in IronvellumNav.kt.
    onOpenLifter: (userId: String, displayName: String) -> Unit = { _, _ -> },
    viewModel: AccountViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AccountViewModel(ironvellumAccount(), ironvellumCloudSync()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // One refresh idiom: pull-to-refresh, like the feed and board. The roster
    // text link is gone. Hosting it here means the signed-in surface owns its
    // own scroll container — a PullToRefreshBox nested inside a verticalScroll
    // Column never sees the drag.
    if (ui.configured && ui.account != null) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = ui.friendsLoading,
            onRefresh = viewModel::refreshFriends,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = ui.friendsLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = IronvellumColors.Vault,
                    color = IronvellumColors.EmeraldBright,
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                SignedInPanels(
                    ui = ui,
                    onOpenLifter = onOpenLifter,
                    onSignOut = viewModel::signOut,
                    onDeleteCloudData = viewModel::deleteCloudData,
                    onVisibility = viewModel::setVisibility,
                    onSync = viewModel::syncNow,
                    onReupload = viewModel::reuploadEverything,
                    onBackup = viewModel::backUpNow,
                    onRestore = viewModel::restoreFromCloud,
                    onAccept = viewModel::acceptFriend,
                    onRequest = viewModel::requestFriend,
                    onClaim = viewModel::claimName,
                    onSkipClaim = viewModel::skipClaim,
                )
                // Clears the bottom nav bar: 28.dp left SEVER THE LINK half
                // hidden behind it at the end of the scroll.
                Spacer(Modifier.height(120.dp))
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AccountTitle()

        when {
            !ui.configured -> NotConfiguredPanel()
            ui.busy && ui.account == null -> BusyPanel("Linking to the Ledger…")
            // Signed OUT with a configured cloud: this is the sign-in form itself.
            // Losing this branch left ACCOUNT rendering nothing but its own
            // title, so there was no way to sign in from anywhere in the app.
            else -> AuthPanels(
                error = ui.error,
                busy = ui.busy,
                googleEnabled = Cloud.googleConfigured,
                onGoogleSignIn = viewModel::signInWithGoogle,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Screen title shared by the signed-in and sign-in layouts. */
@Composable
private fun AccountTitle() {
    Text(
        "ACCOUNT",
        style = MaterialTheme.typography.labelLarge,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = IronvellumTracking.ScreenTitle,
    )
    Text(
        "Cloud link for lifters",
        style = MaterialTheme.typography.labelLarge,
        fontFamily = ChakraPetch,
        color = IronvellumColors.SystemGreen,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NotConfiguredPanel() {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = IronvellumColors.DangerRed)
            Text(
                "CLOUD LINK OFFLINE",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.DangerRed,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "No Supabase endpoint is configured on this device. Sign-in is unavailable — ask the developer to build with SUPABASE_URL and SUPABASE_KEY set.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun BusyPanel(label: String) {
    InkPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkSpinner()
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}
@Composable
private fun AuthPanels(
    error: String?,
    busy: Boolean,
    googleEnabled: Boolean,
    onGoogleSignIn: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    // Segmented auth-mode switch: the shared inked picker.
    InkSegmented(
        options = AuthMode.entries.map { it to if (it == AuthMode.SIGN_IN) "SIGN IN" else "SIGN UP" },
        selected = mode,
        onPick = { mode = it },
    )

    Spacer(Modifier.height(14.dp))

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val passwordValid = password.length >= 6
    val nameValid = displayName.trim().length in 2..24
    val canSubmit = emailValid && passwordValid && (mode == AuthMode.SIGN_IN || nameValid) && !busy

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
        if (googleEnabled) {
            GoogleSignInButton(onToken = onGoogleSignIn)
            Spacer(Modifier.height(14.dp))
        }
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Lifter email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = password,
            onValueChange = { password = it },
            label = { Text("Sigil phrase (min 6)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == AuthMode.SIGN_UP) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = displayName,
                onValueChange = { displayName = it.take(24) },
                label = { Text("Lifter name (2–24)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        IronvellumButton(
            label = if (mode == AuthMode.SIGN_IN) "Sign in" else "Create account",
            onClick = {
                if (mode == AuthMode.SIGN_IN) onSignIn(email, password)
                else onSignUp(email, password, displayName.trim())
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !emailValid && email.isNotEmpty() -> "That email does not read as an email."
                password.isNotEmpty() && !passwordValid -> "The sigil phrase needs at least 6 characters."
                mode == AuthMode.SIGN_UP && displayName.isNotEmpty() && !nameValid ->
                    "Lifter names run 2–24 characters."
                else -> "Measurements stay on this device; sessions, XP and titles sync."
            },
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign-in was refused: $it",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
            )
        }
    }
}

/**
 * "Continue with Google" through Credential Manager. Nonce direction matters:
 * the SHA-256 hash goes to Google (it hashes the ID token's nonce claim), the
 * RAW string goes to Supabase (it compares against what Google hashed).
 * Implemented per flavour: see app/src/play/.../GoogleSignIn.kt (the foss
 * flavour renders nothing — the proprietary credential libraries are absent).
 */


@Composable
private fun SignedInPanels(
    ui: AccountUi,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteCloudData: () -> Unit,
    onVisibility: (String) -> Unit,
    onSync: () -> Unit,
    onReupload: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onAccept: (String) -> Unit,
    onRequest: (String) -> Unit,
    onClaim: (String) -> Unit,
    onSkipClaim: () -> Unit,
) {
    val acct = ui.account ?: return

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            acct.displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Email styled as a house tag rather than plain grey mono.
        Text(
            acct.email,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.Emerald,
            letterSpacing = IronvellumTracking.InlineLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The email and the VISIBILITY label collided without this: adjacent
        // label-sized lines with no gap read as one overlapping block.
        Spacer(Modifier.height(18.dp))
        // Selected state reuses the hub tab pill treatment (green gradient
        // fill, bright border, dark ink) so it reads at a glance instead of
        // relying on text colour alone.
        Text(
            "VISIBILITY",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "public" to Icons.Outlined.Public,
                "friends" to Icons.Outlined.Group,
                "private" to Icons.Outlined.Lock,
            ).forEach { (value, icon) ->
                val selected = acct.visibility == value
                val shape = MaterialTheme.shapes.small
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) {
                                Brush.verticalGradient(
                                    listOf(IronvellumColors.SystemGreen, IronvellumColors.Emerald),
                                )
                            } else {
                                Brush.verticalGradient(listOf(IronvellumColors.Vault, IronvellumColors.Abyss))
                            },
                            shape,
                        )
                        .inkBorder(if (selected) IronvellumColors.EmeraldBright else IronvellumColors.Rune, shape, 1.dp)
                        .clickable { onVisibility(value) }
                        .padding(vertical = 10.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = value,
                        tint = if (selected) IronvellumColors.Abyss else IronvellumColors.InkMuted,
                    )
                    Text(
                        value.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) IronvellumColors.Abyss else IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (acct.visibility) {
                "public" -> "Every lifter on the board can read your sessions."
                "friends" -> "Only lifters on your friend list can read your sessions."
                else -> "No one but you can read your sessions."
            },
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(14.dp))
        IronvellumButton(label = "Sync Now", onClick = onSync, enabled = !ui.busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        // Sync Now skips a session whose fingerprint matches the watermark.
        // After the cloud loses rows that match is false, and this is the only
        // way back.
        NavChip(
            label = "RE-UPLOAD EVERYTHING",
            icon = Icons.Outlined.CloudUpload,
            onClick = onReupload,
            modifier = Modifier.fillMaxWidth(),
        )
        ui.lastSync?.let { outcome ->
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("Pushed ${outcome.sessions} sessions · ${outcome.sets} sets · ${outcome.titles} titles")
                    if (outcome.problems.isNotEmpty()) append(" · ${outcome.problems.size} skipped")
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = if (outcome.problems.isEmpty()) IronvellumColors.Emerald else IronvellumColors.SovereignGold,
            )
            outcome.problems.forEach { problem ->
                Text(
                    "· $problem",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "BACKUP",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(6.dp))
        // Freshness must be visible without tapping anything: a lifter has to
        // be able to tell at a glance whether they are actually protected.
        Text(
            ui.lastBackup?.let {
                "Last backup: " + DateFormat.getDateTimeInstance().format(Date(it.atMs)) +
                    " · " + formatBytes(it.bytes)
            } ?: "No cloud backup yet — tap BACK UP NOW to protect your training.",
            style = MaterialTheme.typography.labelSmall,
            color = if (ui.lastBackup != null) IronvellumColors.Emerald else IronvellumColors.SovereignGold,
        )
        Spacer(Modifier.height(8.dp))
        IronvellumButton(label = "Back Up Now", onClick = onBackup, enabled = !ui.busy, modifier = Modifier.fillMaxWidth())
        // Next to the button that failed. The shared error slot lives below
        // the allies list, several screens down, so a refused backup read as
        // a button that simply did nothing.
        ui.backupError?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
            )
        }
        Spacer(Modifier.height(6.dp))
        // The archive is the one cloud copy that must honour the device
        // promise from SessionScreen: the private note never leaves the phone.
        PrivacyRow(Icons.Outlined.Lock, IronvellumColors.SovereignGold, "Cloud backups never include your private notes — those stay on this device.")
        Spacer(Modifier.height(8.dp))
        // Same inline-confirm treatment as ERASE MY CLOUD DATA below: the
        // destructive step names exactly what it replaces before it runs.
        var confirmRestore by remember { mutableStateOf(false) }
        if (confirmRestore) {
            Text(
                "This replaces EVERYTHING logged on this phone — sessions, " +
                    "titles, skills, stats and measurements — with the cloud " +
                    "archive. Anything not in that archive is lost for good.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.DangerRed,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IronvellumButton(
                    label = "Replace my data",
                    onClick = {
                        confirmRestore = false
                        onRestore()
                    },
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmRestore = false }, enabled = !ui.busy) {
                    Text(
                        "KEEP MINE",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        } else {
            NavChip(
                label = "RESTORE FROM CLOUD",
                icon = Icons.Outlined.CloudDownload,
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ui.lastRestore?.let { outcome ->
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("Restored ${outcome.sessions} sessions · ${outcome.sets} sets · ${outcome.titles} titles")
                    if (outcome.problems.isNotEmpty()) append(" · ${outcome.problems.size} skipped")
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = if (outcome.problems.isEmpty()) IronvellumColors.Emerald else IronvellumColors.SovereignGold,
            )
        }
        Spacer(Modifier.height(14.dp))
        PrivacyRow(Icons.Outlined.Lock, IronvellumColors.SovereignGold, "Body measurements — weight, height, body fat, BMI, FFMI — never leave this device.")
        Spacer(Modifier.height(6.dp))
        PrivacyRow(Icons.Outlined.Public, IronvellumColors.Emerald, "Visibility decides who may read your sessions.")
    }
    // One-time claim prompt: visible after sign-in, but the surface around it
    // stays fully usable — skip hides it for the session, nothing nags twice.
    if (isUnclaimedHandle(acct.displayName) && !ui.claimSkipped) {
        Spacer(Modifier.height(14.dp))
        ClaimNamePanel(
            currentHandle = acct.displayName,
            error = ui.claimError,
            busy = ui.claimBusy,
            onClaim = onClaim,
            onSkip = onSkipClaim,
        )
    }

    SectionHeader("Allies")
    FriendsPanel(
        ui = ui,
        onOpenLifter = onOpenLifter,
        onAccept = onAccept,
        onRequest = onRequest,
    )

    Spacer(Modifier.height(14.dp))
    IronvellumButton(
        label = "Sever the link",
        onClick = onSignOut,
        enabled = !ui.busy,
        gold = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // Withdrawing the data has to be reachable from inside the app: a store
    // listing that reads health data must offer deletion, and until now the
    // only way out was to ask someone with database access.
    Spacer(Modifier.height(20.dp))
    var confirmDelete by remember { mutableStateOf(false) }
    Text(
        "ERASE FROM THE CLOUD",
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        letterSpacing = IronvellumTracking.InlineLabel,
        color = IronvellumColors.DangerRed,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (confirmDelete) {
            "This removes your lifter, every synced session, title and ally " +
                "from the cloud for good, and signs you out. Training logged " +
                "on this phone stays on this phone."
        } else {
            "Deletes everything you have synced. Your on-device training is untouched."
        },
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    Spacer(Modifier.height(8.dp))
    if (confirmDelete) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IronvellumButton(
                label = "Erase it all",
                onClick = {
                    confirmDelete = false
                    onDeleteCloudData()
                },
                enabled = !ui.busy,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmDelete = false }, enabled = !ui.busy) {
                Text(
                    "KEEP IT",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    } else {
        TextButton(onClick = { confirmDelete = true }, enabled = !ui.busy) {
            Text(
                "ERASE MY CLOUD DATA",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
            )
        }
    }
    ui.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.DangerRed,
        )
    }
}

/** Bytes to a short human label for the backup-freshness line. */
private fun formatBytes(bytes: Int): String =
    if (bytes < 1024) "$bytes B" else "%.1f KB".format(bytes / 1024f)

/** One icon-led privacy fact, house-styled for scanning. */
@Composable
private fun PrivacyRow(icon: ImageVector, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

/**
 * One-time "CLAIM YOUR NAME" prompt for Google users still carrying their
 * seeded Lifter#### handle. The name is how other lifters find and add you —
 * so claiming it matters, but skipping must never trap the user here.
 */
@Composable
private fun ClaimNamePanel(
    currentHandle: String,
    error: String?,
    busy: Boolean,
    onClaim: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // Mirror the server rule exactly: letters, digits, spaces; 2..24 trimmed.
    val cleaned = name.filter { it.isLetterOrDigit() || it == ' ' }.trim()
    val valid = cleaned.length in 2..24

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.SovereignGold) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Badge, contentDescription = null, tint = IronvellumColors.SovereignGold)
            Text(
                "CLAIM YOUR NAME",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "This name is how other lifters find and add you.",
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Currently: $currentHandle",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Lifter name (2–24)") },
            singleLine = true,
            enabled = !busy,
            isError = name.isNotBlank() && !valid,
            supportingText = {
                if (name.isNotBlank() && !valid) {
                    Text("2–24 characters, letters and numbers")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        IronvellumButton(
            label = "Claim",
            onClick = { onClaim(cleaned) },
            enabled = valid && !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "KEEP HUNT#### — SKIP",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) { onSkip() }
                .padding(vertical = 6.dp),
            textAlign = TextAlign.Center,
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
            )
        }
    }
}

@Composable
private fun FriendsPanel(
    ui: AccountUi,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onAccept: (String) -> Unit,
    onRequest: (String) -> Unit,
) {
    var friendName by remember { mutableStateOf("") }

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
        val incoming = ui.friends.filter { it.incoming && !it.accepted }
        val accepted = ui.friends.filter { it.accepted }

        if (incoming.isEmpty() && accepted.isEmpty() && !ui.friendsLoading) {
            Image(
                painter = painterResource(R.drawable.art_empty_allies),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(140.dp)
                    .alpha(0.55f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "No allies yet. Solo is how every lifter starts — invite one by name below.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
        }

        incoming.forEach { pending ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pending.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.Ink,
                    )
                    Text(
                        "wants to ally with you",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.SovereignGold,
                    )
                }
                IronvellumButton(label = "Accept", onClick = { onAccept(pending.userId) })
            }
            Spacer(Modifier.height(10.dp))
        }

        // Allies look like lifters everywhere else — IdentityRow, tappable.
        // Title and level now ride along on the friends read, so an ally's crest
        // shows its rarity here exactly as it does on the board and the feed.
        accepted.forEach { friend ->
            IdentityRow(
                displayName = friend.displayName,
                userId = friend.userId,
                wornTitle = friend.currentTitleId?.let { Titles.byId(it)?.name },
                level = friend.level,
                titleId = friend.currentTitleId,
                size = IdentitySize.Compact,
                onClick = { onOpenLifter(friend.userId, friend.displayName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        if (accepted.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${accepted.size} all${if (accepted.size == 1) "y" else "ies"} linked",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = friendName,
                onValueChange = { friendName = it.take(24) },
                label = { Text("Ally's lifter name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            IronvellumButton(
                label = "Invite",
                onClick = {
                    onRequest(friendName.trim())
                    friendName = ""
                },
                enabled = friendName.trim().length >= 2 && !ui.friendsLoading,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (ui.friendsLoading) "Consulting the roster…" else "Invites reach lifters by their exact name.",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

private enum class AuthMode { SIGN_IN, SIGN_UP }
