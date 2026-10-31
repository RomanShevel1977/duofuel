package app.duofuel.feature.analytics

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.duofuel.ui.theme.DuoFuelTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AnalyticsReportTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reportListsPeriodFacts_andDoesNotInventUnclosedRate() {
        var lastN: Int? = null
        var range: Pair<LocalDate, LocalDate>? = null
        compose.setContent {
            DuoFuelTheme {
                AnalyticsScreen(
                    periodTitle = "Месяц",
                    distanceLine = "Недостаточно данных · fuel_cashflow",
                    fillLines = listOf("Бензин · 2 · 30 L"),
                    costLines = listOf("Оплаченные заправки · Недостаточно данных · fuel_cashflow"),
                    cycleLines = emptyList(),
                    consumptionLines = listOf("Бензин · Недостаточно данных · fuel_cycle_full_to_full"),
                    unclosedLines = listOf("LPG · Незакрытый цикл не показан как фактический расход."),
                    scenarioLines = listOf("Чистая экономия · Недостаточно данных · gbo_roi"),
                    chartPoints = emptyList(),
                    periodMismatch = true,
                    onLastN = { lastN = it },
                    onCustom = { start, end -> range = start to end },
                    initialLastN = "x",
                    initialStart = "bad-start",
                    initialEnd = "bad-end",
                )
            }
        }
        compose.onAllNodesWithText("Бензин · 2 · 30 L").assertCountEquals(1)
        compose.onAllNodesWithText("LPG · Незакрытый цикл не показан как фактический расход.").assertCountEquals(1)
        compose.onAllNodesWithText("Бензин · Недостаточно данных · fuel_cycle_full_to_full").assertCountEquals(1)
        compose.onAllNodesWithText("Чистая экономия · Недостаточно данных · gbo_roi").assertCountEquals(1)
        compose.onAllNodesWithText("Нет закрытых циклов для графика.").assertCountEquals(1)
        compose.onAllNodesWithText("Периоды этих показателей на км не совпадают.").assertCountEquals(1)
        compose.onNodeWithText("/100", substring = true).assertDoesNotExist()
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("0,0").assertDoesNotExist()
        compose.onNodeWithText("0.0").assertDoesNotExist()

        compose.onNodeWithText("Показать последние км").performClick()
        compose.onAllNodesWithText("не число").assertCountEquals(1)
        assertNull(lastN)

        compose.onNodeWithText("x").performTextReplacement("40")
        compose.onNodeWithText("Показать последние км").performClick()
        assertEquals(40, lastN)

        compose.onNodeWithText("Показать интервал").performScrollTo().performClick()
        compose.onAllNodesWithText("дата в формате ГГГГ-ММ-ДД").assertCountEquals(1)
        assertNull(range)

        compose.onNodeWithText("bad-start").performScrollTo().performTextReplacement("2026-10-01")
        compose.onNodeWithText("bad-end").performScrollTo().performTextReplacement("2026-10-31")
        compose.onNodeWithText("Показать интервал").performScrollTo().performClick()
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), range)
    }
}
