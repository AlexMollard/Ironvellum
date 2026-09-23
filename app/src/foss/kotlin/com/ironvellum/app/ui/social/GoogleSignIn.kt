package com.ironvellum.app.ui.social

import androidx.compose.runtime.Composable

/**
 * Foss flavour: the proprietary Google credential libraries are absent, so
 * the Google sign-in button does not exist here. Same signature as the play
 * implementation so AccountScreen is flavour-agnostic.
 */
@Composable
internal fun GoogleSignInButton(onToken: (idToken: String, rawNonce: String) -> Unit) {
    // Renders nothing. The parameter is unused by design.
    @Suppress("UNUSED_PARAMETER") onToken
}
