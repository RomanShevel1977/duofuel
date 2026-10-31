package app.duofuel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.MileageForecast
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.RecordSource
import app.duofuel.domain.model.ScheduleStatus
import app.duofuel.domain.model.ServiceDueStatus
import app.duofuel.ui.theme.DuoFuelTheme
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ServiceReadoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dueAndForecast_showCalculatedTerms_andResetStaysOnTheScale() {
        val status = ScheduleStatus(
            scheduleId = "s",
            serviceType = "liquid-filter",
            status = ServiceDueStatus.PENDING,
            dueOdometerKm = 18000,
            dueEpochDay = LocalDate.of(2026, 12, 1).toEpochDay(),
            snoozeUntilEpochDay = null,
            forecast30 = MileageForecast(30, 900, BigDecimal("30.0000"), BigDecimal("100.0000"), Quality.CALCULATED),
            forecast90 = MileageForecast(90, 900, BigDecimal("10.0000"), null, Quality.CALCULATED),
        )
        val unknownPace = ScheduleStatus(
            scheduleId = "pace",
            serviceType = "reducer",
            status = ServiceDueStatus.PENDING,
            dueOdometerKm = null,
            dueEpochDay = null,
            snoozeUntilEpochDay = null,
            forecast30 = MileageForecast(30, null, null, null, Quality.INSUFFICIENT),
            forecast90 = MileageForecast(90, null, null, null, Quality.INSUFFICIENT),
        )
        val reset = OdometerEventCalc(
            id = "reset",
            vehicleId = "v",
            occurredAtMs = 0L,
            odometerKm = 1200,
            kind = OdometerKind.RESET,
            inputMethod = InputMethod.ABSOLUTE,
            source = RecordSource.MANUAL,
            linkedFuelEntryId = null,
            createdAtMs = 1L,
        )
        val reading = OdometerEventCalc(
            id = "read",
            vehicleId = "v",
            occurredAtMs = 0L,
            odometerKm = 50,
            kind = OdometerKind.READING,
            inputMethod = InputMethod.ABSOLUTE,
            source = RecordSource.MANUAL,
            linkedFuelEntryId = null,
            createdAtMs = 2L,
        )
        compose.setContent {
            val context = LocalContext.current
            DuoFuelTheme {
                Column {
                    serviceDetailLines(context, status).forEach { Text(it) }
                    serviceDetailLines(context, unknownPace).forEach { Text(it) }
                    Text(nearestSummary(context, status))
                    Text(odometerLine(context, reset))
                    Text(odometerLine(context, reading))
                }
            }
        }
        compose.onAllNodesWithText("Фильтр жидкой фазы · Ожидается").assertCountEquals(1)
        compose.onAllNodesWithText("Фильтр жидкой фазы · Ожидается · 18000 км · 2026-12-01").assertCountEquals(1)
        compose.onAllNodesWithText("18000 км · 2026-12-01").assertCountEquals(1)
        compose.onAllNodesWithText("Прогноз, не срок 30: 30 км/день · 100 дн.").assertCountEquals(1)
        compose.onAllNodesWithText("Прогноз, не срок 90: 10 км/день").assertCountEquals(1)
        compose.onAllNodesWithText("Прогноз, не срок 90: 10 км/день · 0 дн.").assertCountEquals(0)
        compose.onAllNodesWithText("Прогноз, не срок 30: Недостаточно данных").assertCountEquals(1)
        compose.onAllNodesWithText("1200 км · Замена панели", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("50 км · Показание", substring = true).assertCountEquals(1)
    }
}
