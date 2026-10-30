package com.ironvellum.app.ui.stats

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.PhotoPrefs
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.PhotoKind
import com.ironvellum.app.domain.PhotoRules
import com.ironvellum.app.domain.Pose
import com.ironvellum.app.domain.ProgressPhoto
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.PhotoSourceTiles
import com.ironvellum.app.ui.components.PhotoFrame
import com.ironvellum.app.ui.components.PhotoRemoveDialog
import com.ironvellum.app.ui.components.PhotoSource
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.rememberPhotoSource
import com.ironvellum.app.ui.components.rememberZoneId
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgressPhotosViewModel(private val repo: Repository, private val appContext: Context) : ViewModel() {
    /** Every kept photo; the screen reads the pose ones. */
    val photos: StateFlow<List<ProgressPhoto>> = repo.photoLibrary.photos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The pose being written, so its slot can say so; null when nothing is. */
    var saving by mutableStateOf<Pose?>(null)
        private set

    var failed by mutableStateOf(false)
        private set

    /** The picture for a row, or null when this phone has none (a restore that has not been given its folder yet). */
    fun fileOf(photo: ProgressPhoto): File? = repo.photoLibrary.store?.file(photo.fileName)?.takeIf { it.isFile }

    fun save(pose: Pose, week: Long, uri: Uri) {
        if (saving != null) return
        saving = pose
        failed = false
        viewModelScope.launch {
            runCatching { repo.photoLibrary.savePose(appContext, uri, week, pose) }.onFailure { failed = true }
            saving = null
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.photoLibrary.delete(id) }
    }
}

/**
 * Stats > Body > Progress photos: the weekday the poses are asked on, this week's three poses (front, side, back), and
 * any two weeks compared pose by pose with a draggable divider. Photos live only on this phone and in the lifter's
 * backup folder. At 600dp and wider the pictures grow.
 */
@Composable
fun ProgressPhotosScreen(
    onBack: () -> Unit,
    viewModel: ProgressPhotosViewModel = run {
        val appContext = LocalContext.current.applicationContext
        viewModel(factory = viewModelFactory { initializer { ProgressPhotosViewModel(ironvellumRepository(), appContext) } })
    },
) {
    val all by viewModel.photos.collectAsStateWithLifecycle()
    val zone = rememberZoneId()
    val today = remember(zone) { LocalDate.now(zone) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val poses = remember(all) { all.filter { it.kind == PhotoKind.POSE && it.pose != null } }
    val thisWeek = remember(today) { PhotoRules.weekStart(today) }
    val bySlot = remember(poses) { poses.associateBy { it.day to it.pose!! } }
    val weeks = remember(poses) { poses.map { it.day }.distinct().sortedDescending().map(LocalDate::ofEpochDay) }
    // The two weeks compared: what the lifter picked (kept across rotation), else the last two weeks with poses.
    var leftPick by rememberSaveable { mutableLongStateOf(NONE) }
    var rightPick by rememberSaveable { mutableLongStateOf(NONE) }
    val defaults = remember(weeks) { PhotoRules.defaultComparison(weeks) }
    val left = leftPick.takeIf { it != NONE }?.let(LocalDate::ofEpochDay) ?: defaults.first
    val right = rightPick.takeIf { it != NONE }?.let(LocalDate::ofEpochDay) ?: defaults.second

    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // The pose a camera or picker was opened for; the launcher lives here, not in the sheet, which closes as it opens.
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableLongStateOf(NONE) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val source = rememberPhotoSource { uri ->
        Pose.fromKey(target.orEmpty())?.let { viewModel.save(it, thisWeek.toEpochDay(), uri) }
        target = null
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader(
            stringResource(R.string.photos_title).uppercase(locale),
            onBack,
            Modifier.padding(top = 12.dp, start = LedgerSpace.Gutter, end = LedgerSpace.Gutter),
            backDescription = stringResource(R.string.photos_back_description),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val frameHeight = if (wide) 320.dp else 180.dp
            val compareHeight = if (wide) 480.dp else 400.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LedgerSpace.Gutter)
                    .widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
            ) {
                // --- pose day: one quiet row under the header ---
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = LedgerSpace.Target)
                        .clickable(onClickLabel = stringResource(R.string.photos_day_change_description), role = Role.Button) { pickingDay = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.photos_day_row, PhotoPrefs.poseDay.getDisplayName(TextStyle.FULL, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(14.dp))
                }

                // --- this week ---
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        PanelLabel(stringResource(R.string.photos_this_week_label).uppercase())
                        Text(
                            stringResource(R.string.photos_week_of, thisWeek.format(dateFormat)),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pose.entries.forEach { pose ->
                            val photo = bySlot[thisWeek.toEpochDay() to pose]
                            PoseSlot(
                                pose = pose,
                                file = photo?.let(viewModel::fileOf),
                                busy = viewModel.saving == pose,
                                height = frameHeight,
                                modifier = Modifier.weight(1f),
                                onClick = { editing = pose.key },
                            )
                        }
                    }
                    if (viewModel.failed) {
                        Text(
                            stringResource(R.string.photo_offer_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.DangerRed,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                // --- compare ---
                Column(Modifier.fillMaxWidth()) {
                    PanelLabel(stringResource(R.string.photos_compare_label).uppercase())
                    if (weeks.size < 2) {
                        Text(
                            stringResource(R.string.photos_compare_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (weeks.isNotEmpty()) {
                    InkRowPanel(Modifier.fillMaxWidth()) {
                        ListRow(
                            stringResource(R.string.photos_left_week_label),
                            value = left?.format(dateFormat) ?: stringResource(R.string.photos_pick_week),
                            onClick = { picking = SIDE_LEFT },
                        )
                        InkDivider()
                        ListRow(
                            stringResource(R.string.photos_right_week_label),
                            value = right?.format(dateFormat) ?: stringResource(R.string.photos_pick_week),
                            onClick = { picking = SIDE_RIGHT },
                        )
                    }
                    Pose.entries.forEach { pose ->
                        val label = stringResource(poseLabel(pose))
                        Column(Modifier.fillMaxWidth()) {
                            PanelLabel(label.uppercase(), Modifier.padding(bottom = 8.dp))
                            PhotoCompare(
                                pose = label,
                                leftWeek = left?.format(dateFormat).orEmpty(),
                                rightWeek = right?.format(dateFormat).orEmpty(),
                                leftFile = left?.let { bySlot[it.toEpochDay() to pose] }?.let(viewModel::fileOf),
                                rightFile = right?.let { bySlot[it.toEpochDay() to pose] }?.let(viewModel::fileOf),
                                height = compareHeight,
                            )
                        }
                    }
                }

                Text(stringResource(R.string.photos_privacy_caption), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                Spacer(Modifier.height(LedgerSpace.Section))
            }
        }
    }

    // --- one slot: take, choose, or remove ---
    editing?.let { key ->
        val pose = Pose.fromKey(key)
        if (pose == null) {
            editing = null
            return@let
        }
        val photo = bySlot[thisWeek.toEpochDay() to pose]
        IronvellumDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(poseLabel(pose))) },
            text = {
                PhotoSourceTiles(
                    helper = stringResource(R.string.photos_pose_helper, stringResource(poseLabel(pose)).lowercase(locale)),
                    source = PhotoSource(
                        canTake = source.canTake,
                        take = { target = key; editing = null; source.take() },
                        pick = { target = key; editing = null; source.pick() },
                    ),
                    enabled = viewModel.saving == null,
                )
            },
            dismissButton = if (photo != null && viewModel.saving == null) {
                { IronvellumButton(label = stringResource(R.string.photo_remove_button), onClick = { editing = null; removing = photo.id }, danger = true) }
            } else {
                null
            },
            confirmButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { editing = null }, quiet = true) },
        )
    }
    if (removing != NONE) {
        PhotoRemoveDialog(
            onRemove = { viewModel.delete(removing); removing = NONE },
            onKeep = { removing = NONE },
        )
    }

    // --- week and day pickers ---
    picking?.let { side ->
        IronvellumDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(R.string.photos_pick_week_title)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    weeks.forEachIndexed { i, week ->
                        if (i > 0) InkDivider()
                        ListRow(
                            stringResource(R.string.photos_week_of, week.format(dateFormat)),
                            value = if (week == (if (side == SIDE_LEFT) left else right)) stringResource(R.string.photos_picked) else null,
                            onClick = {
                                if (side == SIDE_LEFT) leftPick = week.toEpochDay() else rightPick = week.toEpochDay()
                                picking = null
                            },
                        )
                    }
                }
            },
            confirmButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { picking = null }, quiet = true) },
        )
    }
    if (pickingDay) {
        IronvellumDialog(
            onDismissRequest = { pickingDay = false },
            title = { Text(stringResource(R.string.photos_day_label)) },
            text = {
                Column {
                    // Monday first, as the rest of the app reads a week.
                    DayOfWeek.entries.forEachIndexed { i, day ->
                        if (i > 0) InkDivider()
                        ListRow(
                            day.getDisplayName(TextStyle.FULL, locale),
                            value = if (day == PhotoPrefs.poseDay) stringResource(R.string.photos_picked) else null,
                            onClick = {
                                PhotoPrefs.setPoseDay(context.applicationContext, day)
                                pickingDay = false
                            },
                        )
                    }
                }
            },
            confirmButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { pickingDay = false }, quiet = true) },
        )
    }
}

private const val SIDE_LEFT = "left"
private const val SIDE_RIGHT = "right"
private const val NONE = -1L

private fun poseLabel(pose: Pose): Int = when (pose) {
    Pose.FRONT -> R.string.photos_pose_front
    Pose.SIDE -> R.string.photos_pose_side
    Pose.BACK -> R.string.photos_pose_back
}

/** One of this week's three slots: the photo, or a camera-with-plus mark on an empty field (no silhouette art exists for a pose). The whole slot is the 48dp-plus target. */
@Composable
private fun PoseSlot(pose: Pose, file: File?, busy: Boolean, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(poseLabel(pose))
    val description = stringResource(if (file != null) R.string.photos_slot_change_description else R.string.photos_slot_add_description, label)
    Column(
        modifier.clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (file != null) {
            PhotoFrame(file, stringResource(R.string.photos_slot_description, label), height = height, scale = ContentScale.Crop)
        } else {
            EmptyField(height, null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.photos_slot_add_label), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            if (busy) stringResource(R.string.photo_offer_saving) else label,
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.heightIn(min = 48.dp).padding(top = 8.dp),
        )
    }
}

@Composable
internal fun EmptyField(height: Dp, text: String?, content: @Composable () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.small).background(IronvellumColors.VaultHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        content()
    }
}
