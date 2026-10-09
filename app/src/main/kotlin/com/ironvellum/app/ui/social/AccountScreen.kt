package com.ironvellum.app.ui.social

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.ui.semantics.Role
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkTextLink
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.plural
import androidx.compose.foundation.layout.Box
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
import com.ironvellum.app.ui.components.InkIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import com.ironvellum.app.ui.components.IronvellumDialog
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
import com.ironvellum.app.IronvellumApp
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
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.wholeKeyboard
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.ironvellumFieldColors
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
    // Ally requests live in Missives; the one row about them switches the pager there.
    onOpenMissives: () -> Unit = {},
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
                    onOpenMissives = onOpenMissives,
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NotConfiguredPanel() {
    Text(
        "Cloud features aren't available in this build.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
}

@Composable
private fun BusyPanel(label: String) {
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

    if (mode == AuthMode.RESET) {
        Text(
            "Reset your password",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(4.dp))
    }

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

    // Each message sits under the field it belongs to. A refusal from the
    // server goes under the last field of the form, the one just filled in.
    val serverError = error?.let {
        "${when (mode) {
            AuthMode.SIGN_IN -> "Sign-in"
            AuthMode.SIGN_UP -> "Sign-up"
            AuthMode.RESET -> "Reset"
        }} failed: $it"
    }
    val emailError = when {
        !emailValid && email.isNotEmpty() -> "Enter a valid email address."
        mode == AuthMode.RESET && !codeSent -> serverError
        else -> null
    }
    val passwordError = when {
        password.isNotEmpty() && !passwordValid -> "Password needs at least 6 characters."
        mode == AuthMode.SIGN_IN -> serverError
        else -> null
    }
    val confirmError = when {
        choosingPassword && confirm.isNotEmpty() && !confirmed -> "Passwords don't match."
        mode == AuthMode.RESET && codeSent -> serverError
        else -> null
    }
    val nameError = when {
        mode == AuthMode.SIGN_UP && displayName.isNotEmpty() && !nameValid -> "True name needs 2–24 characters."
        mode == AuthMode.SIGN_UP -> serverError
        else -> null
    }

    if (googleEnabled && mode != AuthMode.RESET) GoogleSignInButton(onToken = onGoogleSignIn)
    if (mode == AuthMode.RESET && !codeSent) {
        Text(
            "We'll email you a code to set a new password.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
    }
    OutlinedTextField(
        shape = MaterialTheme.shapes.small,
        colors = ironvellumFieldColors(),
        value = email,
        onValueChange = { email = it.trim() },
        label = { Text("Email") },
        singleLine = true,
        isError = emailError != null,
        supportingText = emailError?.let { { Text(it, color = IronvellumColors.DangerRed) } },
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
            error = passwordError,
        )
    }
    if (mode == AuthMode.SIGN_IN) {
        // Under the password field it belongs to, at the trailing edge.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IronvellumButton(
                label = "Forgot password?",
                onClick = { mode = AuthMode.RESET },
                quiet = true,
            )
        }
    }
    if (choosingPassword) {
        Spacer(Modifier.height(10.dp))
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            shown = showPassword,
            onToggleShown = { showPassword = !showPassword },
            error = confirmError,
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
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it, color = IronvellumColors.DangerRed) } },
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
    Spacer(Modifier.height(8.dp))
    // Which form this is, and the way to the other one: a link, not a switch.
    IronvellumButton(
        label = when (mode) {
            AuthMode.SIGN_IN -> "New here? Create an account"
            AuthMode.SIGN_UP -> "Have an account? Sign in"
            AuthMode.RESET -> "Back to sign in"
        },
        onClick = { mode = if (mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN },
        quiet = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (mode != AuthMode.RESET || codeSent) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Readings stay on this device; trials, XP and deeds sync.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
    notice?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.Ink,
        )
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
    error: String? = null,
) {
    OutlinedTextField(
        shape = MaterialTheme.shapes.small,
        colors = ironvellumFieldColors(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = IronvellumColors.DangerRed) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            InkIconButton(onClick = onToggleShown) {
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
    onOpenMissives: () -> Unit,
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

    SectionHeader("Circle", topPadding = 20.dp)
    CircleSection(onOpenLifter = onOpenLifter, refreshSignal = circleRefreshSignal)

    val incoming = ui.friends.filter { it.incoming && !it.accepted }
    val accepted = ui.friends.filter { it.accepted }
    if (incoming.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        RequestsRow(count = incoming.size, onClick = onOpenMissives)
    }

    Spacer(Modifier.height(14.dp))
    AddAllyPanel(loading = ui.friendsLoading, onRequest = onRequest)
    // Right under the input: a refused invite (unknown name, rate limit) is
    // the usual error here, and at the bottom of the list it went unseen.
    ui.error?.let {
        Spacer(Modifier.height(8.dp))
        SocialErrorBanner(it)
    }

    SectionHeader(if (accepted.isEmpty()) "Allies" else "Allies · ${accepted.size}", topPadding = 18.dp)
    AlliesPanel(
        accepted = accepted,
        empty = incoming.isEmpty() && accepted.isEmpty() && !ui.friendsLoading,
        onOpenLifter = onOpenLifter,
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

    // Your own plate: a gold rim and wash, and a gold ring on your crest.
    InkPanel(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 6.dp, bottom = 12.dp),
        metal = Metal.Fabled,
    ) {
        IdentityRow(
            displayName = acct.displayName,
            userId = acct.userId,
            wornTitle = titleId?.let { Titles.byId(it)?.name },
            level = profile?.let { Xp.progress(it.totalXp).level },
            size = IdentitySize.Profile,
            isMe = true,
            ring = AvatarRing.Own,
            trailing = {
                InkIconButton(onClick = onOpenAccount, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Account settings",
                        tint = IronvellumColors.InkMuted,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
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

    InkPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Badge, contentDescription = null, tint = IronvellumColors.InkMuted)
            Text(
                "Take your true name",
                style = MaterialTheme.typography.titleMedium,
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

/**
 * Invite by true name: one field with "Invite" as a link inside it, no card.
 * Invites reach lifters by their exact name.
 */
@Composable
private fun AddAllyPanel(loading: Boolean, onRequest: (String) -> Unit) {
    var friendName by remember { mutableStateOf("") }
    val canInvite = friendName.trim().length >= 2 && !loading
    val label = "Add an ally by true name"
    // A caption over a hairline field with "Invite" inside it: no box, like the mockup.
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = friendName,
                onValueChange = { friendName = it.take(24) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = IronvellumColors.Ink, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(IronvellumColors.Emerald),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                        if (friendName.isEmpty()) {
                            Text("True name", style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.InkMuted)
                        }
                        inner()
                    }
                },
            )
            InkTextLink(
                label = "Invite",
                enabled = canInvite,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                onClick = {
                    onRequest(friendName.trim())
                    friendName = ""
                },
            )
        }
        InkDivider()
    }
}

/**
 * Pending requests are answered in Missives, which handles every request type.
 * This row only counts them and switches the pager there.
 */
@Composable
private fun RequestsRow(count: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        InkDivider()
        ListRow(
            label = "$count ally ${plural(count, "request", "requests")}",
            value = "Missives",
            icon = Icons.Outlined.PersonAdd,
            onClickLabel = "Open Missives",
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
            onClick = onClick,
        )
        InkDivider()
    }
}

/** The accepted allies: unboxed 52dp rows, each opening the lifter's folio, where removing lives. */
@Composable
private fun AlliesPanel(
    accepted: List<FriendRow>,
    empty: Boolean,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
) {
    if (empty) {
        Text(
            "No allies yet. Invite one by true name above.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
    // Title and level ride along on the friends read, so an ally's crest
    // shows its rarity here exactly as it does on the board and the feed.
    accepted.forEach { friend ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpenLifter(friend.userId, friend.displayName) }
                .heightIn(min = ListRowHeight)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LifterAvatar(
                displayName = friend.displayName,
                size = 36.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    friend.displayName.ifBlank { "Ironbound" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                friend.currentTitleId?.let { Titles.byId(it)?.name }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            friend.level?.let {
                Text("Level $it", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
        }
        InkDivider()
    }
}

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }
