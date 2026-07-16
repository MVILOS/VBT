package com.vbt.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.weight
import com.vbt.app.recording.OverlayMetric
import com.vbt.app.ui.theme.VbtTeal

/**
 * Lista przełączników wyboru metryk nakładki nagrywania. Używana i na ekranie
 * Ustawień, i w szybkim panelu na ekranie nagrywania - jedno źródło UI.
 */
@Composable
fun OverlayMetricsSelector(
    selected: Set<OverlayMetric>,
    onToggle: (OverlayMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OverlayMetric.entries.forEach { metric ->
            val isOn = metric in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isOn,
                        role = Role.Switch,
                        onValueChange = { onToggle(metric) }
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (metric.unit.isEmpty()) metric.label else "${metric.label} (${metric.unit})",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        metricDescription(metric),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.padding(horizontal = 8.dp))
                Switch(
                    checked = isOn,
                    onCheckedChange = { onToggle(metric) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = VbtTeal
                    )
                )
            }
        }
    }
}

private fun metricDescription(metric: OverlayMetric): String = when (metric) {
    OverlayMetric.VMAX -> "Prędkość szczytowa powtórzenia"
    OverlayMetric.AVG -> "Średnia prędkość koncentryczna"
    OverlayMetric.POWER -> "Moc szczytowa (siła × Vmax)"
    OverlayMetric.EAI -> "Elastic Acceleration Index (wzór do potwierdzenia)"
    OverlayMetric.ROM -> "Zakres ruchu (dystans powtórzenia)"
}
