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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.duofuel.R
import app.duofuel.domain.calculator.PeriodRequest
import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.DashboardReport
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FuelConsumptionCard
import app.duofuel.domain.model.FuelEntryCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.MethodId
import app.duofuel.domain.model.Metric
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.Period
import app.duofuel.domain.model.Quality
import app.duofuel.domain.model.RecentEventRef
import app.duofuel.domain.model.RoiWindow
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.repository.FuelConfigProfile
import app.duofuel.domain.repository.FuelEntryDraft
import app.duofuel.domain.repository.VehicleProfile
import app.duofuel.domain.validation.Dates
import app.duofuel.domain.validation.FieldCode
import app.duofuel.domain.validation.IssueCode
import app.duofuel.domain.validation.Parse
import app.duofuel.domain.validation.ValidationIssue
import app.duofuel.feature.analytics.AnalyticsScreen
import app.duofuel.feature.dashboard.DashboardScreen
import app.duofuel.feature.dashboard.DuoFuelViewModel
import app.duofuel.feature.dashboard.MetricUi
import app.duofuel.feature.dashboard.currentMonth
import app.duofuel.feature.dashboard.parseDate
import app.duofuel.feature.dashboard.parseKm
import app.duofuel.feature.dashboard.parseMoney
import app.duofuel.feature.dashboard.parseQuantity
import app.duofuel.feature.dashboard.productOrNull
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
    LaunchedEffect(state.notice, state.noticeUndo) {
        val text = state.notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(text, actionLabel = if (state.noticeUndo) undoLabel else null)
        if (result == SnackbarResult.ActionPerformed && state.noticeUndo) viewModel.undo()
        viewModel.clearNotice()
    }
    LaunchedEffect(state.ready, state.app.active?.vehicle?.id) {
        if (state.ready && state.app.active == null && route != "vehicle") nav.navigate("vehicle")
    }
    LaunchedEffect(state.openRefuel) {
        if (!state.openRefuel) return@LaunchedEffect
        viewModel.consumeOpenRefuel()
        nav.navigate("refuel") {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
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
                val dashContext = LocalContext.current
                DashboardScreen(
                    vehicleName = snapshot?.vehicle?.name ?: stringResource(R.string.profile),
                    empty = snapshot?.entries.isNullOrEmpty(),
                    metrics = report?.let { metricsOf(it) }.orEmpty(),
                    consumption = report?.consumptionByFuel?.map { card ->
                        val rate = card.rate
                        MetricUi(
                            title = fuelLabel(dashContext, card.fuel),
                            value = rate.ratePer100?.let { "$it ${rate.volumeUnit ?: ""}/100" },
                            quality = qualityLabel(dashContext, rate.quality),
                            detail = periodTitle(dashContext, rate.period.labelKey) + " · " +
                                dashContext.getString(R.string.formula_cycle) + " · " + rate.methodId,
                            events = rate.inputEventIds.joinToString(),
                        )
                    }.orEmpty(),
                    nearest = state.app.active?.let { snapshotActive ->
                        report?.nearestService?.let { status ->
                            status.serviceType + " · " + statusLabel(LocalContext.current, status.status)
                        }
                    },
                    recent = report?.recentEvents?.asReversed()?.map { recentLine(dashContext, it) }.orEmpty(),
                    period = periodKey(state.period),
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
            composable("refuel") { RefuelRoute(viewModel, entryId = null) }
            composable(
                "refuel/{entryId}",
                arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
            ) { entry ->
                RefuelRoute(viewModel, entry.arguments?.getString("entryId"))
            }
            composable("history") { HistoryRoute(viewModel, onEditFuel = { id -> nav.navigate("refuel/$id") }) }
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
    ScreenPage {
        Text(stringResource(R.string.nav_more), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        listOf(
            "analytics" to R.string.analytics,
            "roi" to R.string.roi_title,
            "vehicle" to R.string.profile,
            "settings" to R.string.settings,
        ).forEach { (route, label) ->
            WideButton(stringResource(label), onClick = { onOpen(route) })
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
            detail = periodTitle(context, metric.period.labelKey) + " · " + context.getString(formula) + " · " + metric.methodId,
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
private fun RefuelRoute(viewModel: DuoFuelViewModel, entryId: String?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.app.active
    val editing = snapshot?.entries?.firstOrNull { it.id == entryId }
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
    var occurred by remember { mutableStateOf(LocalDate.now().toString()) }
    var occurredTime by remember { mutableStateOf(Dates.formatTime(System.currentTimeMillis(), ZoneId.systemDefault())) }
    var extras by remember { mutableStateOf(emptyList<String>()) }
    var pickExtra by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var confirmDecrease by remember { mutableStateOf(false) }
    var confirmChronology by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(editing?.id) {
        val entry = editing ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        fuel = entry.fuelType
        quantity = java.math.BigDecimal.valueOf(entry.quantity).stripTrailingZeros().toPlainString()
        price = entry.unitPriceMinor?.let { formatMinor(it, entry.currency, java.util.Locale.getDefault()).substringBefore(' ') }.orEmpty()
        total = formatMinor(entry.totalCostMinor, entry.currency, java.util.Locale.getDefault()).substringBefore(' ')
        totalLocked = true
        odometer = entry.odometerKm.toString()
        trip = false
        full = entry.isFull
        station = entry.station.orEmpty()
        notes = entry.notes.orEmpty()
        occurred = Dates.formatDay(entry.occurredAtMs, zone)
        occurredTime = Dates.formatTime(entry.occurredAtMs, zone)
        receipt = entry.receiptUri
        extras = entry.extraAttachmentUris
        loaded = true
    }
    val currency = snapshot?.fuels?.firstOrNull { it.fuelType == fuel }?.currency ?: deviceCurrency()
    val unit = snapshot?.fuels?.firstOrNull { it.fuelType == fuel }?.unit ?: "L"
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val copied = copyReceipt(context, uri) ?: return@rememberLauncherForActivityResult
        if (pickExtra) {
            if (copied != receipt && copied !in extras) extras = extras + copied
        } else {
            receipt = copied
            extras = extras.filter { it != copied }
        }
    }
    ScreenPage {
        Text(stringResource(R.string.nav_refuel), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        ChipFlow {
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
        }
        SectionCard(stringResource(R.string.section_amount)) {
            OutlinedTextField(quantity, { quantity = it; if (!totalLocked) total = productOrNull(it, price, currency)?.let { minor -> formatMinor(minor, currency, java.util.Locale.getDefault()).substringBefore(' ') } ?: total }, label = { Text(stringResource(R.string.field_quantity)) }, suffix = { Text(unit) }, modifier = Modifier.fillMaxWidthField())
            OutlinedTextField(price, { price = it; if (!totalLocked) total = productOrNull(quantity, it, currency)?.let { minor -> formatMinor(minor, currency, java.util.Locale.getDefault()).substringBefore(' ') } ?: total }, label = { Text(stringResource(R.string.field_price)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
            OutlinedTextField(total, { total = it; totalLocked = true }, label = { Text(stringResource(R.string.field_total)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
            productOrNull(quantity, price, currency)?.let {
                Text(stringResource(R.string.product_hint, formatMinor(it, currency, java.util.Locale.getDefault())))
            }
        }
        SectionCard(stringResource(R.string.section_when)) {
            if (editing == null) {
                ChipFlow {
                    FilterChip(selected = !trip, onClick = { trip = false }, label = { Text(stringResource(R.string.field_odometer)) }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = trip, onClick = { trip = true }, label = { Text(stringResource(R.string.field_trip)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            OutlinedTextField(odometer, { odometer = it }, label = { Text(if (trip) stringResource(R.string.field_trip) else stringResource(R.string.field_odometer)) }, suffix = { Text("км") }, modifier = Modifier.fillMaxWidthField())
            DateField(occurred, { occurred = it })
            TimeField(occurredTime, { occurredTime = it })
        }
        SectionCard(stringResource(R.string.section_tank)) {
            // OPEN-FULL-UX: полная и частичная всегда видны. Полная не подставляется невидимо.
            ChipFlow {
                FilterChip(selected = full, onClick = { full = true }, label = { Text(stringResource(R.string.full)) }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = !full, onClick = { full = false }, label = { Text(stringResource(R.string.partial)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        SectionCard(stringResource(R.string.section_extra)) {
            OutlinedTextField(station, { station = it }, label = { Text(stringResource(R.string.field_station)) }, supportingText = { Text(stringResource(R.string.field_optional)) }, modifier = Modifier.fillMaxWidthField())
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, supportingText = { Text(stringResource(R.string.field_optional)) }, modifier = Modifier.fillMaxWidthField())
            WideOutlinedButton(stringResource(R.string.receipt), onClick = {
                pickExtra = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            WideOutlinedButton(stringResource(R.string.add_photo), onClick = {
                pickExtra = true
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            extras.forEachIndexed { index, _ ->
                TextButton(onClick = { extras = extras.filterIndexed { i, _ -> i != index } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.remove_photo) + " " + (index + 1))
                }
            }
        }
        errors.forEach { ErrorText(it) }
        WideButton(stringResource(R.string.save), onClick = {
                val vehicleId = snapshot?.vehicle?.id
                if (vehicleId == null) {
                    errors = listOf(context.getString(R.string.issue_line, context.getString(R.string.profile), context.getString(R.string.issue_required)))
                    return@WideButton
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
                    return@WideButton
                }
                val whenMs = enteredMoment(occurred, occurredTime, zone, editing?.occurredAtMs, System.currentTimeMillis())
                if (whenMs == null) {
                    errors = listOf(momentIssue(context, occurred, occurredTime, zone))
                    return@WideButton
                }
                val absolute = if (trip && editing == null) {
                    val base = snapshot.odometer.maxByOrNull { it.sortKey() }?.odometerKm
                    if (base == null) {
                        errors = listOf(issueText(context, app.duofuel.domain.validation.ValidationIssue(app.duofuel.domain.validation.FieldCode.TRIP, app.duofuel.domain.validation.IssueCode.TRIP_NEEDS_BASE, true)))
                        return@WideButton
                    }
                    base + km.value
                } else {
                    km.value
                }
                val draft = FuelEntryDraft(
                    id = editing?.id,
                    vehicleId = vehicleId,
                    fuelType = fuel,
                    occurredAtMs = whenMs,
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
                    extraAttachmentUris = extras,
                )
                if (!confirmDecrease && viewModel.needsDecreaseConfirm(draft)) {
                    confirmDecrease = true
                    errors = listOf(context.getString(R.string.issue_decrease))
                    return@WideButton
                }
                if (!confirmChronology && viewModel.needsChronologyConfirm(draft)) {
                    confirmChronology = true
                    errors = listOf(context.getString(R.string.issue_chronology))
                    return@WideButton
                }
                errors = viewModel.saveFuel(draft, confirmDecrease, confirmChronology)
        })
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
private fun HistoryRoute(viewModel: DuoFuelViewModel, onEditFuel: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.app.active
    val context = LocalContext.current
    var filter by remember { mutableStateOf("fuel") }
    var editingService by remember { mutableStateOf<ServiceRecordCalc?>(null) }
    var editingExpense by remember { mutableStateOf<ExpenseCalc?>(null) }
    var serviceCost by remember { mutableStateOf("") }
    var serviceDate by remember { mutableStateOf("") }
    var serviceTime by remember { mutableStateOf("") }
    var serviceOdo by remember { mutableStateOf("") }
    var serviceProvider by remember { mutableStateOf("") }
    var serviceNotes by remember { mutableStateOf("") }
    var servicePhotos by remember { mutableStateOf(emptyList<String>()) }
    var expenseAmount by remember { mutableStateOf("") }
    var expenseDate by remember { mutableStateOf("") }
    var expenseTime by remember { mutableStateOf("") }
    var expenseNotes by remember { mutableStateOf("") }
    var odometerText by remember { mutableStateOf("") }
    var odometerDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var odometerTime by remember { mutableStateOf(Dates.formatTime(System.currentTimeMillis(), ZoneId.systemDefault())) }
    var panelReset by remember { mutableStateOf(false) }
    var odometerError by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val servicePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val copied = copyReceipt(context, uri) ?: return@rememberLauncherForActivityResult
        if (copied !in servicePhotos) servicePhotos = servicePhotos + copied
    }
    ScreenPage {
        Text(stringResource(R.string.nav_history), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        RowChips(filter) { filter = it }
        when (filter) {
            "service" -> if (snapshot?.serviceRecords.isNullOrEmpty()) {
                Text(stringResource(R.string.history_empty))
            } else snapshot?.serviceRecords?.forEach { record ->
                SectionCard {
                    Text(serviceLabel(context, record.serviceType) + " · " + Dates.formatDay(record.occurredAtMs, zone) + " " + Dates.formatTime(record.occurredAtMs, zone) + " · " + formatMinor(record.costMinor, record.currency, java.util.Locale.getDefault()) + if (record.isGbo) " · ГБО" else "")
                    androidx.compose.foundation.layout.Row {
                    TextButton(onClick = {
                        editingService = record
                        serviceCost = formatMinor(record.costMinor, record.currency, java.util.Locale.getDefault()).substringBefore(' ')
                        serviceDate = Dates.formatDay(record.occurredAtMs, zone)
                        serviceTime = Dates.formatTime(record.occurredAtMs, zone)
                        serviceOdo = record.odometerKm.toString()
                        serviceProvider = record.provider.orEmpty()
                        serviceNotes = record.notes.orEmpty()
                        servicePhotos = record.attachmentUris
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { viewModel.deleteService(record.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            "expense" -> if (snapshot?.expenses.isNullOrEmpty()) {
                Text(stringResource(R.string.history_empty))
            } else snapshot?.expenses?.forEach { expense ->
                SectionCard {
                    Text(expense.category + " · " + Dates.formatDay(expense.occurredAtMs, zone) + " " + Dates.formatTime(expense.occurredAtMs, zone) + " · " + formatMinor(expense.amountMinor, expense.currency, java.util.Locale.getDefault()))
                    androidx.compose.foundation.layout.Row {
                    TextButton(onClick = {
                        editingExpense = expense
                        expenseAmount = formatMinor(expense.amountMinor, expense.currency, java.util.Locale.getDefault()).substringBefore(' ')
                        expenseDate = Dates.formatDay(expense.occurredAtMs, zone)
                        expenseTime = Dates.formatTime(expense.occurredAtMs, zone)
                        expenseNotes = expense.notes.orEmpty()
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { viewModel.deleteExpense(expense.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            else -> {
                if (snapshot?.entries.isNullOrEmpty()) Text(stringResource(R.string.history_empty))
                snapshot?.entries?.forEach { entry ->
                    SectionCard {
                    Text(fuelLabel(LocalContext.current, entry.fuelType) + " · " + Dates.formatDay(entry.occurredAtMs, zone) + " " + Dates.formatTime(entry.occurredAtMs, zone) + " · " + plainQuantity(entry.quantity) + " " + entry.unit + " · " + formatMinor(entry.totalCostMinor, entry.currency, java.util.Locale.getDefault()) + if (entry.isFull) " · " + stringResource(R.string.full) else "")
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { onEditFuel(entry.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = { viewModel.deleteFuel(entry.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete)) }
                    }
                    }
                }
                Text(stringResource(R.string.odometer_only), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                OutlinedTextField(odometerText, { odometerText = it }, label = { Text(stringResource(R.string.field_odometer)) }, suffix = { Text("км") }, modifier = Modifier.fillMaxWidthField())
                DateField(odometerDate, { odometerDate = it })
                TimeField(odometerTime, { odometerTime = it })
                RowSwitch(stringResource(R.string.panel_reset), panelReset) { panelReset = it }
                odometerError?.let { ErrorText(it) }
                WideButton(stringResource(R.string.save), onClick = {
                    val vehicleId = snapshot?.vehicle?.id ?: return@WideButton
                    val km = parseKm(odometerText)
                    if (km !is Parse.Ok) {
                        odometerError = issueText(context, (km as Parse.Err).issue)
                        return@WideButton
                    }
                    val whenMs = enteredMoment(odometerDate, odometerTime, zone, null, System.currentTimeMillis())
                    if (whenMs == null) {
                        odometerError = momentIssue(context, odometerDate, odometerTime, zone)
                        return@WideButton
                    }
                    val issues = viewModel.saveOdometerReading(vehicleId, whenMs, km.value, trip = false, panelReset = panelReset)
                    odometerError = issues.firstOrNull()
                    if (issues.isEmpty()) odometerText = ""
                })
            }
        }
        editingService?.let { record ->
            OutlinedTextField(serviceCost, { serviceCost = it }, label = { Text(stringResource(R.string.cost)) }, modifier = Modifier.fillMaxWidthField())
            DateField(serviceDate, { serviceDate = it })
            TimeField(serviceTime, { serviceTime = it })
            OutlinedTextField(serviceOdo, { serviceOdo = it }, label = { Text(stringResource(R.string.field_odometer)) }, modifier = Modifier.fillMaxWidthField())
            OutlinedTextField(serviceProvider, { serviceProvider = it }, label = { Text(stringResource(R.string.provider)) }, modifier = Modifier.fillMaxWidthField())
            OutlinedTextField(serviceNotes, { serviceNotes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidthField())
            Button(onClick = {
                servicePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_photo)) }
            servicePhotos.forEachIndexed { index, _ ->
                TextButton(onClick = { servicePhotos = servicePhotos.filterIndexed { i, _ -> i != index } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.remove_photo) + " " + (index + 1))
                }
            }
            Button(onClick = {
                val money = parseMoney(serviceCost, record.currency)
                val km = parseKm(serviceOdo)
                val whenMs = enteredMoment(serviceDate, serviceTime, zone, record.occurredAtMs, System.currentTimeMillis())
                if (money !is Parse.Ok || km !is Parse.Ok || whenMs == null) {
                    odometerError = when {
                        whenMs == null -> momentIssue(context, serviceDate, serviceTime, zone)
                        km is Parse.Err -> issueText(context, km.issue)
                        money is Parse.Err -> issueText(context, money.issue)
                        else -> null
                    }
                    return@Button
                }
                viewModel.saveService(
                    record.copy(
                        costMinor = money.value,
                        occurredAtMs = whenMs,
                        odometerKm = km.value,
                        provider = serviceProvider.ifBlank { null },
                        notes = serviceNotes.ifBlank { null },
                        attachmentUris = servicePhotos,
                    ),
                )
                editingService = null
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
        }
        editingExpense?.let { expense ->
            OutlinedTextField(expenseAmount, { expenseAmount = it }, label = { Text(stringResource(R.string.cost)) }, modifier = Modifier.fillMaxWidthField())
            DateField(expenseDate, { expenseDate = it })
            TimeField(expenseTime, { expenseTime = it })
            OutlinedTextField(expenseNotes, { expenseNotes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidthField())
            Button(onClick = {
                val money = parseMoney(expenseAmount, expense.currency)
                val whenMs = enteredMoment(expenseDate, expenseTime, zone, expense.occurredAtMs, System.currentTimeMillis())
                if (money !is Parse.Ok || whenMs == null) {
                    odometerError = if (whenMs == null) momentIssue(context, expenseDate, expenseTime, zone) else issueText(context, (money as Parse.Err).issue)
                    return@Button
                }
                viewModel.saveExpense(
                    expense.copy(
                        amountMinor = money.value,
                        occurredAtMs = whenMs,
                        notes = expenseNotes.ifBlank { null },
                    ),
                )
                editingExpense = null
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
        }
    }
}

private val serviceKinds = listOf(
    "liquid-filter" to R.string.svc_liquid,
    "vapor-filter" to R.string.svc_vapor,
    "injectors" to R.string.svc_injectors,
    "reducer" to R.string.svc_reducer,
    "tank" to R.string.svc_tank,
    "valves" to R.string.svc_valves,
)

@Composable
private fun ServiceKindChips(selected: String, onSelect: (String) -> Unit) {
    ChipFlow {
        serviceKinds.forEach { (code, label) ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

private fun serviceLabel(context: android.content.Context, code: String): String {
    val label = serviceKinds.firstOrNull { it.first == code }?.second
    return if (label == null) code else context.getString(label)
}

private fun applyServiceKind(code: String, setKnown: (String) -> Unit, setCustom: (String) -> Unit) {
    if (serviceKinds.any { it.first == code }) {
        setKnown(code)
        setCustom("")
    } else {
        setCustom(code)
    }
}

@Composable
private fun RowChips(selected: String, onSelect: (String) -> Unit) {
    ChipFlow {
        FilterChip(selected == "fuel", { onSelect("fuel") }, label = { Text(stringResource(R.string.history_fuel)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected == "service", { onSelect("service") }, label = { Text(stringResource(R.string.history_service)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected == "expense", { onSelect("expense") }, label = { Text(stringResource(R.string.history_expense)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
private fun ServiceRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vehicleId = state.app.active?.vehicle?.id ?: return
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    val lastKm = state.app.active?.odometer?.maxByOrNull { it.sortKey() }?.odometerKm?.toString().orEmpty()
    var type by remember { mutableStateOf("liquid-filter") }
    var intervalKm by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf("") }
    var leadKm by remember { mutableStateOf("") }
    var leadDays by remember { mutableStateOf("") }
    var dueKm by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var snooze by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("liquid-filter") }
    var workCost by remember { mutableStateOf("0") }
    var workDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var workTime by remember { mutableStateOf(Dates.formatTime(System.currentTimeMillis(), zone)) }
    var workOdo by remember { mutableStateOf(lastKm) }
    var provider by remember { mutableStateOf("") }
    var workNotes by remember { mutableStateOf("") }
    var workPhotos by remember { mutableStateOf(emptyList<String>()) }
    var isGbo by remember { mutableStateOf(true) }
    var expenseCategory by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("0") }
    var expenseDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var expenseTime by remember { mutableStateOf(Dates.formatTime(System.currentTimeMillis(), zone)) }
    var expenseOdo by remember { mutableStateOf(lastKm) }
    var expenseNotes by remember { mutableStateOf("") }
    var includeOperating by remember { mutableStateOf(false) }
    var includeGbo by remember { mutableStateOf(false) }
    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    var customType by remember { mutableStateOf("") }
    var customWork by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    val currency = deviceCurrency()
    val workPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val copied = copyReceipt(context, uri) ?: return@rememberLauncherForActivityResult
        if (copied !in workPhotos) workPhotos = workPhotos + copied
    }
    ScreenPage {
        Text(stringResource(R.string.nav_service), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        // OPEN-FILTER-DEFAULT: диапазоны из подсказок остаются текстом, в интервал не подставляются.
        SectionCard(stringResource(R.string.hints_title)) {
            Text(stringResource(R.string.hint_liquid))
            Text(stringResource(R.string.hint_vapor))
            Text(stringResource(R.string.hint_injectors))
            Text(stringResource(R.string.hint_reducer))
            Text(stringResource(R.string.hint_tank))
            Text(stringResource(R.string.hint_valves))
            Text(stringResource(R.string.reference_ignored))
        }
        state.app.active?.schedules?.forEach { schedule ->
            TextButton(onClick = {
                applyServiceKind(schedule.serviceType, { type = it }, { customType = it })
                intervalKm = schedule.intervalKm?.toString().orEmpty()
                intervalDays = schedule.intervalDays?.toString().orEmpty()
                leadKm = schedule.leadKm?.toString().orEmpty()
                leadDays = schedule.leadDays?.toString().orEmpty()
                dueKm = schedule.dueOdometerKm?.toString().orEmpty()
                dueDate = schedule.dueDateEpochDay?.let(Dates::formatEpochDay).orEmpty()
                snooze = schedule.snoozeUntilEpochDay?.let(Dates::formatEpochDay).orEmpty()
                editingScheduleId = schedule.id
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.edit) + " · " + serviceLabel(context, schedule.serviceType))
            }
        }
        state.report?.serviceStatuses?.forEach { status ->
            Text(serviceLabel(LocalContext.current, status.serviceType) + " · " + statusLabel(LocalContext.current, status.status))
            Text(stringResource(R.string.forecast) + " 30: " + (status.forecast30.averageKmPerDay?.toPlainString() ?: stringResource(R.string.quality_insufficient)))
            Text(stringResource(R.string.forecast) + " 90: " + (status.forecast90.averageKmPerDay?.toPlainString() ?: stringResource(R.string.quality_insufficient)))
        }
        ServiceKindChips(type) { chosen ->
            val previous = type
            type = chosen
            customType = ""
            if (workType == previous) workType = chosen
        }
        OutlinedTextField(customType, { customType = it }, label = { Text(stringResource(R.string.svc_custom)) }, supportingText = { Text(stringResource(R.string.field_optional)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(intervalKm, { intervalKm = it }, label = { Text(stringResource(R.string.interval_km)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(intervalDays, { intervalDays = it }, label = { Text(stringResource(R.string.interval_days)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(leadKm, { leadKm = it }, label = { Text(stringResource(R.string.lead_km)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(leadDays, { leadDays = it }, label = { Text(stringResource(R.string.lead_days)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(dueKm, { dueKm = it }, label = { Text(stringResource(R.string.field_due_odo)) }, modifier = Modifier.fillMaxWidthField())
        DateField(dueDate, { dueDate = it }, R.string.field_due_date)
        // OPEN-SOON: дату отсрочки выбирает пользователь. Порог «скоро» сам не включается.
        DateField(snooze, { snooze = it }, R.string.snooze)
        formError?.let { ErrorText(it) }
        Button(onClick = {
            val numbers = listOf(
                optionalWhole(intervalKm, FieldCode.SCHEDULE),
                optionalWhole(intervalDays, FieldCode.SCHEDULE),
                optionalWhole(leadKm, FieldCode.SCHEDULE),
                optionalWhole(leadDays, FieldCode.SCHEDULE),
                optionalWhole(dueKm, FieldCode.ODOMETER),
            )
            val numberError = numbers.firstNotNullOfOrNull { parsed ->
                (parsed as? Parse.Err)?.let { issueText(context, it.issue) }
            }
            val dueDay = if (dueDate.isBlank()) null else Dates.parseEpochDay(dueDate)
            val snoozeDay = if (snooze.isBlank()) null else Dates.parseEpochDay(snooze)
            val dateError = when {
                dueDate.isNotBlank() && dueDay == null -> context.getString(R.string.issue_date)
                snooze.isNotBlank() && snoozeDay == null -> context.getString(R.string.issue_date)
                else -> null
            }
            val problem = numberError ?: dateError
            if (problem != null) {
                formError = problem
                return@Button
            }
            val sameType = state.app.active?.schedules?.singleOrNull { it.serviceType == type }
            val kept = state.app.active?.schedules?.firstOrNull { it.id == editingScheduleId } ?: sameType
            viewModel.saveSchedule(
                ServiceScheduleCalc(
                    id = kept?.id ?: UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    serviceType = customType.trim().ifBlank { type },
                    intervalKm = (numbers[0] as? Parse.Ok)?.value,
                    intervalDays = (numbers[1] as? Parse.Ok)?.value,
                    dueOdometerKm = (numbers[4] as? Parse.Ok)?.value,
                    dueDateEpochDay = dueDay,
                    leadKm = (numbers[2] as? Parse.Ok)?.value,
                    leadDays = (numbers[3] as? Parse.Ok)?.value,
                    snoozeUntilEpochDay = snoozeDay,
                    remindersEnabled = kept?.remindersEnabled ?: true,
                ),
            )
            formError = null
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(if (editingScheduleId != null) R.string.save else R.string.add)) }
        Text(stringResource(R.string.service_work), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        ServiceKindChips(workType) { chosen ->
            workType = chosen
            customWork = ""
        }
        OutlinedTextField(customWork, { customWork = it }, label = { Text(stringResource(R.string.svc_custom)) }, supportingText = { Text(stringResource(R.string.field_optional)) }, modifier = Modifier.fillMaxWidthField())
        DateField(workDate, { workDate = it })
        TimeField(workTime, { workTime = it })
        OutlinedTextField(workOdo, { workOdo = it }, label = { Text(stringResource(R.string.field_odometer)) }, suffix = { Text("км") }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(workCost, { workCost = it }, label = { Text(stringResource(R.string.cost)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(provider, { provider = it }, label = { Text(stringResource(R.string.provider)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(workNotes, { workNotes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidthField())
        Button(onClick = {
            workPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_photo)) }
        workPhotos.forEachIndexed { index, _ ->
            TextButton(onClick = { workPhotos = workPhotos.filterIndexed { i, _ -> i != index } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.remove_photo) + " " + (index + 1))
            }
        }
        RowSwitch(stringResource(R.string.is_gbo), isGbo) { isGbo = it }
        Button(onClick = {
            val money = parseMoney(workCost, currency)
            val km = parseKm(workOdo)
            val whenMs = enteredMoment(workDate, workTime, zone, null, System.currentTimeMillis())
            formError = when {
                whenMs == null -> momentIssue(context, workDate, workTime, zone)
                km is Parse.Err -> issueText(context, km.issue)
                money is Parse.Err -> issueText(context, money.issue)
                else -> null
            }
            if (formError != null || money !is Parse.Ok || km !is Parse.Ok || whenMs == null) return@Button
            val issues = viewModel.saveService(
                ServiceRecordCalc(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    serviceType = customWork.trim().ifBlank { workType },
                    occurredAtMs = whenMs,
                    odometerKm = km.value,
                    costMinor = money.value,
                    currency = currency,
                    isGbo = isGbo,
                    scheduleId = state.app.active?.schedules?.singleOrNull { it.serviceType == workType }?.id,
                    createdAtMs = System.currentTimeMillis(),
                    provider = provider.ifBlank { null },
                    notes = workNotes.ifBlank { null },
                    attachmentUris = workPhotos,
                ),
            )
            formError = issues.firstOrNull()
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
        OutlinedTextField(expenseCategory, { expenseCategory = it }, label = { Text(stringResource(R.string.expense_category)) }, modifier = Modifier.fillMaxWidthField())
        DateField(expenseDate, { expenseDate = it })
        TimeField(expenseTime, { expenseTime = it })
        OutlinedTextField(expenseOdo, { expenseOdo = it }, label = { Text(stringResource(R.string.field_odometer)) }, suffix = { Text("км") }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(expenseAmount, { expenseAmount = it }, label = { Text(stringResource(R.string.cost)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(expenseNotes, { expenseNotes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidthField())
        RowSwitch(stringResource(R.string.include_operating), includeOperating) { includeOperating = it }
        RowSwitch(stringResource(R.string.include_gbo), includeGbo) { includeGbo = it }
        Button(onClick = {
            val money = parseMoney(expenseAmount, currency)
            val km = if (expenseOdo.isBlank()) null else parseKm(expenseOdo)
            val whenMs = enteredMoment(expenseDate, expenseTime, zone, null, System.currentTimeMillis())
            formError = when {
                whenMs == null -> momentIssue(context, expenseDate, expenseTime, zone)
                km is Parse.Err -> issueText(context, km.issue)
                money is Parse.Err -> issueText(context, money.issue)
                else -> null
            }
            if (formError != null || money !is Parse.Ok || whenMs == null || km is Parse.Err) return@Button
            viewModel.saveExpense(
                ExpenseCalc(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    category = expenseCategory.ifBlank { "other" },
                    occurredAtMs = whenMs,
                    odometerKm = (km as? Parse.Ok)?.value,
                    amountMinor = money.value,
                    currency = currency,
                    includeInOperating = includeOperating,
                    includeInGbo = includeGbo,
                    createdAtMs = System.currentTimeMillis(),
                    notes = expenseNotes.ifBlank { null },
                ),
            )
            formError = null
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add)) }
    }
}

@Composable
private fun RowSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
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
    var petrolUnit by remember { mutableStateOf("L") }
    var petrolCapacity by remember { mutableStateOf("") }
    var petrolCurrency by remember { mutableStateOf(currency) }
    var petrolPrice by remember { mutableStateOf("") }
    var gasUnit by remember { mutableStateOf(if (gas == FuelType.CNG) "m3" else "L") }
    var gasCapacity by remember { mutableStateOf("") }
    var gasCurrency by remember { mutableStateOf(currency) }
    var gasPrice by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(existing?.id) {
        val vehicle = existing ?: return@LaunchedEffect
        if (profileLoaded) return@LaunchedEffect
        name = vehicle.name
        make = vehicle.make.orEmpty()
        model = vehicle.model.orEmpty()
        year = vehicle.year?.toString().orEmpty()
        engine = vehicle.engine.orEmpty()
        injection = vehicle.injectionType.orEmpty()
        gas = vehicle.gasFuelType ?: FuelType.LPG
        generation = vehicle.gboGeneration.orEmpty()
        manufacturer = vehicle.gboManufacturer.orEmpty()
        equipment = vehicle.gboEquipmentModel.orEmpty()
        useful = vehicle.usefulVolumePercent?.toString().orEmpty()
        switchTemp = vehicle.switchTempC?.toString().orEmpty()
        installDate = vehicle.installedAtMs?.let { Dates.formatDay(it, zone) }.orEmpty()
        val installCurrency = vehicle.installCostCurrency ?: currency
        currency = installCurrency
        installCost = vehicle.installCostMinor?.let {
            formatMinor(it, installCurrency, java.util.Locale.getDefault()).substringBefore(' ')
        }.orEmpty()
        val fuels = state.app.active?.fuels.orEmpty()
        fuels.firstOrNull { it.fuelType == FuelType.PETROL }?.let { fuel ->
            petrolUnit = fuel.unit
            petrolCapacity = plainQuantity(fuel.capacity)
            petrolCurrency = fuel.currency
            petrolPrice = fuel.defaultPriceMinor?.let {
                formatMinor(it, fuel.currency, java.util.Locale.getDefault()).substringBefore(' ')
            }.orEmpty()
        }
        fuels.firstOrNull { it.fuelType == FuelType.LPG || it.fuelType == FuelType.CNG }?.let { fuel ->
            gas = fuel.fuelType
            gasUnit = fuel.unit
            gasCapacity = plainQuantity(fuel.capacity)
            gasCurrency = fuel.currency
            gasPrice = fuel.defaultPriceMinor?.let {
                formatMinor(it, fuel.currency, java.util.Locale.getDefault()).substringBefore(' ')
            }.orEmpty()
        }
        profileLoaded = true
    }
    ScreenPage {
        Text(stringResource(R.string.profile), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        // OPEN-BILLING-WHEN: в интерфейсе один активный профиль. Второе авто — вместе с Pro.
        Text(stringResource(R.string.one_vehicle))
        Text(stringResource(R.string.section_car), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(make, { make = it }, label = { Text(stringResource(R.string.field_make)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.field_model)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(year, { year = it }, label = { Text(stringResource(R.string.field_year)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(engine, { engine = it }, label = { Text(stringResource(R.string.field_engine)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(injection, { injection = it }, label = { Text(stringResource(R.string.field_injection)) }, modifier = Modifier.fillMaxWidthField())
        Text(stringResource(R.string.section_gbo), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        ChipFlow {
        FilterChip(gas == FuelType.LPG, {
            if (gas != FuelType.LPG && (gasUnit == "m3" || gasUnit == "kg")) gasUnit = "L"
            gas = FuelType.LPG
        }, label = { Text(stringResource(R.string.fuel_lpg)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(gas == FuelType.CNG, {
            if (gas != FuelType.CNG && gasUnit == "L") gasUnit = "m3"
            gas = FuelType.CNG
        }, label = { Text(stringResource(R.string.fuel_cng)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        OutlinedTextField(generation, { generation = it }, label = { Text(stringResource(R.string.field_generation)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(manufacturer, { manufacturer = it }, label = { Text(stringResource(R.string.field_manufacturer)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(equipment, { equipment = it }, label = { Text(stringResource(R.string.field_equipment)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(installDate, { installDate = it }, label = { Text(stringResource(R.string.field_install_date)) }, supportingText = { Text(stringResource(R.string.date_hint)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(installCost, { installCost = it }, label = { Text(stringResource(R.string.field_install_cost)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(useful, { useful = it }, label = { Text(stringResource(R.string.field_useful)) }, supportingText = { Text(stringResource(R.string.useful_hint)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(switchTemp, { switchTemp = it }, label = { Text(stringResource(R.string.field_switch)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(currency, { next ->
            val upper = next.uppercase()
            if (petrolCurrency == currency) petrolCurrency = upper
            if (gasCurrency == currency) gasCurrency = upper
            currency = upper
        }, label = { Text(stringResource(R.string.field_currency)) }, modifier = Modifier.fillMaxWidthField())
        Text(stringResource(R.string.fuel_petrol), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        OutlinedTextField(petrolUnit, { petrolUnit = it }, label = { Text(stringResource(R.string.field_unit)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(petrolCapacity, { petrolCapacity = it }, label = { Text(stringResource(R.string.field_capacity)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(petrolCurrency, { petrolCurrency = it.uppercase() }, label = { Text(stringResource(R.string.field_currency)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(petrolPrice, { petrolPrice = it }, label = { Text(stringResource(R.string.field_default_price)) }, suffix = { Text(petrolCurrency) }, modifier = Modifier.fillMaxWidthField())
        Text(if (gas == FuelType.CNG) stringResource(R.string.fuel_cng) else stringResource(R.string.fuel_lpg), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if (gas == FuelType.CNG) {
            // FR-FUEL-09: м³ и кг задаются отдельно и не переводятся друг в друга.
            FilterChip(gasUnit == "m3", { gasUnit = "m3" }, label = { Text(stringResource(R.string.unit_m3)) })
            FilterChip(gasUnit == "kg", { gasUnit = "kg" }, label = { Text(stringResource(R.string.unit_kg)) })
        }
        OutlinedTextField(gasUnit, { gasUnit = it }, label = { Text(stringResource(R.string.field_unit)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(gasCapacity, { gasCapacity = it }, label = { Text(stringResource(R.string.field_capacity)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(gasCurrency, { gasCurrency = it.uppercase() }, label = { Text(stringResource(R.string.field_currency)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(gasPrice, { gasPrice = it }, label = { Text(stringResource(R.string.field_default_price)) }, suffix = { Text(gasCurrency) }, modifier = Modifier.fillMaxWidthField())
        errors.forEach { ErrorText(it) }
        WideButton(stringResource(R.string.save), onClick = {
            val id = existing?.id ?: UUID.randomUUID().toString()
            val cost = installCost.takeIf { it.isNotBlank() }?.let { parseMoney(it, currency) }
            val installed = if (installDate.isBlank()) null else parseDate(installDate, zone)
            val petrolCap = optionalQuantity(petrolCapacity)
            val gasCap = optionalQuantity(gasCapacity)
            val petrolDefault = optionalMoney(petrolPrice, petrolCurrency)
            val gasDefault = optionalMoney(gasPrice, gasCurrency)
            val fieldError = when {
                installDate.isNotBlank() && installed == null -> context.getString(R.string.issue_date)
                cost is Parse.Err -> issueText(context, cost.issue)
                petrolCap is Parse.Err -> issueText(context, petrolCap.issue)
                gasCap is Parse.Err -> issueText(context, gasCap.issue)
                petrolDefault is Parse.Err -> issueText(context, petrolDefault.issue)
                gasDefault is Parse.Err -> issueText(context, gasDefault.issue)
                else -> null
            }
            if (fieldError != null) {
                errors = listOf(fieldError)
                return@WideButton
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
                installedAtMs = installed,
                installCostMinor = (cost as? Parse.Ok)?.value,
                installCostCurrency = currency,
                usefulVolumePercent = useful.toDoubleOrNull(),
                switchTempC = switchTemp.toDoubleOrNull(),
                archivedAtMs = existing?.archivedAtMs,
                createdAtMs = existing?.createdAtMs ?: 0L,
                updatedAtMs = existing?.updatedAtMs ?: 0L,
            )
            val storedFuels = state.app.active?.fuels.orEmpty()
            val fuels = listOf(
                keptFuel(storedFuels, FuelType.PETROL, petrolUnit, (petrolCap as? Parse.Ok)?.value, petrolCurrency, (petrolDefault as? Parse.Ok)?.value),
                keptFuel(storedFuels, gas, gasUnit, (gasCap as? Parse.Ok)?.value, gasCurrency, (gasDefault as? Parse.Ok)?.value),
            ).distinctBy { it.fuelType }
            errors = viewModel.saveVehicle(profile, fuels, state.app.active?.settings ?: app.duofuel.domain.model.CalculationSettingsCalc(null, null, currency, true, false))
        })
        if (existing != null) {
            WideOutlinedButton(stringResource(R.string.archive), onClick = { viewModel.archiveActive() })
            WideOutlinedButton(stringResource(R.string.delete), onClick = { confirmDelete = true })
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

@Composable
private fun AnalyticsRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report = state.report
    val context = LocalContext.current
    val today = LocalDate.now()
    val entries = state.app.active?.entries.orEmpty()
    val settings = state.app.active?.settings
    AnalyticsScreen(
        periodTitle = report?.let { periodTitle(context, it.period.labelKey) } ?: stringResource(R.string.period_month),
        distanceLine = report?.let { distanceLine(context, it) } ?: stringResource(R.string.quality_insufficient),
        fillLines = report?.let { fillLines(context, entries, it.period) }.orEmpty(),
        costLines = report?.let { costLines(context, it) }.orEmpty(),
        cycleLines = report?.consumptionByFuel?.mapNotNull { cycleLine(context, it) }.orEmpty(),
        consumptionLines = report?.consumptionByFuel?.map { consumptionLine(context, it) }.orEmpty(),
        unclosedLines = report?.unclosed?.map { fuelLabel(context, it.fuel) + " · " + context.getString(R.string.unclosed) }.orEmpty(),
        scenarioLines = report?.let { scenarioLines(context, it, settings) }.orEmpty(),
        chartPoints = report?.closedCycles?.mapNotNull { cycle ->
            cycle.rate.ratePer100?.takeIf { cycle.rate.quality == Quality.FACTUAL }
        }.orEmpty(),
        periodMismatch = report?.periodMismatch == true,
        onLastN = { viewModel.setPeriod(PeriodRequest.LastNKm(it)) },
        onCustom = { start, end -> viewModel.setPeriod(PeriodRequest.Custom(start, end)) },
        initialStart = today.minusDays(30).toString(),
        initialEnd = today.toString(),
    )
}

@Composable
private fun RoiRoute(viewModel: DuoFuelViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roi = state.report?.roi
    var baseline by remember { mutableStateOf(state.app.active?.settings?.baselinePetrolL100?.toString().orEmpty()) }
    var price by remember { mutableStateOf("") }
    val currency = state.app.active?.vehicle?.installCostCurrency ?: deviceCurrency()
    ScreenPage {
        Text(stringResource(R.string.roi_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        SectionCard {
            Text(stringResource(R.string.roi_no_date))
            if (roi?.doesNotPayOff == true) Text(stringResource(R.string.no_payoff))
            if (roi?.window == RoiWindow.ENTIRE_HISTORY) Text(stringResource(R.string.roi_entire_history))
            roi?.remainingKm?.remainingKm?.let { Text(stringResource(R.string.roi_remaining, it.toPlainString())) }
            if (roi?.unreimbursedMinor == 0L) Text(stringResource(R.string.roi_covered))
            roi?.investmentMinor?.let { Text(formatMinor(it, roi.investmentCurrency ?: currency, java.util.Locale.getDefault())) }
            if (roi?.saving?.priceIsScenario == true) Text(stringResource(R.string.scenario_price))
            val factual = roi?.saving?.factualAveragePetrolPrice
            Text(stringResource(R.string.factual_price) + ": " + (factual?.moneyMinor?.let { formatMinor(it, factual.currency ?: currency, java.util.Locale.getDefault()) } ?: stringResource(R.string.quality_insufficient)))
        }
        OutlinedTextField(baseline, { baseline = it }, label = { Text(stringResource(R.string.baseline)) }, modifier = Modifier.fillMaxWidthField())
        OutlinedTextField(price, { price = it }, label = { Text(stringResource(R.string.scenario_price)) }, suffix = { Text(currency) }, modifier = Modifier.fillMaxWidthField())
        WideButton(stringResource(R.string.save), onClick = {
            val active = state.app.active ?: return@WideButton
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
        })
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
    ScreenPage {
        Text(stringResource(R.string.settings), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        SectionCard {
        RowSwitch(stringResource(R.string.settings_full_default), state.app.prefillFull) { viewModel.setPrefillFull(it) }
        Text(stringResource(R.string.reminders_hint))
        RowSwitch(stringResource(R.string.settings_reminders), state.app.remindersEnabled) { enabled ->
            if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setReminders(enabled)
            }
        }
        }
        WideButton(stringResource(R.string.csv), onClick = { scope.launch { shareBytes(context, "duofuel-csv.zip", viewModel.exportCsv()) } })
        WideButton(stringResource(R.string.local_archive), onClick = { scope.launch { shareBytes(context, "duofuel-archive.zip", viewModel.exportArchive()) } })
        WideOutlinedButton(stringResource(R.string.import_archive), onClick = { picker.launch(arrayOf("application/zip")) })
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

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(stringResource(R.string.field_time)) },
        supportingText = { Text(stringResource(R.string.time_hint)) },
        modifier = Modifier.fillMaxWidthField(),
    )
}

private fun enteredMoment(date: String, time: String, zone: ZoneId, existingMs: Long?, nowMs: Long): Long? {
    if (Dates.parseStartOfDay(date, zone) == null || !Dates.clockAccepted(time)) return null
    return Dates.resolveOccurredAt(date, zone, existingMs, nowMs, time)
}

private fun momentIssue(context: android.content.Context, date: String, time: String, zone: ZoneId): String {
    if (Dates.parseStartOfDay(date, zone) == null) return context.getString(R.string.issue_date)
    if (!Dates.clockAccepted(time)) return context.getString(R.string.issue_time)
    return context.getString(R.string.issue_date)
}

@Composable
private fun DateField(value: String, onChange: (String) -> Unit, labelRes: Int = R.string.field_date) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(stringResource(labelRes)) },
        supportingText = { Text(stringResource(R.string.date_hint)) },
        modifier = Modifier.fillMaxWidthField(),
    )
}

private fun optionalWhole(text: String, field: FieldCode): Parse<Int>? {
    if (text.isBlank()) return null
    val value = text.trim().toIntOrNull()
        ?: return Parse.Err(ValidationIssue(field, IssueCode.NOT_A_NUMBER, true))
    if (value < 0) return Parse.Err(ValidationIssue(field, IssueCode.MUST_BE_NON_NEGATIVE, true))
    return Parse.Ok(value)
}

private fun optionalQuantity(text: String): Parse<Double>? = if (text.isBlank()) null else parseQuantity(text)

private fun optionalMoney(text: String, currency: String): Parse<Long>? =
    if (text.isBlank()) null else parseMoney(text, currency)

private fun plainQuantity(value: Double?): String =
    value?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()

private fun keptFuel(
    existing: List<FuelConfigProfile>,
    type: FuelType,
    unit: String,
    capacity: Double?,
    currency: String,
    price: Long?,
) = FuelConfigProfile(
    id = existing.firstOrNull { it.fuelType == type }?.id ?: UUID.randomUUID().toString(),
    fuelType = type,
    unit = unit.ifBlank { if (type == FuelType.CNG) "m3" else "L" },
    capacity = capacity,
    currency = currency,
    defaultPriceMinor = price,
)

private fun periodKey(period: PeriodRequest): String = when (period) {
    is PeriodRequest.CalendarMonth -> "month"
    is PeriodRequest.Custom -> "custom"
    is PeriodRequest.LastNKm -> "last_n"
    PeriodRequest.LatestControlCycles -> "cycle"
    PeriodRequest.AllHistory -> "all"
}

private fun periodTitle(context: android.content.Context, labelKey: String): String = context.getString(
    when (labelKey) {
        "cycle" -> R.string.period_cycle
        "all", "roi_all" -> R.string.period_all
        "custom" -> R.string.period_custom
        "last_n" -> R.string.period_last_n
        "roi_install" -> R.string.roi_title
        else -> R.string.period_month
    },
)

private fun recentLine(context: android.content.Context, event: RecentEventRef): String {
    val zone = ZoneId.systemDefault()
    val kind = context.getString(
        when (event.kind) {
            "SERVICE" -> R.string.history_service
            "EXPENSE" -> R.string.history_expense
            else -> R.string.history_fuel
        },
    )
    val fuel = event.fuelType?.let { " · " + fuelLabel(context, it) }.orEmpty()
    val km = event.odometerKm?.let { " · $it км" }.orEmpty()
    return Dates.formatDay(event.occurredAtMs, zone) + " " + Dates.formatTime(event.occurredAtMs, zone) + " · " + kind + fuel + km
}

private fun distanceLine(context: android.content.Context, report: DashboardReport): String {
    val metric = report.cashflowPerKm
    val km = metric.distanceKm?.let { context.getString(R.string.report_km, it) }
    return metricValue(context, metric, km)
}

private fun fillLines(context: android.content.Context, entries: List<FuelEntryCalc>, period: Period): List<String> {
    val rows = entries.filter { occurredIn(it.occurredAtMs, period) }
    return rows.groupBy { it.fuelType to it.unit }
        .entries
        .sortedWith(compareBy({ it.key.first.name }, { it.key.second }))
        .map { (key, group) ->
            context.getString(
                R.string.report_fill_line,
                fuelLabel(context, key.first),
                group.size,
                plainQuantity(group.sumOf { it.quantity }),
                key.second,
            )
        }
}

private fun costLines(context: android.content.Context, report: DashboardReport): List<String> {
    val cash = metricLine(
        context,
        context.getString(R.string.metric_cashflow),
        report.cashflow.total,
        moneyText(report.cashflow.total),
    )
    val consumed = report.consumptionByFuel.map { card ->
        metricLine(
            context,
            fuelLabel(context, card.fuel) + " · " + context.getString(R.string.report_consumed),
            card.consumedMoney,
            moneyText(card.consumedMoney),
        )
    }
    return listOf(cash) + consumed
}

private fun cycleLine(context: android.content.Context, card: FuelConsumptionCard): String? {
    val cycle = card.cycle ?: return null
    val bits = mutableListOf(fuelLabel(context, card.fuel))
    cycle.distanceKm?.let { bits += context.getString(R.string.report_km, it) }
    val volume = cycle.volume
    if (volume.volume != null && volume.volumeUnit != null) {
        bits += plainQuantity(volume.volume) + " " + volume.volumeUnit
    }
    val rate = cycle.rate
    if (rate.ratePer100 != null && rate.volumeUnit != null) {
        bits += context.getString(R.string.report_rate, plainQuantity(rate.ratePer100), rate.volumeUnit)
    }
    bits += qualityLabel(context, rate.quality)
    bits += rate.methodId
    return bits.joinToString(" · ")
}

private fun consumptionLine(context: android.content.Context, card: FuelConsumptionCard): String {
    val rate = card.rate
    val unit = rate.volumeUnit ?: card.volume.volumeUnit
    val value = if (rate.ratePer100 != null && unit != null) {
        context.getString(R.string.report_rate, plainQuantity(rate.ratePer100), unit)
    } else {
        null
    }
    return metricLine(context, fuelLabel(context, card.fuel), rate, value)
}

private fun scenarioLines(
    context: android.content.Context,
    report: DashboardReport,
    settings: CalculationSettingsCalc?,
): List<String> {
    val saving = report.saving
    val baselineRate = settings?.baselinePetrolL100?.takeIf { it > 0.0 }?.let {
        plainQuantity(it) + " " + context.getString(R.string.report_l100)
    }
    // Ручной базовый расход — оценочный, пока его не заменил фактический бензиновый цикл.
    val baseline = manualLine(context, context.getString(R.string.report_baseline_rate), baselineRate, MethodId.BASELINE_PETROL.id)
    val priceValue = settings?.petrolPriceMinor?.let { minor ->
        settings.petrolPriceCurrency?.let { formatMinor(minor, it, java.util.Locale.getDefault()) }
    }
    val price = if (saving.priceIsScenario) {
        manualLine(context, context.getString(R.string.scenario_price), priceValue, MethodId.BASELINE_PETROL.id)
    } else {
        null
    }
    return listOfNotNull(
        baseline,
        price,
        metricLine(
            context,
            context.getString(R.string.factual_price),
            saving.factualAveragePetrolPrice,
            moneyText(saving.factualAveragePetrolPrice),
        ),
        metricLine(context, context.getString(R.string.report_gbo), saving.actual, moneyText(saving.actual)),
        metricLine(context, context.getString(R.string.metric_saving), saving.net, moneyText(saving.net)),
    )
}

private fun metricLine(context: android.content.Context, title: String, metric: Metric, value: String?): String =
    "$title · " + metricValue(context, metric, value)

private fun metricValue(context: android.content.Context, metric: Metric, value: String?): String {
    val quality = qualityLabel(context, metric.quality)
    val shown = if (value == null) quality else "$value · $quality"
    return "$shown · ${metric.methodId}"
}

private fun manualLine(context: android.content.Context, title: String, value: String?, methodId: String): String {
    val quality = context.getString(if (value == null) R.string.quality_insufficient else R.string.quality_estimated)
    val shown = if (value == null) quality else "$value · $quality"
    return "$title · $shown · $methodId"
}

private fun moneyText(metric: Metric): String? {
    val minor = metric.moneyMinor ?: return null
    val currency = metric.currency ?: return null
    return formatMinor(minor, currency, java.util.Locale.getDefault())
}

private fun occurredIn(at: Long, period: Period): Boolean {
    val start = period.startInclusiveMs
    val end = period.endExclusiveMs
    return (start == null || at >= start) && (end == null || at < end)
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
