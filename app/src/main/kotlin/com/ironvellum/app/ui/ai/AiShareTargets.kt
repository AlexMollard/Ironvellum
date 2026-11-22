package com.ironvellum.app.ui.ai

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.chooser.ChooserResult
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import com.ironvellum.app.R
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val AI_SHARE_MIME = "text/plain"
private const val LAST_AI_PACKAGE = "last_ai_package"

internal enum class AiShareTarget(val packageName: String, @StringRes val label: Int, @StringRes val themed: Int) {
    CLAUDE("com.anthropic.claude", R.string.local_ai_share_claude, R.string.local_ai_share_claude__t),
    CHATGPT("com.openai.chatgpt", R.string.local_ai_share_chatgpt, R.string.local_ai_share_chatgpt__t),
}

private fun Context.sharePreferences() = getSharedPreferences("ai_sharing", Context.MODE_PRIVATE)

@Suppress("DEPRECATION") // The int-flags API also works on Android 10–12.
private fun AiShareTarget.isEligible(context: Context) =
    context.packageManager.resolveActivity(
        Intent(Intent.ACTION_SEND).setType(AI_SHARE_MIME).setPackage(packageName),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.let { it.exported && it.enabled && it.packageName == packageName } == true

internal fun eligibleAiTargets(
    context: Context,
    installed: List<AiShareTarget> = AiShareTarget.entries.filter { it.isEligible(context) },
): List<AiShareTarget> {
    val last = context.sharePreferences().getString(LAST_AI_PACKAGE, null)
    return installed.sortedBy { it.packageName != last }
}

private fun Context.rememberAiTarget(target: AiShareTarget) {
    sharePreferences().edit { putString(LAST_AI_PACKAGE, target.packageName) }
}

/** The chooser reports selection only. The recipient handles the attachment and any sending. */
class AiShareSelectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        rememberAiSelection(context, intent, eligibleAiTargets(context))
    }
}

internal fun rememberAiSelection(context: Context, intent: Intent, eligible: List<AiShareTarget>) {
    val chosen = (if (Build.VERSION.SDK_INT >= 35) {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOOSER_RESULT, ChooserResult::class.java)
            ?.selectedComponent
    } else null)
        ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
    eligible.firstOrNull { it.packageName == chosen?.packageName }
        ?.let { context.rememberAiTarget(it) }
}

internal suspend fun shareAiPackage(context: Context, title: String, target: AiShareTarget?, bytes: ByteArray) {
    val (name, uri) = withContext(Dispatchers.IO) {
        val name = AiTrainingPackage.fileName(bytes)
        val file = stageAiShare(File(context.cacheDir, "exports"), name, bytes)
        name to FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = AI_SHARE_MIME
        putExtra(Intent.EXTRA_TITLE, name)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (target != null && target.isEligible(context)) {
        try {
            context.startActivity(Intent(send).setPackage(target.packageName))
            context.rememberAiTarget(target)
            return
        } catch (_: ActivityNotFoundException) {
            // The app may have been removed, disabled or updated after resolution.
        } catch (_: SecurityException) {
            // The resolved activity can become inaccessible before launch.
        }
    }
    val selection = PendingIntent.getBroadcast(
        context, UUID.randomUUID().hashCode(), Intent(context, AiShareSelectionReceiver::class.java),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
    )
    context.startActivity(Intent.createChooser(send, title, selection.intentSender))
}
