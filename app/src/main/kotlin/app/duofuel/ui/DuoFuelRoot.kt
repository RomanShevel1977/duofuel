@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.duofuel.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.duofuel.R
import app.duofuel.domain.calculator.PeriodRequest
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.RoiWindow
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.repository.FuelConfigProfile
import app.duofuel.domain.repository.FuelEntryDraft
import app.duofuel.domain.repository.VehicleProfile
import app.duofuel.domain.validation.Parse
import app.duofuel.feature.dashboard.DashboardScreen
import app.duofuel.feature.dashboard.DuoFuelViewModel
import app.duofuel.feature.dashboard.MetricUi
import app.duofuel.feature.dashboard.currentMonth
import app.duofuel.feature.dashboard.parseDate
import app.duofuel.feature.dashboard.parseKm
import app.duofuel.feature.dashboard.parseMoney
import app.duofuel.feature.dashboard.parseQuantity
import app.duofuel.feature.dashboard.productOrNull
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun DuoFuelRoot(viewModel: DuoFuelViewModel = hiltViewModel(LocalContext.current as ComponentActivity)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "dashboard"
    LaunchedEffect(state.notice) {
        val text = state.notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(text, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        viewModel.clearNotice()
    }
    LaunchedEffect(state.ready, state.app.active?.vehicle?.id) {
        if (state.ready && state.app.active == null && route != "vehicle") nav.navigate("vehicle")
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = route == destination.route,
                        onClick = {
                            nav.navigate(destination.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
            composable("dashboard") {
                val snapshot = state.app.active
                val report = state.report
                DashboardScreen(
                    vehicleName = snapshot?.vehicle?.name ?: stringResource(R.string.profile),
                    empty = snapshot?.entries.isNullOrEmpty(),
                    metrics = report?.let { metricsOf(it) }.orEmpty(),
                    consumption = report?.consumptionByFuel?.map { card ->
                        val rate = card.rate.ratePer100?.let { "$it ${card.rate.volumeUnit ?: ""}/100" }
                            ?: stringResource(R.string.quality_insufficient)
                        fuelLabel(LocalContext.current, card.fuel) + ": " + rate + " · " + qualityLabel(LocalContext.current, card.rate.quality)
                    }.orEmpty(),
                    nearest = state.app.active?.let { snapshotActive ->
                        report?.nearestService?.let { status ->
                            status.serviceType + " · " + statusLabel(LocalContext.current, status.status)
                        }
                    },
                    recent = report?.recentEvents?.map { it.kind + " " + (it.odometerKm ?: "") }.orEmpty(),
                    periodMismatch = report?.periodMismatch == true,
                    payoffText = report?.roi?.let { roi ->
                        when {
                            roi.doesNotPayOff -> stringResource(R.string.no_payoff)
                            roi.window == RoiWindow.ENTIRE_HISTORY -> stringResource(R.string.roi_entire_history)
                            else -> null
                        }
                    },
                    onPeriod = { key ->
                        viewModel.setPeriod(
                            when (key) {
                                "cycle" -> PeriodRequest.LatestControlCycles
                                "all" -> PeriodRequest.AllHistory
                                else -> currentMonth()
                            },
                        )
                    },
                )
            }
            composable("refuel") { RefuelRoute(viewModel) }
            composable("history") { HistoryRoute(viewModel) }
            composable("service") { ServiceRoute(viewModel) }
            composable("more") {
                MoreRoute(
                    onOpen = { nav.navigate(it) },
                )
            }
            composable("vehicle") { VehicleRoute(viewModel) }
            composable("analytics") { AnalyticsRoute(viewModel) }
            composable("roi") { RoiRoute(viewModel) }
            composable("settings") { SettingsRoute(viewModel) }
        }
    }
}

private data class Destination(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val destinations = listOf(
    Destination("dashboard", R.string.nav_dashboard, Icons.Filled.Home),
    Destination("refuel", R.string.nav_refuel, Icons.Filled.Add),
    Destination("history", R.string.nav_history, Icons.AutoMirrored.Filled.List),
    Destination("service", R.string.nav_service, Icons.Filled.Notifications),
    Destination("more", R.string.nav_more, Icons.Filled.MoreVert),
)

@Composable
private fun MoreRoute(onOpen: (String) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        listOf(
            "analytics" to R.string.analytics,
            "roi" to R.string.roi_title,
            "vehicle" to R.string.profile,
            "settings" to R.string.settings,
        ).forEach { (route, label) ->
            Button(onClick = { onOpen(route) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun metricsOf(report: app.duofuel.domain.model.DashboardReport): List<MetricUi> {
    val context = LocalContext.current
    fun money(metric: app.duofuel.domain.model.Metric, title: Int, formula: Int, perKm: Boolean): MetricUi {
        val minor = metric.moneyMinor
        val currency = metric.currency
        val value = if (metric.quality == Quality.INSUFFICIENT || minor == null || currency == null) {
            null
        } else if (perKm) {
            metric.distanceKm?.let { formatPerKm(minor, it, currency) }
        } else {
            formatMinor(minor, currency, java.util.Locale.getDefault())
        }
        return MetricUi(
            title = context.getString(title),
            value = value,
            quality = qualityLabel(context, metric.quality),
            detail = context.getString(formula) + " · " + metric.methodId,
            events = metric.inputEventIds.joinToString(),
        )
    }
    return listOf(
        money(report.cashflow.total, R.string.metric_cashflow, R.string.formula_cashflow, false),
        money(report.cashflowPerKm, R.string.metric_cashflow_km, R.string.formula_cashflow, true),
        money(report.fuelCostPerKm, R.string.metric_fuel_km, R.string.formula_cost, true),
        money(report.fuelPlusGboPerKm, R.string.metric_fuel_gbo_km, R.string.formula_cost, true),
        money(report.operatingPerKm, R.string.metric_operating_km, R.string.formula_cost, true),
        money(report.ownershipPerKm, R.string.metric_ownership_km, R.string.formula_cost, true),
        money(report.saving.net, R.string.metric_saving, R.string.formula_roi, false),
    )
}

@Composable
private fun RefuelRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.app.active
    val context = LocalContext.current
    var fuel by remember { mutableStateOf(viewModel.suggestedFuel()) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(viewModel.suggestedPrice(fuel)) }
    var total by remember { mutableStateOf("") }
    var totalLocked by remember { mutableStateOf(false) }
    var odometer by remember { mutableStateOf(viewModel.suggestedOdometer()) }
    var trip by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(state.app.prefillFull) }
    var station by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var confirmDecrease by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<String?>(null) }
    val currency = snapshot?.fuels?.firstOrNull { it.fuelType == fuel }?.currency ?: deviceCurrency()
    val unit = snapshot?.fuels?.firstOrNull { it.fuelType == fuel }?.unit ?: "L"
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        receipt = copyReceipt(context, uri)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.nav_refuel), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        FuelType.entries.forEach { type ->
            FilterChip(
                selected = fuel == type,
                onClick = {
                    fuel = type
                    if (!totalLocked) price = viewModel.suggestedPrice(type)
                },
                label = { Text(fuelLabel(context, type)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        OutlinedTextField(quantity, { quantity = it; if (!totalLocked) total = productOrNull(it, price, currency)?.let { minor -> formatMinor(minor, currency, java.util.Locale.getDefault()).substringBefore(' ') } ?: total }, label = { Text(stringResource(R.string.field_quantity)) }, suffix = { Text(unit) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(price, { price = it; if (!totalLocked) total = productOrNull(quantity, it, currency)?.let { minor -> formatMinor(minor, currency, java.util.Locale.getDefault()).substringBefore(' ') } ?: total }, label = { Text(stringResource(R.string.field_price)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(total, { total = it; totalLocked = true }, label = { Text(stringResource(R.string.field_total)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        productOrNull(quantity, price, currency)?.let {
            Text(stringResource(R.string.product_hint, formatMinor(it, currency, java.util.Locale.getDefault())))
        }
        FilterChip(selected = !trip, onClick = { trip = false }, label = { Text(stringResource(R.string.field_odometer)) })
        FilterChip(selected = trip, onClick = { trip = true }, label = { Text(stringResource(R.string.field_trip)) })
        OutlinedTextField(odometer, { odometer = it }, label = { Text(if (trip) stringResource(R.string.field_trip) else stringResource(R.string.field_odometer)) }, suffix = { Text("км") }, modifier = Modifier.fillMaxWidthField())
        // OPEN-FULL-UX: полная и частичная всегда видны. Полная не подставляется невидимо.
        FilterChip(selected = full, onClick = { full = true }, label = { Text(stringResource(R.string.full)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = !full, onClick = { full = false }, label = { Text(stringResource(R.string.partial)) }, modifier = Modifier.heightIn(min = 48.dp))
        OutlinedTextField(station, { station = it }, label = { Text(stringResource(R.string.field_station)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidthField())
        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.receipt))
        }
        errors.forEach { Text(it) }
        Button(
            onClick = {
                val vehicleId = snapshot?.vehicle?.id
                if (vehicleId == null) {
                    errors = listOf(context.getString(R.string.issue_line, context.getString(R.string.profile), context.getString(R.string.issue_required)))
                    return@Button
                }
                val qty = parseQuantity(quantity)
                val km = parseKm(odometer)
                val money = parseMoney(total.ifBlank { "0" }.let { if (total.isBlank()) "" else total }, currency)
                val localErrors = buildList {
                    if (qty is Parse.Err) add(issueText(context, qty.issue))
                    if (km is Parse.Err) add(issueText(context, km.issue))
                    if (total.isBlank()) add(issueText(context, app.duofuel.domain.validation.ValidationIssue(app.duofuel.domain.validation.FieldCode.TOTAL, app.duofuel.domain.validation.IssueCode.REQUIRED, true)))
                    if (money is Parse.Err && total.isNotBlank()) add(issueText(context, money.issue))
                }
                if (localErrors.isNotEmpty() || qty !is Parse.Ok || km !is Parse.Ok || money !is Parse.Ok) {
                    errors = localErrors
                    return@Button
                }
                val absolute = if (trip) {
                    val base = snapshot.odometer.maxByOrNull { it.sortKey() }?.odometerKm
                    if (base == null) {
                        errors = listOf(issueText(context, app.duofuel.domain.validation.ValidationIssue(app.duofuel.domain.validation.FieldCode.TRIP, app.duofuel.domain.validation.IssueCode.TRIP_NEEDS_BASE, true)))
                        return@Button
                    }
                    base + km.value
                } else {
                    km.value
                }
                val draft = FuelEntryDraft(
                    id = null,
                    vehicleId = vehicleId,
                    fuelType = fuel,
                    occurredAtMs = System.currentTimeMillis(),
                    odometerKm = absolute,
                    inputMethod = if (trip) InputMethod.TRIP else InputMethod.ABSOLUTE,
                    odometerKind = OdometerKind.READING,
                    quantity = qty.value,
                    unit = unit,
                    unitPriceMinor = (parseMoney(price, currency) as? Parse.Ok)?.value,
                    totalCostMinor = money.value,
                    currency = currency,
                    isFull = full,
                    station = station.ifBlank { null },
                    notes = notes.ifBlank { null },
                    receiptUri = receipt,
                    extraAttachmentUris = emptyList(),
                )
                if (!confirmDecrease && viewModel.needsDecreaseConfirm(draft)) {
                    confirmDecrease = true
                    errors = listOf(context.getString(R.string.issue_decrease))
                    return@Button
                }
                errors = viewModel.saveFuel(draft, confirmDecrease, true)
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.save)) }
    }
}

// OPEN-PHOTO: байты копируются как есть, без сжатия и без выдуманного лимита.
private fun copyReceipt(context: android.content.Context, uri: Uri?): String? {
    if (uri == null) return null
    val dir = File(context.filesDir, "receipts").apply { mkdirs() }
    val dest = File(dir, UUID.randomUUID().toString())
    context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
    return dest.toURI().toString()
}

@Composable
private fun HistoryRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.app.active
    var filter by remember { mutableStateOf("fuel") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        RowChips(filter) { filter = it }
        when (filter) {
            "service" -> snapshot?.serviceRecords?.forEach { record ->
                Text(record.serviceType + " · " + record.costMinor + " " + record.currency + if (record.isGbo) " · ГБО" else "")
                TextButton(onClick = { viewModel.deleteService(record.id) }) { Text(stringResource(R.string.delete)) }
            }
            "expense" -> snapshot?.expenses?.forEach { expense ->
                Text(expense.category + " · " + expense.amountMinor + " " + expense.currency)
                TextButton(onClick = { viewModel.deleteExpense(expense.id) }) { Text(stringResource(R.string.delete)) }
            }
            else -> snapshot?.entries?.forEach { entry ->
                Text(fuelLabel(LocalContext.current, entry.fuelType) + " · " + entry.quantity + " " + entry.unit + " · " + entry.totalCostMinor + " " + entry.currency + if (entry.isFull) " · " + stringResource(R.string.full) else "")
                TextButton(onClick = { viewModel.deleteFuel(entry.id) }) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun RowChips(selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.Row {
        FilterChip(selected == "fuel", { onSelect("fuel") }, label = { Text(stringResource(R.string.history_fuel)) })
        FilterChip(selected == "service", { onSelect("service") }, label = { Text(stringResource(R.string.history_service)) })
        FilterChip(selected == "expense", { onSelect("expense") }, label = { Text(stringResource(R.string.history_expense)) })
    }
}

@Composable
private fun ServiceRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vehicleId = state.app.active?.vehicle?.id ?: return
    var type by remember { mutableStateOf("liquid-filter") }
    var intervalKm by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf("") }
    var leadKm by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("liquid-filter") }
    var workCost by remember { mutableStateOf("0") }
    var isGbo by remember { mutableStateOf(true) }
    var expenseCategory by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("0") }
    var includeOperating by remember { mutableStateOf(false) }
    var includeGbo by remember { mutableStateOf(false) }
    val currency = deviceCurrency()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        // OPEN-FILTER-DEFAULT: диапазоны из подсказок остаются текстом, в интервал не подставляются.
        Text(stringResource(R.string.hint_liquid))
        Text(stringResource(R.string.hint_vapor))
        Text(stringResource(R.string.hint_injectors))
        Text(stringResource(R.string.hint_reducer))
        Text(stringResource(R.string.hint_tank))
        Text(stringResource(R.string.hint_valves))
        Text(stringResource(R.string.reference_ignored))
        state.report?.serviceStatuses?.forEach { status ->
            Text(status.serviceType + " · " + statusLabel(LocalContext.current, status.status))
            Text(stringResource(R.string.forecast) + " 30: " + (status.forecast30.averageKmPerDay?.toPlainString() ?: stringResource(R.string.quality_insufficient)))
        }
        OutlinedTextField(type, { type = it }, label = { Text(stringResource(R.string.service_schedule)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(intervalKm, { intervalKm = it }, label = { Text(stringResource(R.string.interval_km)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(intervalDays, { intervalDays = it }, label = { Text(stringResource(R.string.interval_days)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(leadKm, { leadKm = it }, label = { Text(stringResource(R.string.lead_km)) }, modifier = Modifier.fillMaxWidthField())
        Button(onClick = {
            viewModel.saveSchedule(
                ServiceScheduleCalc(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    serviceType = type,
                    intervalKm = intervalKm.toIntOrNull(),
                    intervalDays = intervalDays.toIntOrNull(),
                    dueOdometerKm = null,
                    dueDateEpochDay = null,
                    leadKm = leadKm.toIntOrNull(),
                    leadDays = null,
                    snoozeUntilEpochDay = null,
                    remindersEnabled = true,
                ),
            )
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add)) }
        OutlinedTextField(workType, { workType = it }, label = { Text(stringResource(R.string.service_work)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(workCost, { workCost = it }, label = { Text(stringResource(R.string.cost)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        RowSwitch(stringResource(R.string.is_gbo), isGbo) { isGbo = it }
        Button(onClick = {
            val money = (parseMoney(workCost, currency) as? Parse.Ok)?.value ?: return@Button
            viewModel.saveService(
                ServiceRecordCalc(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    serviceType = workType,
                    occurredAtMs = System.currentTimeMillis(),
                    odometerKm = state.app.active?.odometer?.maxByOrNull { it.sortKey() }?.odometerKm ?: 0,
                    costMinor = money,
                    currency = currency,
                    isGbo = isGbo,
                    scheduleId = state.app.active?.schedules?.singleOrNull { it.serviceType == workType }?.id,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
        OutlinedTextField(expenseCategory, { expenseCategory = it }, label = { Text(stringResource(R.string.expense_category)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(expenseAmount, { expenseAmount = it }, label = { Text(stringResource(R.string.cost)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        RowSwitch(stringResource(R.string.include_operating), includeOperating) { includeOperating = it }
        RowSwitch(stringResource(R.string.include_gbo), includeGbo) { includeGbo = it }
        Button(onClick = {
            val money = (parseMoney(expenseAmount, currency) as? Parse.Ok)?.value ?: return@Button
            viewModel.saveExpense(
                ExpenseCalc(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    category = expenseCategory.ifBlank { "other" },
                    occurredAtMs = System.currentTimeMillis(),
                    odometerKm = state.app.active?.odometer?.maxByOrNull { it.sortKey() }?.odometerKm,
                    amountMinor = money,
                    currency = currency,
                    includeInOperating = includeOperating,
                    includeInGbo = includeGbo,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add)) }
    }
}

@Composable
private fun RowSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun VehicleRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = state.app.active?.vehicle
    val zone = ZoneId.systemDefault()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var make by remember { mutableStateOf(existing?.make.orEmpty()) }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var year by remember { mutableStateOf(existing?.year?.toString().orEmpty()) }
    var engine by remember { mutableStateOf(existing?.engine.orEmpty()) }
    var injection by remember { mutableStateOf(existing?.injectionType.orEmpty()) }
    var gas by remember { mutableStateOf(existing?.gasFuelType ?: FuelType.LPG) }
    var generation by remember { mutableStateOf(existing?.gboGeneration.orEmpty()) }
    var manufacturer by remember { mutableStateOf(existing?.gboManufacturer.orEmpty()) }
    var equipment by remember { mutableStateOf(existing?.gboEquipmentModel.orEmpty()) }
    var installDate by remember { mutableStateOf("") }
    var installCost by remember { mutableStateOf("") }
    var useful by remember { mutableStateOf(existing?.usefulVolumePercent?.toString().orEmpty()) }
    var switchTemp by remember { mutableStateOf(existing?.switchTempC?.toString().orEmpty()) }
    var currency by remember { mutableStateOf(state.app.active?.fuels?.firstOrNull()?.currency ?: deviceCurrency()) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        // OPEN-BILLING-WHEN: в интерфейсе один активный профиль. Второе авто — вместе с Pro.
        Text(stringResource(R.string.one_vehicle))
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(make, { make = it }, label = { Text(stringResource(R.string.field_make)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.field_model)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(year, { year = it }, label = { Text(stringResource(R.string.field_year)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(engine, { engine = it }, label = { Text(stringResource(R.string.field_engine)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(injection, { injection = it }, label = { Text(stringResource(R.string.field_injection)) }, modifier = Modifier.fillMaxWidthField())
        FilterChip(gas == FuelType.LPG, { gas = FuelType.LPG }, label = { Text(stringResource(R.string.fuel_lpg)) })
        FilterChip(gas == FuelType.CNG, { gas = FuelType.CNG }, label = { Text(stringResource(R.string.fuel_cng)) })
        OutlinedTextField(generation, { generation = it }, label = { Text(stringResource(R.string.field_generation)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(manufacturer, { manufacturer = it }, label = { Text(stringResource(R.string.field_manufacturer)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(equipment, { equipment = it }, label = { Text(stringResource(R.string.field_equipment)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(installDate, { installDate = it }, label = { Text(stringResource(R.string.field_install_date)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(installCost, { installCost = it }, label = { Text(stringResource(R.string.field_install_cost)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(useful, { useful = it }, label = { Text(stringResource(R.string.field_useful)) }, supportingText = { Text(stringResource(R.string.useful_hint)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(switchTemp, { switchTemp = it }, label = { Text(stringResource(R.string.field_switch)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text(stringResource(R.string.field_currency)) }, modifier = Modifier.fillMaxWidthField())
        errors.forEach { Text(it) }
        Button(onClick = {
            val id = existing?.id ?: UUID.randomUUID().toString()
            val cost = installCost.takeIf { it.isNotBlank() }?.let { parseMoney(it, currency) }
            if (cost is Parse.Err) {
                errors = listOf(issueText(context, cost.issue))
                return@Button
            }
            val profile = VehicleProfile(
                id = id,
                name = name,
                make = make.ifBlank { null },
                model = model.ifBlank { null },
                year = year.toIntOrNull(),
                engine = engine.ifBlank { null },
                injectionType = injection.ifBlank { null },
                gasFuelType = gas,
                gboGeneration = generation.ifBlank { null },
                gboManufacturer = manufacturer.ifBlank { null },
                gboEquipmentModel = equipment.ifBlank { null },
                installedAtMs = installDate.takeIf { it.isNotBlank() }?.let { parseDate(it, zone) },
                installCostMinor = (cost as? Parse.Ok)?.value,
                installCostCurrency = currency,
                usefulVolumePercent = useful.toDoubleOrNull(),
                switchTempC = switchTemp.toDoubleOrNull(),
                archivedAtMs = existing?.archivedAtMs,
                createdAtMs = existing?.createdAtMs ?: 0L,
                updatedAtMs = existing?.updatedAtMs ?: 0L,
            )
            val fuels = listOf(
                FuelConfigProfile(UUID.randomUUID().toString(), FuelType.PETROL, "L", null, currency, null),
                FuelConfigProfile(UUID.randomUUID().toString(), gas, if (gas == FuelType.CNG) "m3" else "L", null, currency, null),
            ).distinctBy { it.fuelType }
            errors = viewModel.saveVehicle(profile, fuels, state.app.active?.settings ?: app.duofuel.domain.model.CalculationSettingsCalc(null, null, currency, true, false))
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
        if (existing != null) {
            Button(onClick = { viewModel.archiveActive() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.archive)) }
            Button(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete)) }
        }
        state.app.archived.forEach { archived ->
            TextButton(onClick = { viewModel.restore(archived.id) }) { Text(stringResource(R.string.restore) + " " + archived.name) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_vehicle_title)) },
            text = { Text(stringResource(R.string.delete_vehicle_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        shareBytes(context, "duofuel.csv.zip", viewModel.exportCsv())
                        viewModel.deleteActive()
                        confirmDelete = false
                    }
                }) { Text(stringResource(R.string.export)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteActive()
                    confirmDelete = false
                }) { Text(stringResource(R.string.delete_without_export)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report = state.report
    var lastN by remember { mutableStateOf("100") }
    var start by remember { mutableStateOf(LocalDate.now().minusDays(30).toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    val points = report?.closedCycles?.mapNotNull { it.rate.ratePer100 }.orEmpty()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.analytics), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        report?.consumptionByFuel?.forEach { card ->
            Text(card.fuel.name + " · " + (card.rate.ratePer100?.toString() ?: stringResource(R.string.quality_insufficient)) + " · " + card.rate.methodId)
        }
        if (report?.unclosed?.isNotEmpty() == true) Text(stringResource(R.string.unclosed))
        if (points.isEmpty()) {
            Text(stringResource(R.string.empty_chart))
        } else {
            val producer = remember { CartesianChartModelProducer() }
            LaunchedEffect(points) { producer.runTransaction { lineSeries { series(points) } } }
            CartesianChartHost(
                rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                producer,
                modifier = Modifier.heightIn(min = 220.dp),
            )
        }
        OutlinedTextField(lastN, { lastN = it }, label = { Text(stringResource(R.string.period_last_n)) })
        Button(onClick = { lastN.toIntOrNull()?.let { viewModel.setPeriod(PeriodRequest.LastNKm(it)) } }) { Text(stringResource(R.string.period_last_n)) }
        OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.period_custom)) })
        OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.period_custom)) })
        Button(onClick = {
            val from = runCatching { LocalDate.parse(start) }.getOrNull()
            val to = runCatching { LocalDate.parse(end) }.getOrNull()
            if (from != null && to != null) viewModel.setPeriod(PeriodRequest.Custom(from, to))
        }) { Text(stringResource(R.string.period_custom)) }
    }
}

@Composable
private fun RoiRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roi = state.report?.roi
    var baseline by remember { mutableStateOf(state.app.active?.settings?.baselinePetrolL100?.toString().orEmpty()) }
    var price by remember { mutableStateOf("") }
    val currency = state.app.active?.vehicle?.installCostCurrency ?: deviceCurrency()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.roi_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.roi_no_date))
        if (roi?.doesNotPayOff == true) Text(stringResource(R.string.no_payoff))
        if (roi?.window == RoiWindow.ENTIRE_HISTORY) Text(stringResource(R.string.roi_entire_history))
        roi?.remainingKm?.remainingKm?.let { Text(stringResource(R.string.roi_remaining, it.toPlainString())) }
        if (roi?.unreimbursedMinor == 0L) Text(stringResource(R.string.roi_covered))
        roi?.investmentMinor?.let { Text(formatMinor(it, roi.investmentCurrency ?: currency, java.util.Locale.getDefault())) }
        Text(stringResource(R.string.scenario_price) + ": " + (roi?.saving?.priceIsScenario == true))
        val factual = roi?.saving?.factualAveragePetrolPrice
        Text(stringResource(R.string.factual_price) + ": " + (factual?.moneyMinor?.let { formatMinor(it, factual.currency ?: currency, java.util.Locale.getDefault()) } ?: stringResource(R.string.quality_insufficient)))
        OutlinedTextField(baseline, { baseline = it }, label = { Text(stringResource(R.string.baseline)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(price, { price = it }, label = { Text(stringResource(R.string.scenario_price)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        Button(onClick = {
            val active = state.app.active ?: return@Button
            val priceMinor = (parseMoney(price, currency) as? Parse.Ok)?.value
            viewModel.saveVehicle(
                active.vehicle,
                active.fuels,
                CalculationSettingsCalc(
                    baselinePetrolL100 = baseline.toDoubleOrNull(),
                    petrolPriceMinor = priceMinor,
                    petrolPriceCurrency = currency,
                    petrolPriceIsScenario = true,
                    baselineFromFactualHistory = false,
                ),
            )
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun SettingsRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var conflicts by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        scope.launch {
            val preview = viewModel.previewArchive(bytes)
            conflicts = preview.conflicts.size
            if (preview.checksumMatches) pending = bytes
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setReminders(true)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        RowSwitch(stringResource(R.string.settings_full_default), state.app.prefillFull) { viewModel.setPrefillFull(it) }
        Text(stringResource(R.string.reminders_hint))
        RowSwitch(stringResource(R.string.settings_reminders), state.app.remindersEnabled) { enabled ->
            if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setReminders(enabled)
            }
        }
        Button(onClick = { scope.launch { shareBytes(context, "duofuel-csv.zip", viewModel.exportCsv()) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.csv))
        }
        Button(onClick = { scope.launch { shareBytes(context, "duofuel-archive.zip", viewModel.exportArchive()) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.local_archive))
        }
        Button(onClick = { picker.launch(arrayOf("application/zip")) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.import_archive))
        }
        if (conflicts > 0) Text(stringResource(R.string.conflict_count, conflicts))
    }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.import_archive)) },
            text = { Text(stringResource(R.string.import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    val bytes = pending
                    pending = null
                    if (bytes != null) scope.launch { viewModel.applyArchive(bytes) }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Modifier.fillMaxWidthField(): Modifier = fillMaxWidth()

private fun shareBytes(context: android.content.Context, name: String, bytes: ByteArray) {
    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    val file = File(dir, name)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private typealias CalculationSettingsCalc = app.duofuel.domain.model.CalculationSettingsCalc
