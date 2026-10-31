package app.duofuel.ui

import android.content.Context
import app.duofuel.R
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.ServiceDueStatus
import app.duofuel.domain.validation.FieldCode
import app.duofuel.domain.validation.IssueCode
import app.duofuel.domain.validation.ValidationIssue
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

fun qualityLabel(context: Context, quality: Quality): String = context.getString(
    when (quality) {
        Quality.FACTUAL -> R.string.quality_factual
        Quality.CALCULATED -> R.string.quality_calculated
        Quality.ESTIMATED -> R.string.quality_estimated
        Quality.INSUFFICIENT -> R.string.quality_insufficient
    },
)

fun fuelLabel(context: Context, fuel: FuelType): String = context.getString(
    when (fuel) {
        FuelType.PETROL -> R.string.fuel_petrol
        FuelType.LPG -> R.string.fuel_lpg
        FuelType.CNG -> R.string.fuel_cng
    },
)

fun statusLabel(context: Context, status: ServiceDueStatus): String = context.getString(
    when (status) {
        ServiceDueStatus.SOON -> R.string.status_soon
        ServiceDueStatus.DUE -> R.string.status_due
        ServiceDueStatus.OVERDUE -> R.string.status_overdue
        ServiceDueStatus.PENDING -> R.string.status_pending
        ServiceDueStatus.INSUFFICIENT -> R.string.quality_insufficient
    },
)

fun issueText(context: Context, issue: ValidationIssue): String {
    val field = context.getString(
        when (issue.field) {
            FieldCode.NAME -> R.string.field_name
            FieldCode.FUEL_CONFIG, FieldCode.FUEL_TYPE -> R.string.add_fuel
            FieldCode.QUANTITY -> R.string.field_quantity
            FieldCode.TOTAL -> R.string.field_total
            FieldCode.UNIT_PRICE -> R.string.field_price
            FieldCode.ODOMETER -> R.string.field_odometer
            FieldCode.TRIP -> R.string.field_trip
            FieldCode.CURRENCY -> R.string.field_currency
            FieldCode.UNIT -> R.string.field_unit
            FieldCode.SOURCE -> R.string.field_notes
            FieldCode.INSTALL_COST -> R.string.field_install_cost
            FieldCode.SCHEDULE -> R.string.service_schedule
        },
    )
    val reason = context.getString(
        when (issue.code) {
            IssueCode.REQUIRED -> R.string.issue_required
            IssueCode.MUST_BE_POSITIVE -> R.string.issue_positive
            IssueCode.MUST_BE_NON_NEGATIVE -> R.string.issue_non_negative
            IssueCode.WHOLE_KILOMETERS -> R.string.issue_whole_km
            IssueCode.EXTRA_FRACTION_DIGITS -> R.string.issue_fraction
            IssueCode.INVALID_CURRENCY -> R.string.issue_currency
            IssueCode.DECREASE_NEEDS_CONFIRMATION -> R.string.issue_decrease
            IssueCode.CHRONOLOGY_NEEDS_CONFIRMATION -> R.string.issue_chronology
            IssueCode.TRIP_NEEDS_BASE -> R.string.issue_trip
            IssueCode.SOURCE_NOT_ACCEPTED -> R.string.issue_required
            IssueCode.DUPLICATE_FUEL -> R.string.issue_duplicate
            IssueCode.AMBIGUOUS_SCHEDULE -> R.string.issue_schedule
            IssueCode.NOT_A_NUMBER -> R.string.issue_number
        },
    )
    return context.getString(R.string.issue_line, field, reason)
}

fun formatMinor(minor: Long, currency: String, locale: Locale): String {
    val digits = try {
        Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    } catch (_: IllegalArgumentException) {
        2
    }
    val amount = BigDecimal.valueOf(minor).movePointLeft(digits)
    return amount.stripTrailingZeros().toPlainString() + " " + currency
}

fun formatPerKm(minor: Long, distanceKm: Int, currency: String): String? {
    if (distanceKm <= 0) return null
    val digits = try {
        Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    } catch (_: IllegalArgumentException) {
        2
    }
    val perKm = BigDecimal.valueOf(minor)
        .movePointLeft(digits)
        .divide(BigDecimal.valueOf(distanceKm.toLong()), digits + 2, RoundingMode.HALF_UP)
    return perKm.stripTrailingZeros().toPlainString() + " " + currency + "/км"
}

// OPEN-MARKET: валюта и даты идут из локали устройства, не из константы языка.
fun deviceCurrency(): String = try {
    Currency.getInstance(Locale.getDefault()).currencyCode
} catch (_: Exception) {
    "USD"
}
