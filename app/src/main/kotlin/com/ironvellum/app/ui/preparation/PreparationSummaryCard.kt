package com.ironvellum.app.ui.preparation

import androidx.compose.runtime.Composable
import com.ironvellum.app.domain.SessionPreparation

/** Only the final sealed summary hosts this; reward pages stay unchanged. */
@Composable
fun PreparationSummaryCard(state: SessionPreparation, onBegin: () -> Unit, onChange: () -> Unit, onSkip: () -> Unit, onView: () -> Unit = onBegin) =
    PreparationOfferCard(state, onBegin, onChange, onSkip, onView)
