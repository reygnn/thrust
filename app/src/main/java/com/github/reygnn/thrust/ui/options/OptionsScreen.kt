package com.github.reygnn.thrust.ui.options

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.thrust.R
import com.github.reygnn.thrust.data.ControlMode
import com.github.reygnn.thrust.data.ThrustSide
import com.github.reygnn.thrust.data.WheelSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    onNavigateBack: () -> Unit,
    vm: OptionsViewModel = viewModel(factory = OptionsViewModel.Factory),
) {
    val gunEnabled  by vm.playerGunEnabled.collectAsStateWithLifecycle()
    val controlMode by vm.controlMode.collectAsStateWithLifecycle()
    val thrustSide  by vm.thrustSide.collectAsStateWithLifecycle()
    val wheelSize   by vm.wheelSize.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.options_title)) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text  = "←",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Section: Steuerung ───────────────────────────────────────────
            Text(
                text       = stringResource(R.string.options_section_controls),
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()

            // Steuerungsmodus
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text  = stringResource(R.string.options_control_mode),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text  = stringResource(R.string.options_control_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    RadioRow(
                        selected = controlMode == ControlMode.BUTTONS,
                        label    = stringResource(R.string.options_control_mode_buttons),
                        onSelect = { vm.setControlMode(ControlMode.BUTTONS) },
                        modifier = Modifier.weight(1f),
                    )
                    RadioRow(
                        selected = controlMode == ControlMode.WHEEL,
                        label    = stringResource(R.string.options_control_mode_wheel),
                        onSelect = { vm.setControlMode(ControlMode.WHEEL) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Schub-Position (nur Wheel-Modus)
            if (controlMode == ControlMode.WHEEL) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text  = stringResource(R.string.options_thrust_side),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text  = stringResource(R.string.options_thrust_side_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        RadioRow(
                            selected = thrustSide == ThrustSide.LEFT,
                            label    = stringResource(R.string.options_thrust_side_left),
                            onSelect = { vm.setThrustSide(ThrustSide.LEFT) },
                            modifier = Modifier.weight(1f),
                        )
                        RadioRow(
                            selected = thrustSide == ThrustSide.RIGHT,
                            label    = stringResource(R.string.options_thrust_side_right),
                            onSelect = { vm.setThrustSide(ThrustSide.RIGHT) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Drehrad-Größe (nur Wheel-Modus)
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text  = stringResource(R.string.options_wheel_size),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text  = stringResource(R.string.options_wheel_size_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        RadioRow(
                            selected = wheelSize == WheelSize.SMALL,
                            label    = stringResource(R.string.options_wheel_size_small),
                            onSelect = { vm.setWheelSize(WheelSize.SMALL) },
                            modifier = Modifier.weight(1f),
                        )
                        RadioRow(
                            selected = wheelSize == WheelSize.MEDIUM,
                            label    = stringResource(R.string.options_wheel_size_medium),
                            onSelect = { vm.setWheelSize(WheelSize.MEDIUM) },
                            modifier = Modifier.weight(1f),
                        )
                        RadioRow(
                            selected = wheelSize == WheelSize.LARGE,
                            label    = stringResource(R.string.options_wheel_size_large),
                            onSelect = { vm.setWheelSize(WheelSize.LARGE) },
                            modifier = Modifier.weight(1f),
                        )
                        RadioRow(
                            selected = wheelSize == WheelSize.XL,
                            label    = stringResource(R.string.options_wheel_size_xl),
                            onSelect = { vm.setWheelSize(WheelSize.XL) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Section: Gameplay ────────────────────────────────────────────
            Text(
                text       = stringResource(R.string.options_section_gameplay),
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.options_player_cannon),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.options_player_cannon_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked         = gunEnabled,
                    onCheckedChange = vm::togglePlayerGun,
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            if (gunEnabled) {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.options_cannon_active_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.options_cannon_active_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.options_classic_mode_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.options_classic_mode_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    label:    String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}