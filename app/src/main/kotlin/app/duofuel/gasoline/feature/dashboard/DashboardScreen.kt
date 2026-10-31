@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.duofuel.gasoline.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import app.duofuel.gasoline.R

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
    consumption: List<String>,
    nearest: String?,
    recent: List<String>,
    periodMismatch: Boolean,
    payoffText: String?,
    onPeriod: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(vehicleName, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "month" to stringResource(R.string.period_month),
                "cycle" to stringResource(R.string.period_cycle),
                "all" to stringResource(R.string.period_all),
            ).forEach { (key, label) ->
                FilterChip(selected = false, onClick = { onPeriod(key) }, label = { Text(label) })
            }
        }
        if (empty) {
            Text(stringResource(R.string.dashboard_empty))
        }
        if (periodMismatch) {
            Text(stringResource(R.string.period_mismatch))
        }
        metrics.forEachIndexed { index, metric ->
            MetricBlock(metric) { selected = index }
        }
        consumption.forEach { Text(it) }
        Text(stringResource(R.string.nearest_service), style = MaterialTheme.typography.titleMedium)
        Text(nearest ?: stringResource(R.string.no_service))
        payoffText?.let { Text(it) }
        Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleMedium)
        if (recent.isEmpty()) {
            Text(stringResource(R.string.quality_insufficient))
        } else {
            recent.forEach { Text(it) }
        }
    }
    selected?.let { index ->
        val metric = metrics[index]
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                TextButton(onClick = { selected = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.confirm))
                }
            },
            title = { Text(metric.title) },
            text = {
                Column {
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
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 4.dp),
    ) {
        Text(metric.title, style = MaterialTheme.typography.titleMedium)
        Text(metric.value ?: stringResource(R.string.quality_insufficient))
        Text(metric.quality)
    }
}
