package com.ironvellum.app.data

import com.ironvellum.app.domain.BlockKey
import com.ironvellum.app.domain.blockKey
import kotlinx.coroutines.flow.first

/** The first block the session holds of [exerciseId]: what a screen would hand the repository when its card is tapped. */
internal suspend fun Repository.blockOf(sessionId: Long, exerciseId: Long): BlockKey =
    observeSessionSets(sessionId).first().first { it.exerciseId == exerciseId }.blockKey
