@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.duofuel.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duofuel.R
import app.duofuel.ui.ChipFlow
import app.duofuel.ui.ScreenPage
import app.duofuel.ui.SectionCard

data class MetricUi(
    val title: String,
    val value: String?,
    val quality: String,
    val detail: String,
    val events: String,
)

@Composable
fun DashboardScreen(
    vehicleName: String,
    empty: Boolean,
    metrics: List<MetricUi>,
    consumption: List<MetricUi>,
    nearest: String?,
    recent: List<String>,
    period: String,
    periodMismatch: Boolean,
    payoffText: String?,
    onPeriod: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    ScreenPage(modifier) {
        Text(vehicleName, style = MaterialTheme.typography.headlineSmall)
        ChipFlow {
            listOf(
                "month" to stringResource(R.string.period_month),
                "cycle" to stringResource(R.string.period_cycle),
                "all" to stringResource(R.string.period_all),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = period == key,
                    onClick = { onPeriod(key) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (empty) {
            SectionCard {
                Text(stringResource(R.string.dashboard_empty), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (periodMismatch) {
            Text(stringResource(R.string.period_mismatch), color = MaterialTheme.colorScheme.error)
        }
        metrics.forEachIndexed { index, metric ->
            MetricBlock(metric) { selected = index }
        }
        consumption.forEachIndexed { index, card ->
            MetricBlock(card) { selected = metrics.size + index }
        }
        SectionCard(stringResource(R.string.nearest_service)) {
            Text(nearest ?: stringResource(R.string.no_service), style = MaterialTheme.typography.bodyLarge)
        }
        payoffText?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        SectionCard(stringResource(R.string.recent)) {
            if (recent.isEmpty()) {
                Text(stringResource(R.string.quality_insufficient))
            } else {
                recent.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
    selected?.let { index ->
        val metric = (metrics + consumption).getOrNull(index) ?: return@let
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                TextButton(onClick = { selected = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.confirm))
                }
            },
            title = { Text(metric.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(metric.quality)
                    Text(metric.detail)
                    Text(stringResource(R.string.events))
                    Text(metric.events.ifBlank { stringResource(R.string.quality_insufficient) })
                }
            },
        )
    }
}

@Composable
private fun MetricBlock(metric: MetricUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(metric.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(metric.value ?: stringResource(R.string.quality_insufficient), style = MaterialTheme.typography.headlineSmall)
            Text(metric.quality, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
