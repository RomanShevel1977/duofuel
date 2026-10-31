package app.duofuel.ui

import android.content.Context
import app.duofuel.R
import app.duofuel.domain.model.MileageForecast
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.ScheduleStatus
import app.duofuel.domain.validation.Dates
import java.time.ZoneId

internal val serviceKinds = listOf(
    "liquid-filter" to R.string.svc_liquid,
    "vapor-filter" to R.string.svc_vapor,
    "injectors" to R.string.svc_injectors,
    "reducer" to R.string.svc_reducer,
    "tank" to R.string.svc_tank,
    "valves" to R.string.svc_valves,
)

internal fun serviceLabel(context: Context, code: String): String {
    val label = serviceKinds.firstOrNull { it.first == code }?.second
    return if (label == null) code else context.getString(label)
}

internal fun nearestSummary(context: Context, status: ScheduleStatus): String {
    val head = serviceLabel(context, status.serviceType) + " · " + statusLabel(context, status.status)
    val due = dueParts(context, status)
    return if (due.isEmpty()) head else head + " · " + due.joinToString(" · ")
}

internal fun serviceDetailLines(context: Context, status: ScheduleStatus): List<String> = buildList {
    add(serviceLabel(context, status.serviceType) + " · " + statusLabel(context, status.status))
    val due = dueParts(context, status)
    if (due.isNotEmpty()) add(due.joinToString(" · "))
    status.snoozeUntilEpochDay?.let { day ->
        add(context.getString(R.string.snooze) + " " + Dates.formatEpochDay(day))
    }
    add(forecastLine(context, status.forecast30))
    add(forecastLine(context, status.forecast90))
}

internal fun odometerLine(context: Context, event: OdometerEventCalc): String {
    val zone = ZoneId.systemDefault()
    val kind = if (event.kind == OdometerKind.RESET) {
        context.getString(R.string.odometer_reset)
    } else {
        context.getString(R.string.odometer_mode)
    }
    return Dates.formatDay(event.occurredAtMs, zone) + " " +
        Dates.formatTime(event.occurredAtMs, zone) + " · " +
        context.getString(R.string.report_km, event.odometerKm) + " · " + kind
}

private fun dueParts(context: Context, status: ScheduleStatus): List<String> = buildList {
    status.dueOdometerKm?.let { add(context.getString(R.string.report_km, it)) }
    status.dueEpochDay?.let { add(Dates.formatEpochDay(it)) }
}

private fun forecastLine(context: Context, forecast: MileageForecast): String {
    val title = context.getString(R.string.forecast) + " " + forecast.windowDays
    val average = forecast.averageKmPerDay?.stripTrailingZeros()?.toPlainString()
        ?: return "$title: " + context.getString(R.string.quality_insufficient)
    val parts = mutableListOf(context.getString(R.string.forecast_per_day, average))
    forecast.daysToDue?.stripTrailingZeros()?.toPlainString()?.let { days ->
        parts += context.getString(R.string.forecast_days, days)
    }
    return "$title: " + parts.joinToString(" · ")
}
