package app.duofuel.feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.duofuel.ui.theme.DuoFuelTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DashboardEmptyStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyDashboard_showsInsufficientCopy_andDoesNotShowZero() {
        compose.setContent {
            DuoFuelTheme {
                DashboardScreen(
                    vehicleName = "Octavia",
                    empty = true,
                    metrics = listOf(
                        MetricUi(
                            title = "Расход",
                            value = null,
                            quality = "Недостаточно данных",
                            detail = "",
                            events = "",
                        ),
                    ),
                    consumption = emptyList(),
                    nearest = null,
                    recent = emptyList(),
                    period = "month",
                    periodMismatch = false,
                    payoffText = null,
                    onPeriod = {},
                )
            }
        }
        compose.onNodeWithText("Octavia").assertIsDisplayed()
        compose.onNodeWithText("Пока нет заправок. Расход, стоимость километра и экономия не подменяются нулём.")
            .assertIsDisplayed()
        compose.onAllNodesWithText("Недостаточно данных").assertCountEquals(2)
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("0,0").assertDoesNotExist()
        compose.onNodeWithText("0.0").assertDoesNotExist()
        compose.onNodeWithText("Месяц").assertIsSelected()
    }

    @Test
    fun periodChip_switchesSelection_andRecentEventShowsDate() {
        compose.setContent {
            var period by remember { mutableStateOf("month") }
            DuoFuelTheme {
                DashboardScreen(
                    vehicleName = "Octavia",
                    empty = false,
                    metrics = listOf(
                        MetricUi("Оплаты", "10", "Расчётный", "Месяц · формула", "fill-1"),
                    ),
                    consumption = listOf(
                        MetricUi("Бензин", null, "Недостаточно данных", "Месяц · цикл", "fill-1"),
                    ),
                    nearest = "Фильтр · Срок наступил",
                    recent = listOf("2026-10-31 15:05 · Заправки · Бензин · 1200 км"),
                    period = period,
                    periodMismatch = false,
                    payoffText = null,
                    onPeriod = { period = it },
                )
            }
        }
        compose.onAllNodesWithText("2026-10-31 15:05 · Заправки · Бензин · 1200 км").assertCountEquals(1)
        compose.onNodeWithText("Цикл").performClick()
        compose.onNodeWithText("Цикл").assertIsSelected()
        compose.onNodeWithText("Месяц").assertIsNotSelected()
        compose.onNodeWithText("Оплаты").performClick()
        compose.onAllNodesWithText("Месяц · формула").assertCountEquals(1)
        compose.onAllNodesWithText("fill-1").assertCountEquals(1)
    }
}
