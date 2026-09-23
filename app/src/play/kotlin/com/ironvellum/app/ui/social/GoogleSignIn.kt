package com.ironvellum.app.ui.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * "Continue with Google" through Credential Manager. Nonce direction matters:
 * the SHA-256 hash goes to Google (it hashes the ID token's nonce claim), the
 * RAW string goes to Supabase (it compares against what Google hashed).
 */
@Composable
internal fun GoogleSignInButton(onToken: (idToken: String, rawNonce: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inFlight by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column {
        IronvellumButton(
            label = "Continue with Google",
            modifier = Modifier.fillMaxWidth(),
            // Same busy-guard as the other IronvellumButtons ("Sync Now",
            // "Sever the link"): a second tap mid-flow would fire a parallel
            // Credential Manager request and duplicate the sign-in.
            enabled = !inFlight,
            onClick = {
                if (inFlight) return@IronvellumButton
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
                        // The lifter dismissed the sheet — not an error.
                    } catch (_: NoCredentialException) {
                        localError = "No Google account found on this device — use the email form below."
                    } catch (e: GetCredentialException) {
                        // Credential Manager messages are API internals —
                        // they never reach the lifter verbatim.
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
                color = IronvellumColors.DangerRed,
            )
        }
    }
}
