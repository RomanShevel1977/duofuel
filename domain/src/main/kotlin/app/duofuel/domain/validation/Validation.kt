package app.duofuel.domain.validation

import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.RecordSource
import java.math.BigDecimal
import java.util.Currency

enum class FieldCode {
    NAME,
    FUEL_CONFIG,
    QUANTITY,
    TOTAL,
    UNIT_PRICE,
    ODOMETER,
    TRIP,
    CURRENCY,
    UNIT,
    SOURCE,
    FUEL_TYPE,
    INSTALL_COST,
    SCHEDULE,
}

enum class IssueCode {
    REQUIRED,
    MUST_BE_POSITIVE,
    MUST_BE_NON_NEGATIVE,
    WHOLE_KILOMETERS,
    EXTRA_FRACTION_DIGITS,
    INVALID_CURRENCY,
    DECREASE_NEEDS_CONFIRMATION,
    CHRONOLOGY_NEEDS_CONFIRMATION,
    TRIP_NEEDS_BASE,
    SOURCE_NOT_ACCEPTED,
    DUPLICATE_FUEL,
    AMBIGUOUS_SCHEDULE,
    NOT_A_NUMBER,
}

data class ValidationIssue(
    val field: FieldCode,
    val code: IssueCode,
    val blocking: Boolean,
)

object MoneyMath {
    fun fractionDigits(currency: String): Int? = try {
        val digits = Currency.getInstance(currency).defaultFractionDigits
        if (digits < 0) 0 else digits
    } catch (_: IllegalArgumentException) {
        null
    }

    // OPEN-ROUND: сумма чека не округляется. Лишние знаки — ошибка поля, а не тихое округление.
    fun parseMinor(text: String, currency: String): Parse<Long> {
        val digits = fractionDigits(currency) ?: return Parse.Err(
            ValidationIssue(FieldCode.CURRENCY, IssueCode.INVALID_CURRENCY, true),
        )
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) {
            return Parse.Err(ValidationIssue(FieldCode.TOTAL, IssueCode.REQUIRED, true))
        }
        val amount = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return Parse.Err(ValidationIssue(FieldCode.TOTAL, IssueCode.NOT_A_NUMBER, true))
        }
        if (amount.signum() < 0) {
            return Parse.Err(ValidationIssue(FieldCode.TOTAL, IssueCode.MUST_BE_NON_NEGATIVE, true))
        }
        if (amount.stripTrailingZeros().scale() > digits) {
            return Parse.Err(ValidationIssue(FieldCode.TOTAL, IssueCode.EXTRA_FRACTION_DIGITS, true))
        }
        return Parse.Ok(amount.movePointRight(digits).setScale(0).longValueExact())
    }

    fun exactProductMinor(quantityText: String, unitPriceMinor: Long): Long? {
        val quantity = try {
            BigDecimal(quantityText.trim().replace(',', '.'))
        } catch (_: NumberFormatException) {
            return null
        }
        val product = quantity.multiply(BigDecimal.valueOf(unitPriceMinor)).stripTrailingZeros()
        if (product.scale() > 0) return null
        return product.longValueExact()
    }
}

sealed interface Parse<out T> {
    data class Ok<T>(val value: T) : Parse<T>
    data class Err<T>(val issue: ValidationIssue) : Parse<T>
}

object InputParsers {
    fun quantity(text: String): Parse<Double> {
        if (text.isBlank()) return Parse.Err(ValidationIssue(FieldCode.QUANTITY, IssueCode.REQUIRED, true))
        val amount = try {
            BigDecimal(text.trim().replace(',', '.'))
        } catch (_: NumberFormatException) {
            return Parse.Err(ValidationIssue(FieldCode.QUANTITY, IssueCode.NOT_A_NUMBER, true))
        }
        if (amount.signum() <= 0) {
            return Parse.Err(ValidationIssue(FieldCode.QUANTITY, IssueCode.MUST_BE_POSITIVE, true))
        }
        val value = amount.toDouble()
        if (!value.isFinite() || value <= 0.0) {
            return Parse.Err(ValidationIssue(FieldCode.QUANTITY, IssueCode.MUST_BE_POSITIVE, true))
        }
        return Parse.Ok(value)
    }

    // OPEN-ODO-UNIT: дробный километраж и мили не входят, пока столбец odometer_km INTEGER.
    fun odometerKm(text: String): Parse<Int> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Parse.Err(ValidationIssue(FieldCode.ODOMETER, IssueCode.REQUIRED, true))
        if (trimmed.contains('.') || trimmed.contains(',')) {
            return Parse.Err(ValidationIssue(FieldCode.ODOMETER, IssueCode.WHOLE_KILOMETERS, true))
        }
        val value = trimmed.toIntOrNull()
            ?: return Parse.Err(ValidationIssue(FieldCode.ODOMETER, IssueCode.NOT_A_NUMBER, true))
        if (value < 0) return Parse.Err(ValidationIssue(FieldCode.ODOMETER, IssueCode.MUST_BE_NON_NEGATIVE, true))
        return Parse.Ok(value)
    }
}

object VehicleValidator {
    // OPEN-REQUIRED: достаточно имени и одной конфигурации топлива с единицей и валютой.
    fun validate(
        name: String,
        fuels: List<FuelDraft>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (name.isBlank()) issues += ValidationIssue(FieldCode.NAME, IssueCode.REQUIRED, true)
        if (fuels.isEmpty()) issues += ValidationIssue(FieldCode.FUEL_CONFIG, IssueCode.REQUIRED, true)
        if (fuels.map { it.fuelType }.distinct().size != fuels.size) {
            // OPEN-MULTI-TANK: одна конфигурация на тип топлива.
            issues += ValidationIssue(FieldCode.FUEL_TYPE, IssueCode.DUPLICATE_FUEL, true)
        }
        fuels.forEach { fuel ->
            if (fuel.unit.isBlank()) issues += ValidationIssue(FieldCode.UNIT, IssueCode.REQUIRED, true)
            if (MoneyMath.fractionDigits(fuel.currency) == null) {
                issues += ValidationIssue(FieldCode.CURRENCY, IssueCode.INVALID_CURRENCY, true)
            }
        }
        return issues
    }
}

data class FuelDraft(val fuelType: FuelType, val unit: String, val currency: String)

object FuelEntryValidator {
    fun source(source: RecordSource): ValidationIssue? {
        if (source != RecordSource.MANUAL) {
            return ValidationIssue(FieldCode.SOURCE, IssueCode.SOURCE_NOT_ACCEPTED, true)
        }
        return null
    }

    // OPEN-ZERO-TOTAL: нулевая сумма при положительном количестве допустима.
    fun total(totalMinor: Long?): ValidationIssue? = when {
        totalMinor == null -> ValidationIssue(FieldCode.TOTAL, IssueCode.REQUIRED, true)
        totalMinor < 0L -> ValidationIssue(FieldCode.TOTAL, IssueCode.MUST_BE_NON_NEGATIVE, true)
        else -> null
    }
}

object OdometerValidator {
    fun check(
        candidate: OdometerEventCalc,
        existing: List<OdometerEventCalc>,
        confirmDecrease: Boolean,
        confirmChronology: Boolean,
    ): List<ValidationIssue> {
        val merged = (existing.filter { it.id != candidate.id } + candidate)
            .sortedWith(compareBy({ it.occurredAtMs }, { it.odometerKm }, { it.createdAtMs }, { it.id }))
        val index = merged.indexOfFirst { it.id == candidate.id }
        val previous = merged.getOrNull(index - 1)
        val next = merged.getOrNull(index + 1)
        val issues = mutableListOf<ValidationIssue>()
        if (candidate.inputMethod == InputMethod.TRIP && existing.isEmpty()) {
            issues += ValidationIssue(FieldCode.TRIP, IssueCode.TRIP_NEEDS_BASE, true)
        }
        if (previous != null && candidate.odometerKm < previous.odometerKm && candidate.kind != OdometerKind.RESET) {
            // OPEN-ODO-THRESHOLD: числового порога нет. Уменьшение без подтверждения не сохраняется.
            if (!confirmDecrease) {
                issues += ValidationIssue(FieldCode.ODOMETER, IssueCode.DECREASE_NEEDS_CONFIRMATION, true)
            }
        }
        if (next != null && candidate.odometerKm > next.odometerKm && next.kind != OdometerKind.RESET && !confirmChronology) {
            issues += ValidationIssue(FieldCode.ODOMETER, IssueCode.CHRONOLOGY_NEEDS_CONFIRMATION, true)
        }
        return issues
    }
}

object ServiceValidator {
    fun ambiguous(matchingSchedules: Int, chosenScheduleId: String?): ValidationIssue? {
        if (matchingSchedules > 1 && chosenScheduleId.isNullOrBlank()) {
            return ValidationIssue(FieldCode.SCHEDULE, IssueCode.AMBIGUOUS_SCHEDULE, true)
        }
        return null
    }
}
