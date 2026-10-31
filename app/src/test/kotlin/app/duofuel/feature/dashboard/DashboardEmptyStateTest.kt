package app.duofuel.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
    }
}
