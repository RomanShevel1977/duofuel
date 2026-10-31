package app.duofuel.feature.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.duofuel.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.LocalDate

@Composable
fun AnalyticsScreen(
    periodTitle: String,
    distanceLine: String,
    fillLines: List<String>,
    costLines: List<String>,
    cycleLines: List<String>,
    consumptionLines: List<String>,
    unclosedLines: List<String>,
    scenarioLines: List<String>,
    chartPoints: List<Double>,
    periodMismatch: Boolean,
    onLastN: (Int) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    initialLastN: String = "100",
    initialStart: String = "",
    initialEnd: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var lastN by remember { mutableStateOf(initialLastN) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var formError by remember { mutableStateOf<String?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.analytics), style = MaterialTheme.typography.headlineSmall)
        Text(periodTitle, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            lastN,
            { lastN = it },
            label = { Text(stringResource(R.string.period_last_n)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val parsed = lastN.toIntOrNull()
                when {
                    parsed == null -> formError = context.getString(R.string.issue_number)
                    parsed <= 0 -> formError = context.getString(R.string.issue_positive)
                    else -> {
                        formError = null
                        onLastN(parsed)
                    }
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.report_apply_last_n)) }
        OutlinedTextField(
            start,
            { start = it },
            label = { Text(stringResource(R.string.period_custom)) },
            supportingText = { Text(stringResource(R.string.date_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            end,
            { end = it },
            label = { Text(stringResource(R.string.period_custom)) },
            supportingText = { Text(stringResource(R.string.date_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val from = runCatching { LocalDate.parse(start) }.getOrNull()
                val to = runCatching { LocalDate.parse(end) }.getOrNull()
                if (from == null || to == null || to < from) {
                    formError = context.getString(R.string.issue_date)
                } else {
                    formError = null
                    onCustom(from, to)
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.report_apply_custom)) }
        formError?.let { Text(it) }
        if (periodMismatch) Text(stringResource(R.string.period_mismatch))
        Section(stringResource(R.string.report_distance), listOf(distanceLine))
        Section(stringResource(R.string.report_fills), fillLines)
        Section(stringResource(R.string.report_costs), costLines)
        Section(stringResource(R.string.report_cycles), cycleLines)
        Section(stringResource(R.string.report_consumption), consumptionLines)
        if (unclosedLines.isNotEmpty()) {
            Text(stringResource(R.string.unclosed), style = MaterialTheme.typography.titleMedium)
            unclosedLines.forEach { Text(it) }
        }
        Section(stringResource(R.string.report_scenario), scenarioLines)
        Text(stringResource(R.string.chart_closed), style = MaterialTheme.typography.titleMedium)
        if (chartPoints.isEmpty()) {
            Text(stringResource(R.string.empty_chart))
        } else {
            val producer = remember { CartesianChartModelProducer() }
            LaunchedEffect(chartPoints) { producer.runTransaction { lineSeries { series(chartPoints) } } }
            CartesianChartHost(
                rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                producer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (lines.isEmpty()) {
        Text(stringResource(R.string.quality_insufficient))
    } else {
        lines.forEach { Text(it) }
    }
}
