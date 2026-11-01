import '../model/records.dart';

/// Порт [FuelCycleCalculator] с main.
/// Версия правил та же, формула не заменялась.
const fuelCycleMethodId = 'fuel_cycle_full_to_full';
const kFormulaVersion = 'srs-2.0-open-cycle-1';

enum CycleWarning { distanceNonPositive, scaleBreak, mixedUnits }

class CycleResult {
  const CycleResult({
    required this.fuel,
    required this.openingId,
    required this.closingId,
    required this.consumedEntryIds,
    required this.openingOdometerKm,
    required this.closingOdometerKm,
    required this.startMs,
    required this.endMs,
    required this.distanceKm,
    required this.volume,
    required this.volumeQuality,
    required this.ratePer100,
    required this.rateQuality,
    required this.rateWarnings,
  });

  final FuelType fuel;
  final String openingId;
  final String closingId;
  final List<String> consumedEntryIds;
  final int openingOdometerKm;
  final int closingOdometerKm;
  final int startMs;
  final int endMs;

  /// Положительная дистанция непрерывной шкалы. Иначе null, не ноль.
  final int? distanceKm;
  final double? volume;
  final Quality volumeQuality;
  final double? ratePer100;
  final Quality rateQuality;
  final List<CycleWarning> rateWarnings;

  String get methodId => fuelCycleMethodId;
  String get formulaVersion => kFormulaVersion;
}

class UnclosedCycle {
  const UnclosedCycle({
    required this.fuel,
    required this.openingBoundaryId,
    required this.partialIds,
  });

  final FuelType fuel;
  final String? openingBoundaryId;
  final List<String> partialIds;
}

class FuelAverage {
  const FuelAverage({
    required this.quality,
    required this.litersPer100,
    required this.volumeLiters,
    required this.distanceKm,
    required this.cycleCount,
  });

  const FuelAverage.insufficient()
    : quality = Quality.insufficient,
      litersPer100 = null,
      volumeLiters = null,
      distanceKm = null,
      cycleCount = 0;

  final Quality quality;
  final double? litersPer100;
  final double? volumeLiters;
  final int? distanceKm;
  final int cycleCount;

  String get methodId => fuelCycleMethodId;
  String get formulaVersion => kFormulaVersion;
}

class FuelConsumption {
  const FuelConsumption({
    required this.fuel,
    required this.latest,
    required this.average,
    required this.cycles,
    required this.unclosed,
  });

  final FuelType fuel;
  final CycleResult? latest;
  final FuelAverage average;
  final List<CycleResult> cycles;
  final UnclosedCycle? unclosed;
}

int compareFuel(FuelEntry a, FuelEntry b) => a.sortKey.compareTo(b.sortKey);

/// OPEN-CYCLE: объём открывающей заправки не входит.
/// В сумму входят заправки строго после неё и закрывающая включительно.
List<CycleResult> cycles(
  List<FuelEntry> entries,
  List<OdometerReset> resets,
  FuelType fuel,
) {
  final sorted = entries.where((entry) => entry.fuelType == fuel).toList()
    ..sort(compareFuel);
  final fullIndexes = <int>[
    for (var index = 0; index < sorted.length; index++)
      if (sorted[index].isFull) index,
  ];
  return [
    for (var pair = 0; pair < fullIndexes.length - 1; pair++)
      _close(sorted, fullIndexes[pair], fullIndexes[pair + 1], resets),
  ];
}

UnclosedCycle? unclosed(List<FuelEntry> entries, FuelType fuel) {
  final sorted = entries.where((entry) => entry.fuelType == fuel).toList()
    ..sort(compareFuel);
  if (sorted.isEmpty) return null;
  final lastFull = sorted.lastIndexWhere((entry) => entry.isFull);
  final tail = lastFull < 0 ? sorted : sorted.sublist(lastFull + 1);
  if (tail.isEmpty) return null;
  return UnclosedCycle(
    fuel: fuel,
    openingBoundaryId: lastFull >= 0 ? sorted[lastFull].id : null,
    partialIds: [for (final entry in tail) entry.id],
  );
}

/// Средний расход — та же формула, не отдельная.
/// Сумма V_consumed фактических циклов / сумма их дистанций × 100.
/// Цикл без фактического расхода в суммы не входит и не становится нулём.
FuelAverage averageConsumption(List<CycleResult> closed) {
  final factual = closed.where((cycle) {
    final rate = cycle.ratePer100;
    final volume = cycle.volume;
    final distance = cycle.distanceKm;
    return cycle.rateQuality == Quality.factual &&
        rate != null &&
        rate.isFinite &&
        volume != null &&
        volume.isFinite &&
        distance != null &&
        distance > 0;
  }).toList();
  if (factual.isEmpty) return const FuelAverage.insufficient();
  final volume = factual.fold<double>(0, (sum, cycle) => sum + cycle.volume!);
  final distance = factual.fold<int>(
    0,
    (sum, cycle) => sum + cycle.distanceKm!,
  );
  if (distance <= 0 || !volume.isFinite) {
    return const FuelAverage.insufficient();
  }
  final rate = volume / distance * 100.0;
  if (!rate.isFinite) return const FuelAverage.insufficient();
  return FuelAverage(
    quality: Quality.factual,
    litersPer100: rate,
    volumeLiters: volume,
    distanceKm: distance,
    cycleCount: factual.length,
  );
}

FuelConsumption consumptionFor(
  List<FuelEntry> entries,
  List<OdometerReset> resets,
  FuelType fuel,
) {
  final closed = cycles(entries, resets, fuel);
  return FuelConsumption(
    fuel: fuel,
    latest: closed.isEmpty ? null : closed.last,
    average: averageConsumption(closed),
    cycles: closed,
    unclosed: unclosed(entries, fuel),
  );
}

bool scaleBroken(
  FuelEntry opening,
  FuelEntry closing,
  List<OdometerReset> resets,
) {
  final openKey = opening.sortKey;
  final closeKey = closing.sortKey;
  for (final reset in resets) {
    if (reset.linkedFuelEntryId == opening.id ||
        reset.linkedFuelEntryId == closing.id) {
      continue;
    }
    final key = reset.sortKey;
    if (key.compareTo(openKey) > 0 && key.compareTo(closeKey) < 0) return true;
  }
  return false;
}

CycleResult _close(
  List<FuelEntry> sorted,
  int openIndex,
  int closeIndex,
  List<OdometerReset> resets,
) {
  final opening = sorted[openIndex];
  final closing = sorted[closeIndex];
  final consumed = sorted.sublist(openIndex + 1, closeIndex + 1);
  final units = consumed.map((entry) => entry.unit).toSet();
  final broken = scaleBroken(opening, closing, resets);
  final rawDistance = broken ? null : closing.odometerKm - opening.odometerKm;
  final warnings = <CycleWarning>[
    if (units.length > 1) CycleWarning.mixedUnits,
    if (broken) CycleWarning.scaleBreak,
    if (rawDistance != null && rawDistance <= 0)
      CycleWarning.distanceNonPositive,
  ];
  final volumeValue = _volume(consumed, units);
  final distanceKm = rawDistance != null && rawDistance > 0
      ? rawDistance
      : null;
  double? rate;
  if (volumeValue != null && distanceKm != null) {
    final per100 = volumeValue / distanceKm * 100.0;
    if (per100.isFinite) rate = per100;
  }
  return CycleResult(
    fuel: opening.fuelType,
    openingId: opening.id,
    closingId: closing.id,
    consumedEntryIds: [for (final entry in consumed) entry.id],
    openingOdometerKm: opening.odometerKm,
    closingOdometerKm: closing.odometerKm,
    startMs: opening.occurredAtMs,
    endMs: closing.occurredAtMs,
    distanceKm: distanceKm,
    volume: volumeValue,
    volumeQuality: volumeValue == null ? Quality.insufficient : Quality.factual,
    ratePer100: rate,
    rateQuality: rate == null ? Quality.insufficient : Quality.factual,
    rateWarnings: rate == null ? List.unmodifiable(warnings) : const [],
  );
}

double? _volume(List<FuelEntry> consumed, Set<String> units) {
  if (units.length != 1 || consumed.isEmpty) return null;
  var sum = 0.0;
  for (final entry in consumed) {
    if (!entry.quantity.isFinite) return null;
    sum += entry.quantity;
  }
  if (!sum.isFinite) return null;
  return sum;
}
