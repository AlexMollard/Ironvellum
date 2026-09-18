package com.monarch.app.ui.social

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
import com.monarch.app.R
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
import com.monarch.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.Account
import com.monarch.app.data.cloud.AccountRepository
import com.monarch.app.data.cloud.Cloud
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.cloud.FriendRow
import com.monarch.app.data.cloud.SyncOutcome
import com.monarch.app.data.cloud.isUnclaimedHandle
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.InkSpinner
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.monarchAccount
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.components.InkSegmented
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.domain.Titles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton

/** One honest snapshot of the account gate: which panel to show and why. */
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
            // real reason or the gate sits blank with no explanation.
            accountRepo.restore().onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            setBusy(false)
        }
        // Keep the UI in step with the repository's own session state.
        viewModelScope.launch {
            accountRepo.account.collect { acct ->
                _ui.value = _ui.value.copy(account = acct)
                if (acct != null) refreshFriends()
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
                // server confirmed the rows are gone.
                .onSuccess {
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
    // real hunter route in MonarchNav.kt.
    onOpenHunter: (userId: String, displayName: String) -> Unit = { _, _ -> },
    viewModel: AccountViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AccountViewModel(monarchAccount(), monarchCloudSync()) }
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
                    containerColor = MonarchColors.Vault,
                    color = MonarchColors.EmeraldBright,
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
                    onOpenHunter = onOpenHunter,
                    onSignOut = viewModel::signOut,
                    onDeleteCloudData = viewModel::deleteCloudData,
                    onVisibility = viewModel::setVisibility,
                    onSync = viewModel::syncNow,
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
        GatewayTitle()

        when {
            !ui.configured -> NotConfiguredPanel()
            ui.busy && ui.account == null -> BusyPanel("Linking to the System…")
            // Signed OUT with a configured cloud: this is the gate itself.
            // Losing this branch left GATEWAY rendering nothing but its own
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

/** Screen title shared by the signed-in and gate layouts. */
@Composable
private fun GatewayTitle() {
    Text(
        "GATEWAY",
        style = MaterialTheme.typography.labelLarge,
        fontFamily = ChakraPetch,
        color = MonarchColors.InkMuted,
        letterSpacing = MonarchTracking.ScreenTitle,
    )
    Text(
        "Cloud link for hunters",
        style = MaterialTheme.typography.labelLarge,
        fontFamily = ChakraPetch,
        color = MonarchColors.SystemGreen,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NotConfiguredPanel() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MonarchColors.DangerRed)
            Text(
                "CLOUD LINK OFFLINE",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.DangerRed,
                letterSpacing = MonarchTracking.InlineLabel,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "No Supabase endpoint is configured on this device. The gate cannot open — ask the guild to build with SUPABASE_URL and SUPABASE_KEY set.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun BusyPanel(label: String) {
    SystemWindow(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkSpinner()
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
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

    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Rune) {
        if (googleEnabled) {
            GoogleGateButton(onToken = onGoogleSignIn)
            Spacer(Modifier.height(14.dp))
        }
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Hunter email") },
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
                label = { Text("Hunter name (2–24)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        MonarchButton(
            label = if (mode == AuthMode.SIGN_IN) "Enter the Gate" else "Awaken",
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
                    "Hunter names run 2–24 characters."
                else -> "Measurements stay on this device; sessions, XP and titles sync."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "The gate refused: $it",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.DangerRed,
            )
        }
    }
}

/**
 * "Continue with Google" through Credential Manager. Nonce direction matters:
 * the SHA-256 hash goes to Google (it hashes the ID token's nonce claim), the
 * RAW string goes to Supabase (it compares against what Google hashed).
 */
@Composable
private fun GoogleGateButton(onToken: (idToken: String, rawNonce: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inFlight by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column {
        MonarchButton(
            label = "Continue with Google",
            modifier = Modifier.fillMaxWidth(),
            // Same busy-guard as the other MonarchButtons ("Sync Now",
            // "Sever the link"): a second tap mid-flow would fire a parallel
            // Credential Manager request and duplicate the sign-in.
            enabled = !inFlight,
            onClick = {
                if (inFlight) return@MonarchButton
                inFlight = true
                scope.launch {
                    localError = null
                    try {
                        // Raw nonce first; only its hash ever reaches Google.
                        val rawNonce = BigInteger(130, SecureRandom()).toString(36)
                        val hashedNonce = MessageDigest.getInstance("SHA-256")
                            .digest(rawNonce.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        val option = GetGoogleIdOption.Builder()
                            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                            .setFilterByAuthorizedAccounts(false)
                            .setNonce(hashedNonce)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(option)
                            .build()
                        val response = CredentialManager.create(context)
                            .getCredential(context, request)
                        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
                        onToken(idToken, rawNonce)
                    } catch (_: GetCredentialCancellationException) {
                        // The hunter dismissed the sheet — not an error.
                    } catch (_: NoCredentialException) {
                        localError = "No Google account found on this device — use the email gate below."
                    } catch (e: GetCredentialException) {
                        // Credential Manager messages are API internals —
                        // they never reach the hunter verbatim.
                        localError = Cloud.explain(e)
                    } catch (e: Exception) {
                        localError = Cloud.explain(e)
                    } finally {
                        inFlight = false
                    }
                }
            },
        )
        localError?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.DangerRed,
            )
        }
    }
}

@Composable
private fun SignedInPanels(
    ui: AccountUi,
    onOpenHunter: (userId: String, displayName: String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteCloudData: () -> Unit,
    onVisibility: (String) -> Unit,
    onSync: () -> Unit,
    onAccept: (String) -> Unit,
    onRequest: (String) -> Unit,
    onClaim: (String) -> Unit,
    onSkipClaim: () -> Unit,
) {
    val acct = ui.account ?: return

    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Emerald) {
        Text(
            acct.displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Email styled as a house tag rather than plain grey mono.
        Text(
            acct.email,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.Emerald,
            letterSpacing = MonarchTracking.InlineLabel,
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
            color = MonarchColors.SystemGreen,
            letterSpacing = MonarchTracking.InlineLabel,
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
                                    listOf(MonarchColors.SystemGreen, MonarchColors.Emerald),
                                )
                            } else {
                                Brush.verticalGradient(listOf(MonarchColors.Vault, MonarchColors.Abyss))
                            },
                            shape,
                        )
                        .inkBorder(if (selected) MonarchColors.EmeraldBright else MonarchColors.Rune, shape, 1.dp)
                        .clickable { onVisibility(value) }
                        .padding(vertical = 10.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = value,
                        tint = if (selected) MonarchColors.Abyss else MonarchColors.InkMuted,
                    )
                    Text(
                        value.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MonarchColors.Abyss else MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (acct.visibility) {
                "public" -> "Every hunter on the board can read your sessions."
                "friends" -> "Only hunters on your friend list can read your sessions."
                else -> "No one but you can read your sessions."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(14.dp))
        MonarchButton(label = "Sync Now", onClick = onSync, enabled = !ui.busy, modifier = Modifier.fillMaxWidth())
        ui.lastSync?.let { outcome ->
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("Pushed ${outcome.sessions} sessions · ${outcome.sets} sets · ${outcome.titles} titles")
                    if (outcome.problems.isNotEmpty()) append(" · ${outcome.problems.size} skipped")
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = if (outcome.problems.isEmpty()) MonarchColors.Emerald else MonarchColors.SovereignGold,
            )
            outcome.problems.forEach { problem ->
                Text(
                    "· $problem",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
                )
            }
        }
        // Privacy facts stay load-bearing; scanning beats a paragraph.
        PrivacyRow(Icons.Outlined.Lock, MonarchColors.SovereignGold, "Body measurements — weight, height, body fat, BMI, FFMI — never leave this device.")
        Spacer(Modifier.height(6.dp))
        PrivacyRow(Icons.Outlined.Public, MonarchColors.Emerald, "Visibility decides who may read your sessions.")
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
        onOpenHunter = onOpenHunter,
        onAccept = onAccept,
        onRequest = onRequest,
    )

    Spacer(Modifier.height(14.dp))
    MonarchButton(
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
        letterSpacing = MonarchTracking.InlineLabel,
        color = MonarchColors.DangerRed,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (confirmDelete) {
            "This removes your hunter, every synced session, title and ally " +
                "from the cloud for good, and signs you out. Training logged " +
                "on this phone stays on this phone."
        } else {
            "Deletes everything you have synced. Your on-device training is untouched."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MonarchColors.InkMuted,
    )
    Spacer(Modifier.height(8.dp))
    if (confirmDelete) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonarchButton(
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
                    color = MonarchColors.InkMuted,
                )
            }
        }
    } else {
        TextButton(onClick = { confirmDelete = true }, enabled = !ui.busy) {
            Text(
                "ERASE MY CLOUD DATA",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.DangerRed,
            )
        }
    }
    ui.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.DangerRed,
        )
    }
}

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
            color = MonarchColors.InkMuted,
        )
    }
}

/**
 * One-time "CLAIM YOUR NAME" prompt for Google users still carrying their
 * seeded Hunter#### handle. The name is how other hunters find and add you —
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

    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Badge, contentDescription = null, tint = MonarchColors.SovereignGold)
            Text(
                "CLAIM YOUR NAME",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.Ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "This name is how other hunters find and add you.",
            style = MaterialTheme.typography.labelMedium,
            color = MonarchColors.InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Currently: $currentHandle",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Hunter name (2–24)") },
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
        MonarchButton(
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
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
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
                color = MonarchColors.DangerRed,
            )
        }
    }
}

@Composable
private fun FriendsPanel(
    ui: AccountUi,
    onOpenHunter: (userId: String, displayName: String) -> Unit,
    onAccept: (String) -> Unit,
    onRequest: (String) -> Unit,
) {
    var friendName by remember { mutableStateOf("") }

    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Rune) {
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
                "No allies yet. Solo is how every hunter starts — invite one by name below.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
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
                        color = MonarchColors.Ink,
                    )
                    Text(
                        "wants to ally with you",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.SovereignGold,
                    )
                }
                MonarchButton(label = "Accept", onClick = { onAccept(pending.userId) })
            }
            Spacer(Modifier.height(10.dp))
        }

        // Allies look like hunters everywhere else — IdentityRow, tappable.
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
                onClick = { onOpenHunter(friend.userId, friend.displayName) },
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
                color = MonarchColors.InkMuted,
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
                label = { Text("Ally's hunter name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            MonarchButton(
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
            if (ui.friendsLoading) "Consulting the roster…" else "Invites reach hunters by their exact name.",
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
        )
    }
}

private enum class AuthMode { SIGN_IN, SIGN_UP }
