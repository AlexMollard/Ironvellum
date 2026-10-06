package com.ironvellum.app.ui.social

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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import com.ironvellum.app.IronvellumApp
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
import com.ironvellum.app.data.cloud.BlockedLifter
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.SyncOutcome
import com.ironvellum.app.data.cloud.SignUpOutcome
import com.ironvellum.app.data.cloud.isUnclaimedHandle
import com.ironvellum.app.data.cloud.signUpNamePrefill
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.wholeKeyboard
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.ironvellumFieldColors
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.DecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One honest snapshot of the account state: which panel to show and why. */
data class AccountUi(
    // Snapshot for ViewModel logic; the SCREEN collects Cloud.config so a
    // backend switch redraws here without a restart.
    val configured: Boolean = Cloud.config.value != null,
    val account: Account? = null,
    val busy: Boolean = false,
    /** The real server/network reason, verbatim — never a generic "failed". */
    val error: String? = null,
    /** Sign-up succeeded but the server wants the email confirmed first. */
    val notice: String? = null,
    /** The address the last reset code went to; the form then asks for it. */
    val resetCodeSentTo: String? = null,
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
    /** Lifters this account blocked; blocked_name is kept server-side because their profile turns unreadable. */
    val blocked: List<BlockedLifter> = emptyList(),
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
                    refreshBlocked()
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
            _ui.value = _ui.value.copy(error = null, notice = null)
            accountRepo.signIn(email, password)
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null, notice = null)
            accountRepo.signUp(email, password, displayName)
                .onSuccess { outcome ->
                    if (outcome is SignUpOutcome.ConfirmEmail) {
                        _ui.value = _ui.value.copy(
                            notice = "Account created — open the link sent to $email, then sign in.",
                        )
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }


    fun sendResetCode(email: String) {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null, notice = null)
            accountRepo.sendPasswordReset(email)
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        resetCodeSentTo = email,
                        notice = "If $email has an account, a code is on its way.",
                    )
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            setBusy(true)
            _ui.value = _ui.value.copy(error = null, notice = null)
            accountRepo.resetPassword(email, code, newPassword)
                .onSuccess { _ui.value = _ui.value.copy(resetCodeSentTo = null) }
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
                    _ui.value = _ui.value.copy(lastSync = null, friends = emptyList(), blocked = emptyList())
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
                    _ui.value = _ui.value.copy(lastSync = null, friends = emptyList(), blocked = emptyList())
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

    /** Ends an alliance; the row leaves the list only once the server confirms. */
    fun removeFriend(userId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(error = null)
            cloudSync.removeFriend(userId)
                .onSuccess { _ui.value = _ui.value.copy(friends = _ui.value.friends.filter { it.userId != userId }) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
        }
    }

    fun refreshBlocked(force: Boolean = false) {
        viewModelScope.launch {
            cloudSync.blocked(force = force)
                .onSuccess { _ui.value = _ui.value.copy(blocked = it) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
        }
    }

    fun unblock(userId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(error = null)
            cloudSync.unblock(userId)
                .onSuccess { _ui.value = _ui.value.copy(blocked = _ui.value.blocked.filter { it.userId != userId }) }
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
    // Same no-op default reasoning; SocialScreen wires the ACCOUNT route.
    onOpenAccount: () -> Unit = {},
    // Pushed from Settings rather than hosted as the Allies tab: the signed-out
    // form then carries its own BACK header.
    pushed: Boolean = false,
    viewModel: AccountViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AccountViewModel(ironvellumAccount(), ironvellumCloudSync()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // The backend can change at runtime (Settings → CLOUD); collecting the
    // flow here redraws the account surface — including the re-evaluated
    // Cloud.googleConfigured below — without an app restart.
    val cloudConfigured = Cloud.config.collectAsStateWithLifecycle().value != null

    // One refresh idiom: pull-to-refresh, like the feed and board. The roster
    // text link is gone. Hosting it here means the signed-in surface owns its
    // own scroll container — a PullToRefreshBox nested inside a verticalScroll
    // Column never sees the drag.
    if (cloudConfigured && ui.account != null) {
        val pullState = rememberPullToRefreshState()
        // A pull also forces a fresh circle read: the circle section owns its
        // own view model, so the pull reaches it as a bumped counter.
        var circleRefresh by remember { mutableIntStateOf(0) }
        PullToRefreshBox(
            isRefreshing = ui.friendsLoading,
            onRefresh = {
                viewModel.refreshFriends()
                viewModel.refreshBlocked(force = true)
                circleRefresh++
            },
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
            // SocialScreen owns the margins and the top gap, so this tab starts
            // at the same spot under the pills as the others.
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                SignedInPanels(
                    ui = ui,
                    onOpenLifter = onOpenLifter,
                    onOpenAccount = onOpenAccount,
                    onAccept = viewModel::acceptFriend,
                    onDecline = viewModel::removeFriend,
                    onRemoveAlly = viewModel::removeFriend,
                    onRequest = viewModel::requestFriend,
                    onClaim = viewModel::claimName,
                    onSkipClaim = viewModel::skipClaim,
                    circleRefreshSignal = circleRefresh,
                )
                // Clears the bottom nav bar: 28.dp left the last ally row half
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
        Spacer(Modifier.height(if (pushed) 8.dp else 20.dp))
        AccountTitle(pushed, onBack)

        when {
            !cloudConfigured -> NotConfiguredPanel()
            ui.busy && ui.account == null -> BusyPanel("Linking to the Ledger…")
            // Signed OUT with a configured cloud: this is the sign-in form itself.
            // Losing this branch left ACCOUNT rendering nothing but its own
            // title, so there was no way to sign in from anywhere in the app.
            else -> AuthPanels(
                error = ui.error,
                notice = ui.notice,
                busy = ui.busy,
                googleEnabled = Cloud.googleConfigured,
                onGoogleSignIn = viewModel::signInWithGoogle,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
                resetCodeSentTo = ui.resetCodeSentTo,
                onSendResetCode = viewModel::sendResetCode,
                onResetPassword = viewModel::resetPassword,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Screen title shared by the signed-in and sign-in layouts. */
@Composable
private fun AccountTitle(pushed: Boolean, onBack: () -> Unit) {
    if (pushed) {
        PushedHeader("ACCOUNT", onBack)
    } else {
        Text(
            "ACCOUNT",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.ScreenTitle,
        )
        Spacer(Modifier.height(6.dp))
    }
    Text(
        "Cloud link for the Ironbound",
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
    notice: String?,
    busy: Boolean,
    googleEnabled: Boolean,
    onGoogleSignIn: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    resetCodeSentTo: String?,
    onSendResetCode: (String) -> Unit,
    onResetPassword: (email: String, code: String, newPassword: String) -> Unit,
) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    // A login wall with no reason to sign in reads as a requirement. Say it
    // is optional and what it adds before asking for anything.
    Text(
        "Optional: everything else works without an account. Sign in to back up your trials, " +
            "add allies, join a circle and stand in the Reckoning.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.Ink,
    )
    Spacer(Modifier.height(12.dp))

    // Segmented auth-mode switch: the shared inked picker. Password reset is
    // reached from SIGN IN, so it keeps that segment lit.
    InkSegmented(
        options = listOf(AuthMode.SIGN_IN to "SIGN IN", AuthMode.SIGN_UP to "SIGN UP"),
        selected = if (mode == AuthMode.RESET) AuthMode.SIGN_IN else mode,
        onPick = { mode = it },
    )

    Spacer(Modifier.height(14.dp))

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    // One toggle shows both password fields: checking they match is the point.
    var showPassword by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    // The name already on this phone is the natural true name; it fills an
    // empty field once the profile loads, and never overwrites typing.
    val app = LocalContext.current.applicationContext as IronvellumApp
    val localName by remember(app) { app.repository.observeProfile() }
        .collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(localName?.name) {
        if (displayName.isEmpty()) displayName = signUpNamePrefill(localName?.name)
    }
    var code by remember { mutableStateOf("") }

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val passwordValid = password.length >= 6
    val nameValid = displayName.trim().length in 2..24
    // Tied to the address it went to: editing the email starts over.
    val codeSent = mode == AuthMode.RESET && resetCodeSentTo != null && resetCodeSentTo == email
    val codeValid = code.length in 6..10
    // A new password is typed twice: a typo there locks the lifter out of an
    // account they only just made.
    val choosingPassword = mode == AuthMode.SIGN_UP || codeSent
    val confirmed = !choosingPassword || confirm == password
    val canSubmit = !busy && emailValid && when (mode) {
        AuthMode.SIGN_IN -> passwordValid
        AuthMode.SIGN_UP -> passwordValid && confirmed && nameValid
        AuthMode.RESET -> !codeSent || (codeValid && passwordValid && confirmed)
    }

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
        if (googleEnabled && mode != AuthMode.RESET) GoogleSignInButton(onToken = onGoogleSignIn)
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            colors = ironvellumFieldColors(),
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        if (codeSent) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = code,
                onValueChange = { typed -> code = DecimalInput.sanitizeCode(typed, 10) },
                label = { Text("Code from email") },
                singleLine = true,
                keyboardOptions = wholeKeyboard(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (mode != AuthMode.RESET || codeSent) {
            Spacer(Modifier.height(10.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = if (mode == AuthMode.RESET) "New password" else "Password",
                shown = showPassword,
                onToggleShown = { showPassword = !showPassword },
            )
        }
        if (choosingPassword) {
            Spacer(Modifier.height(10.dp))
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Confirm password",
                shown = showPassword,
                onToggleShown = { showPassword = !showPassword },
                isError = confirm.isNotEmpty() && !confirmed,
            )
        }
        if (mode == AuthMode.SIGN_UP) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = displayName,
                onValueChange = { displayName = it.take(24) },
                label = { Text("True name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        IronvellumButton(
            label = when (mode) {
                AuthMode.SIGN_IN -> "Sign in"
                AuthMode.SIGN_UP -> "Create account"
                AuthMode.RESET -> if (codeSent) "Set new password" else "Email me a code"
            },
            onClick = {
                when (mode) {
                    AuthMode.SIGN_IN -> onSignIn(email, password)
                    AuthMode.SIGN_UP -> onSignUp(email, password, displayName.trim())
                    AuthMode.RESET ->
                        if (codeSent) onResetPassword(email, code, password) else onSendResetCode(email)
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode != AuthMode.SIGN_UP) {
            Spacer(Modifier.height(8.dp))
            IronvellumButton(
                label = if (mode == AuthMode.SIGN_IN) "Forgot password?" else "Back to sign in",
                onClick = { mode = if (mode == AuthMode.SIGN_IN) AuthMode.RESET else AuthMode.SIGN_IN },
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !emailValid && email.isNotEmpty() -> "Enter a valid email address."
                password.isNotEmpty() && !passwordValid -> "Password needs at least 6 characters."
                choosingPassword && confirm.isNotEmpty() && !confirmed -> "Passwords don't match."
                mode == AuthMode.SIGN_UP && displayName.isNotEmpty() && !nameValid ->
                    "True name needs 2–24 characters."
                mode == AuthMode.RESET && !codeSent -> "We'll email you a code to set a new password."
                else -> "Readings stay on this device; trials, XP and deeds sync."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.EmeraldBright,
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "${when (mode) {
                    AuthMode.SIGN_IN -> "Sign-in"
                    AuthMode.SIGN_UP -> "Sign-up"
                    AuthMode.RESET -> "Reset"
                }} failed: $it",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
            )
        }
    }
}

/** Password input with a show/hide toggle; [shown] is shared by the password and its confirmation. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    shown: Boolean,
    onToggleShown: () -> Unit,
    isError: Boolean = false,
) {
    OutlinedTextField(
        shape = MaterialTheme.shapes.small,
        colors = ironvellumFieldColors(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleShown) {
                Icon(
                    if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (shown) "Hide password" else "Show password",
                    tint = IronvellumColors.InkMuted,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * "Continue with Google" through Credential Manager. Nonce direction matters:
 * the SHA-256 hash goes to Google (it hashes the ID token's nonce claim), the
 * RAW string goes to Supabase (it compares against what Google hashed).
 * Implemented per flavour: see app/src/play/.../GoogleSignIn.kt (the foss
 * flavour renders nothing — the proprietary credential libraries are absent).
 */

/**
 * The ALLIES tab: social only. Account settings live behind the gear on the
 * profile header (AccountSettingsScreen) - this tab used to carry sync,
 * backup, privacy notes and sign-out between the lifter and their allies.
 */
@Composable
private fun SignedInPanels(
    ui: AccountUi,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onOpenAccount: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRemoveAlly: (String) -> Unit,
    onRequest: (String) -> Unit,
    onClaim: (String) -> Unit,
    onSkipClaim: () -> Unit,
    circleRefreshSignal: Int,
) {
    val acct = ui.account ?: return

    ProfileHeader(acct = acct, onOpenAccount = onOpenAccount)
    // One-time claim prompt: visible after sign-in, but the surface around it
    // stays fully usable - skip hides it for the session, nothing nags twice.
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

    SectionHeader("Circle")
    CircleSection(onOpenLifter = onOpenLifter, refreshSignal = circleRefreshSignal)

    Spacer(Modifier.height(14.dp))
    AddAllyPanel(loading = ui.friendsLoading, onRequest = onRequest)
    // Right under the input: a refused invite (unknown name, rate limit) is
    // the usual error here, and at the bottom of the list it went unseen.
    ui.error?.let {
        Spacer(Modifier.height(8.dp))
        SocialErrorBanner(it)
    }

    val incoming = ui.friends.filter { it.incoming && !it.accepted }
    val accepted = ui.friends.filter { it.accepted }
    if (incoming.isNotEmpty()) {
        SectionHeader("Requests")
        RequestsPanel(incoming = incoming, onAccept = onAccept, onDecline = onDecline)
    }

    SectionHeader(if (accepted.isEmpty()) "Allies" else "Allies · ${accepted.size}")
    AlliesPanel(
        accepted = accepted,
        empty = incoming.isEmpty() && accepted.isEmpty() && !ui.friendsLoading,
        onOpenLifter = onOpenLifter,
        onRemoveAlly = onRemoveAlly,
    )
}

/**
 * The lifter's own card: crest, name, level and worn title from local state
 * (the profile is on this phone, no cloud read), the visibility they publish
 * at, and the gear to the account screen.
 */
@Composable
private fun ProfileHeader(acct: Account, onOpenAccount: () -> Unit) {
    val app = LocalContext.current.applicationContext as IronvellumApp
    val profile by remember(app) { app.repository.observeProfile() }
        .collectAsStateWithLifecycle(initialValue = null)
    val titleId = profile?.currentTitleId

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        IdentityRow(
            displayName = acct.displayName,
            userId = acct.userId,
            wornTitle = titleId?.let { Titles.byId(it)?.name },
            level = profile?.let { Xp.progress(it.totalXp).level },
            size = IdentitySize.Standard,
            isMe = true,
            titleId = titleId,
            trailing = {
                IconButton(onClick = onOpenAccount, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Account settings",
                        tint = IronvellumColors.InkMuted,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        VisibilityChip(acct.visibility)
    }
}

/** Read-only: changing visibility is an account setting, one gear tap away. */
@Composable
private fun VisibilityChip(visibility: String) {
    val (icon, label) = when (visibility) {
        "public" -> Icons.Outlined.Public to "Public"
        "friends" -> Icons.Outlined.Group to "Allies only"
        else -> Icons.Outlined.Lock to "Private"
    }
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}


/**
 * One-time "TAKE YOUR TRUE NAME" prompt for Google users still carrying their
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
                "TAKE YOUR TRUE NAME",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "This true name is how other Ironbound find and add you.",
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
            colors = ironvellumFieldColors(),
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("True name (2–24)") },
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
            label = "Take",
            onClick = { onClaim(cleaned) },
            enabled = valid && !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        IronvellumButton(
            label = "Skip for now",
            onClick = onSkip,
            enabled = !busy,
            quiet = true,
            modifier = Modifier.fillMaxWidth(),
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

/** Compact invite-by-name row; invites reach lifters by their exact name. */
@Composable
private fun AddAllyPanel(loading: Boolean, onRequest: (String) -> Unit) {
    var friendName by remember { mutableStateOf("") }
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = friendName,
                onValueChange = { friendName = it.take(24) },
                label = { Text("Add an ally by true name") },
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
                enabled = friendName.trim().length >= 2 && !loading,
            )
        }
    }
}

/** Incoming requests. Decline is the same delete as removing an ally: the row goes either way. */
@Composable
private fun RequestsPanel(
    incoming: List<FriendRow>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.SovereignGold) {
        incoming.forEachIndexed { index, pending ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                RowAction("DECLINE", IronvellumColors.InkMuted) { onDecline(pending.userId) }
                IronvellumButton(label = "Accept", onClick = { onAccept(pending.userId) })
            }
        }
    }
}

@Composable
private fun AlliesPanel(
    accepted: List<FriendRow>,
    empty: Boolean,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onRemoveAlly: (String) -> Unit,
) {
    var confirmRemove by remember { mutableStateOf<FriendRow?>(null) }

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
        if (empty) {
            Image(
                painter = painterResource(R.drawable.art_empty_allies),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(110.dp)
                    .alpha(0.55f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No allies yet. Invite one by true name above.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }

        // Allies look like lifters everywhere else - IdentityRow, tappable.
        // Title and level ride along on the friends read, so an ally's crest
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
                trailing = { RowAction("REMOVE", IronvellumColors.InkMuted) { confirmRemove = friend } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
    }

    confirmRemove?.let { friend ->
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${friend.displayName} as an ally?") },
            text = { Text("Their allies-only trials leave your tidings. Either of you can send a new request later.") },
            confirmButton = {
                IronvellumButton(label = "Remove", onClick = {
                    confirmRemove = null
                    onRemoveAlly(friend.userId)
                }, danger = true)
            },
            dismissButton = {
                IronvellumButton(label = "Keep", onClick = { confirmRemove = null }, quiet = true)
            },
        )
    }
}

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }
