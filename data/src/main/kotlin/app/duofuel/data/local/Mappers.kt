package app.duofuel.data.local

import app.duofuel.domain.model.CalculationSettingsCalc
import app.duofuel.domain.model.ExpenseCalc
import app.duofuel.domain.model.FuelEntryCalc
import app.duofuel.domain.model.FuelType
import app.duofuel.domain.model.InputMethod
import app.duofuel.domain.model.OdometerEventCalc
import app.duofuel.domain.model.OdometerKind
import app.duofuel.domain.model.RecordSource
import app.duofuel.domain.model.ServiceRecordCalc
import app.duofuel.domain.model.ServiceScheduleCalc
import app.duofuel.domain.repository.FuelConfigProfile
import app.duofuel.domain.repository.VehicleProfile

fun VehicleEntity.toProfile() = VehicleProfile(
    id = id,
    name = name,
    make = make,
    model = model,
    year = year,
    engine = engine,
    injectionType = injectionType,
    gasFuelType = gasFuelType?.let { FuelType.valueOf(it) },
    gboGeneration = gboGeneration,
    gboManufacturer = gboManufacturer,
    gboEquipmentModel = gboEquipmentModel,
    installedAtMs = installedAt,
    installCostMinor = installCostMinor,
    installCostCurrency = installCostCurrency,
    usefulVolumePercent = usefulVolumePercent,
    switchTempC = switchTempC,
    archivedAtMs = archivedAt,
    createdAtMs = createdAt,
    updatedAtMs = updatedAt,
)

fun FuelTypeConfigEntity.toProfile() = FuelConfigProfile(
    id = id,
    fuelType = FuelType.valueOf(fuelType),
    unit = unit,
    capacity = capacity,
    currency = currency,
    defaultPriceMinor = defaultPriceMinor,
)

fun FuelEntryEntity.toCalc() = FuelEntryCalc(
    id = id,
    vehicleId = vehicleId,
    fuelType = FuelType.valueOf(fuelType),
    occurredAtMs = occurredAt,
    odometerKm = odometerKm,
    quantity = quantity,
    unit = unit,
    unitPriceMinor = unitPriceMinor,
    totalCostMinor = totalCostMinor,
    currency = currency,
    isFull = isFull,
    createdAtMs = createdAt,
    station = stationName,
    notes = notes,
    receiptUri = receiptUri,
)

fun OdometerEventEntity.toCalc() = OdometerEventCalc(
    id = id,
    vehicleId = vehicleId,
    occurredAtMs = occurredAt,
    odometerKm = odometerKm,
    kind = OdometerKind.valueOf(kind),
    inputMethod = InputMethod.valueOf(inputMethod),
    source = RecordSource.valueOf(source),
    linkedFuelEntryId = linkedFuelEntryId,
    createdAtMs = createdAt,
)

fun ServiceRecordEntity.toCalc() = ServiceRecordCalc(
    id = id,
    vehicleId = vehicleId,
    serviceType = serviceType,
    occurredAtMs = occurredAt,
    odometerKm = odometerKm,
    costMinor = costMinor,
    currency = currency,
    isGbo = isGbo,
    scheduleId = scheduleId,
    createdAtMs = createdAt,
    provider = provider,
    notes = notes,
)

fun ServiceScheduleEntity.toCalc() = ServiceScheduleCalc(
    id = id,
    vehicleId = vehicleId,
    serviceType = serviceType,
    intervalKm = intervalKm,
    intervalDays = intervalDays,
    dueOdometerKm = dueOdometer,
    dueDateEpochDay = dueDateEpochDay,
    leadKm = leadKm,
    leadDays = leadDays,
    snoozeUntilEpochDay = snoozeUntilEpochDay,
    remindersEnabled = remindersEnabled,
)

fun ExpenseEntryEntity.toCalc() = ExpenseCalc(
    id = id,
    vehicleId = vehicleId,
    category = category,
    occurredAtMs = occurredAt,
    odometerKm = odometerKm,
    amountMinor = amountMinor,
    currency = currency,
    includeInOperating = includeInOperating,
    includeInGbo = includeInGbo,
    createdAtMs = createdAt,
    notes = notes,
)

fun CalculationSettingsEntity?.toCalc() = CalculationSettingsCalc(
    baselinePetrolL100 = this?.baselinePetrolL100,
    petrolPriceMinor = this?.petrolPriceMinor,
    petrolPriceCurrency = this?.petrolPriceCurrency,
    petrolPriceIsScenario = this?.petrolPriceIsScenario ?: true,
    baselineFromFactualHistory = this?.baselineFromFactualHistory ?: false,
)
