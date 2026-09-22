package com.monarch.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.BodyLimits
import com.monarch.app.domain.Sex
import com.monarch.app.ui.components.InkSegmented
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.launchGuarded
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class OnboardingViewModel(
    private val repo: Repository,
) : ViewModel() {

    /** In-memory only: a skip must not need a new persisted flag to hold for this process. */
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    /**
     * "Never set up" is the profile's own null height (Entities: "Null = never
     * set"): a fresh install has it, everyone who ever saved a height does not.
     * Null until Room's first emission, so neither gate branch flashes before
     * the truth arrives.
     */
    val needsSetup: StateFlow<Boolean?> =
        combine(repo.observeBodyProfile(), _dismissed) { body, dismissed ->
            body.first == null && !dismissed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun skip() {
        _dismissed.value = true
    }

    /**
     * Height is written BEFORE the weigh-in on purpose: addStat stamps the
     * profile height onto the stat row, so the wrong order would land the very
     * first reading heightless with BMI stuck at the dash.
     */
    fun complete(name: String, sex: Sex, heightCm: Double, weightKg: Double) {
        viewModelScope.launchGuarded("onboarding") {
            if (name.trim().isNotEmpty()) repo.rename(name)
            repo.setSex(sex)
            repo.setHeight(heightCm)
            repo.addStat(weightKg, null)
        }
    }
}

/**
 * First-run setup: name, sex, height and the first weigh-in, in one window.
 *
 * Every number this app shows a new hunter is body-scaled - strength scores
 * need a bodyweight, BMI and FFMI need a height, the Navy estimator needs a
 * sex. Without this window those facts stayed buried in Settings, and a hunter
 * who went straight to Train banked strength zero on every session until they
 * happened to weigh in.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingViewModel(monarchRepository()) } },
    ),
) {
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var weightInput by rememberSaveable { mutableStateOf("") }

    // The same bounds the repository enforces, checked here so a refused
    // value never reaches a submit button.
    val heightCm = heightInput.toDoubleOrNull()
    val weightKg = weightInput.toDoubleOrNull()
    val valid = name.trim().isNotEmpty() &&
        BodyLimits.validHeight(heightCm) &&
        BodyLimits.validWeight(weightKg)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "WELCOME, HUNTER",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.EmeraldBright,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Three facts scale every number this app shows you. Set them once.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(20.dp))

        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                "HUNTER PROFILE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("Claim your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = heightInput,
                    onValueChange = { heightInput = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = weightInput,
                    onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            InkSegmented(
                options = Sex.entries.map { it to it.name },
                selected = sex,
                onPick = { sex = it },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Weight is your first weigh-in: strength scores are body-scaled against it. " +
                    "Height powers BMI and FFMI. Sex picks the body-fat formula.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonarchButton(
                    label = "Begin",
                    onClick = { viewModel.complete(name, sex, heightCm!!, weightKg!!) },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                MonarchButton(
                    label = "Skip",
                    onClick = { viewModel.skip() },
                    quiet = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Skipping is fine - Settings and the Stats log can set these any time. " +
                    "Until a weigh-in exists, sessions bank no strength score.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
