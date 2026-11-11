package com.ironvellum.app.ui.social

import com.ironvellum.app.data.cloud.Cloud
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The account and SDK instance whose private results a social screen may display. */
internal data class SocialOwner(val userId: String?, val backend: Any?)

internal fun currentSocialOwner(userId: String?): SocialOwner =
    SocialOwner(userId, if (Cloud.config.value != null) Cloud.client() else null)

internal fun socialOwners(userIds: Flow<String?>, currentUserId: () -> String?): Flow<SocialOwner> =
    combine(userIds, Cloud.config) { _, _ -> currentSocialOwner(currentUserId()) }.distinctUntilChanged()
