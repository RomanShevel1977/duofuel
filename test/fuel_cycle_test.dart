import 'package:duofuel/src/calc/fuel_cycle.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('full to full excludes opening and includes closing', () {
    final entries = [
      fuel('open', 1, 1000, 40, true),
      fuel('partial', 2, 1100, 10, false),
      fuel('close', 3, 1300, 20, true),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.consumedEntryIds, ['partial', 'close']);
    expect(cycle.consumedEntryIds.contains('open'), isFalse);
    expect(cycle.volume, closeTo(30, 1e-9));
    expect(cycle.ratePer100, closeTo(10, 1e-9));
    expect(cycle.distanceKm, 300);
    expect(cycle.rateQuality, Quality.factual);
    expect(cycle.methodId, fuelCycleMethodId);
    expect(cycle.formulaVersion, kFormulaVersion);
  });

  test('several partials sum all named liters', () {
    final entries = [
      fuel('open', 1, 0, 50, true),
      fuel('a', 2, 100, 5, false),
      fuel('b', 3, 180, 7, false),
      fuel('close', 4, 250, 8, true),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.volume, closeTo(20, 1e-9));
    expect(cycle.ratePer100, closeTo(8, 1e-9));
    expect(cycle.consumedEntryIds, ['a', 'b', 'close']);
  });

  test('shared boundary fill is consumed only in the cycle it closes', () {
    final entries = [
      fuel('a', 1, 0, 100, true),
      fuel('b', 2, 300, 30, true),
      fuel('partial', 3, 400, 10, false),
      fuel('c', 4, 500, 20, true),
    ];
    final closed = cycles(entries, const [], FuelType.lpg);
    expect(closed[0].consumedEntryIds, ['b']);
    expect(closed[0].volume, closeTo(30, 1e-9));
    expect(closed[0].ratePer100, closeTo(10, 1e-9));
    expect(closed[1].consumedEntryIds, ['partial', 'c']);
    expect(closed[1].volume, closeTo(30, 1e-9));
    expect(closed[1].ratePer100, closeTo(15, 1e-9));
    final average = averageConsumption(closed);
    expect(average.volumeLiters, closeTo(60, 1e-9));
    expect(average.distanceKm, 500);
    expect(average.litersPer100, closeTo(12, 1e-9));
    expect(average.cycleCount, 2);
    expect(average.quality, Quality.factual);
    expect(average.methodId, fuelCycleMethodId);
  });

  test('unclosed cycle has no factual rate and is not zero', () {
    final entries = [
      fuel('open', 1, 0, 40, true),
      fuel('partial', 2, 100, 10, false),
    ];
    expect(cycles(entries, const [], FuelType.lpg), isEmpty);
    expect(unclosed(entries, FuelType.lpg)!.partialIds, ['partial']);
    final average = averageConsumption(cycles(entries, const [], FuelType.lpg));
    expect(average.litersPer100, isNull);
    expect(average.volumeLiters, isNull);
    expect(average.quality, Quality.insufficient);
  });

  test('zero distance is insufficient and not infinite', () {
    final entries = [
      fuel('open', 1, 100, 40, true),
      fuel('close', 2, 100, 12, true),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.rateQuality, Quality.insufficient);
    expect(cycle.ratePer100, isNull);
    expect(cycle.rateWarnings, contains(CycleWarning.distanceNonPositive));
    expect(cycle.volume, closeTo(12, 1e-9));
    expect(averageConsumption([cycle]).litersPer100, isNull);
  });

  test('negative distance is not divided', () {
    final entries = [
      fuel('open', 1, 500, 40, true),
      fuel('close', 2, 100, 12, true),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.distanceKm, isNull);
    expect(cycle.ratePer100, isNull);
    expect(cycle.rateWarnings, contains(CycleWarning.distanceNonPositive));
    expect(cycle.volume, closeTo(12, 1e-9));
  });

  test('petrol and lpg stay separate', () {
    final entries = [
      fuel('g-open', 1, 0, 30, true, type: FuelType.lpg),
      fuel('p-open', 1, 0, 20, true, type: FuelType.petrol),
      fuel('g-close', 2, 300, 15, true, type: FuelType.lpg),
      fuel('p-mid', 3, 100, 5, false, type: FuelType.petrol),
      fuel('p-close', 4, 400, 6, true, type: FuelType.petrol),
    ];
    final lpg = cycles(entries, const [], FuelType.lpg).single;
    final petrol = cycles(entries, const [], FuelType.petrol).single;
    expect(lpg.volume, closeTo(15, 1e-9));
    expect(petrol.volume, closeTo(11, 1e-9));
    expect(lpg.distanceKm, 300);
    expect(petrol.distanceKm, 400);
    expect(
      averageConsumption([lpg]).litersPer100,
      isNot(averageConsumption([petrol]).litersPer100),
    );
  });

  test('payments without a closed cycle are not zero consumption', () {
    final entries = [fuel('only', 5, 10, 12, false)];
    expect(cycles(entries, const [], FuelType.lpg), isEmpty);
    final average = averageConsumption(const []);
    expect(average.litersPer100, isNull);
    expect(average.volumeLiters, isNull);
    expect(average.distanceKm, isNull);
    expect(unclosed(entries, FuelType.lpg)!.partialIds, ['only']);
  });

  test('editing a past entry recalculates only that fuel', () {
    final original = [
      fuel('g-open', 1, 0, 40, true, type: FuelType.lpg),
      fuel('g-close', 2, 200, 10, true, type: FuelType.lpg),
      fuel('p-open', 1, 0, 40, true, type: FuelType.petrol),
      fuel('p-close', 2, 200, 8, true, type: FuelType.petrol),
    ];
    final edited = [
      for (final entry in original)
        if (entry.id == 'g-close') entry.copyWith(quantity: 16) else entry,
    ];
    final beforePetrol = cycles(
      original,
      const [],
      FuelType.petrol,
    ).single.volume;
    final afterPetrol = cycles(edited, const [], FuelType.petrol).single.volume;
    final afterGas = cycles(edited, const [], FuelType.lpg).single.volume;
    expect(afterPetrol, beforePetrol);
    expect(afterGas, closeTo(16, 1e-9));
  });

  test('backdated partial joins chronology', () {
    final entries = [
      fuel('open', 1, 0, 30, true),
      fuel('close', 4, 200, 8, true),
      fuel('late-added', 2, 80, 4, false),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.consumedEntryIds, ['late-added', 'close']);
    expect(cycle.volume, closeTo(12, 1e-9));
  });

  test('equal time and odometer sort by created time then id', () {
    final entries = [
      fuel('b', 10, 100, 8, true, created: 2),
      fuel('a', 10, 100, 4, true, created: 1),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.openingId, 'a');
    expect(cycle.closingId, 'b');
    expect(cycle.consumedEntryIds, ['b']);
    expect(cycle.volume, closeTo(8, 1e-9));
    expect(cycle.ratePer100, isNull);
  });

  test('reset between boundaries removes the rate but not the volume', () {
    final entries = [
      fuel('open', 1, 100, 40, true),
      fuel('close', 3, 400, 20, true),
    ];
    final broken = [reset('panel', 2, 0)];
    final cycle = cycles(entries, broken, FuelType.lpg).single;
    expect(cycle.volume, closeTo(20, 1e-9));
    expect(cycle.distanceKm, isNull);
    expect(cycle.ratePer100, isNull);
    expect(cycle.rateWarnings, contains(CycleWarning.scaleBreak));
    expect(averageConsumption([cycle]).litersPer100, isNull);

    final intact = cycles(entries, const [], FuelType.lpg).single;
    expect(intact.distanceKm, 300);
    expect(intact.ratePer100, closeTo(20 / 300 * 100, 1e-9));
  });

  test('reset linked to a boundary does not break that cycle', () {
    final entries = [
      fuel('open', 1, 100, 40, true),
      fuel('close', 3, 400, 20, true),
    ];
    final linked = [reset('panel', 2, 0, linked: 'close')];
    final cycle = cycles(entries, linked, FuelType.lpg).single;
    expect(cycle.ratePer100, isNotNull);
    expect(cycle.distanceKm, 300);
  });

  test('average skips a broken cycle and keeps the factual one', () {
    final entries = [
      fuel('open', 1, 0, 40, true),
      fuel('close', 2, 100, 10, true),
      fuel('next', 4, 400, 20, true),
    ];
    final closed = cycles(entries, [reset('panel', 3, 0)], FuelType.lpg);
    expect(closed, hasLength(2));
    expect(closed[0].ratePer100, closeTo(10, 1e-9));
    expect(closed[1].ratePer100, isNull);
    final average = averageConsumption(closed);
    expect(average.cycleCount, 1);
    expect(average.litersPer100, closeTo(10, 1e-9));
    expect(average.distanceKm, 100);
  });

  test('mixed units are not summed', () {
    final entries = [
      fuel('open', 1, 0, 5, true, unit: 'L'),
      fuel('other', 2, 50, 2, false, unit: 'm3'),
      fuel('close', 3, 100, 3, true, unit: 'L'),
    ];
    final cycle = cycles(entries, const [], FuelType.lpg).single;
    expect(cycle.volume, isNull);
    expect(cycle.volumeQuality, Quality.insufficient);
    expect(cycle.ratePer100, isNull);
    expect(cycle.rateWarnings, contains(CycleWarning.mixedUnits));
    expect(averageConsumption([cycle]).litersPer100, isNull);
  });

  test('single factual cycle average matches that cycle', () {
    final entries = [
      fuel('open', 1, 1000, 40, true, type: FuelType.petrol),
      fuel('close', 2, 1300, 30, true, type: FuelType.petrol),
    ];
    final closed = cycles(entries, const [], FuelType.petrol);
    expect(closed.single.ratePer100, closeTo(10, 1e-9));
    expect(averageConsumption(closed).litersPer100, closeTo(10, 1e-9));
  });
}

FuelEntry fuel(
  String id,
  int at,
  int odometer,
  double quantity,
  bool full, {
  FuelType type = FuelType.lpg,
  String unit = 'L',
  int? created,
}) {
  return FuelEntry(
    id: id,
    fuelType: type,
    occurredAtMs: at,
    odometerKm: odometer,
    quantity: quantity,
    unit: unit,
    isFull: full,
    createdAtMs: created ?? at,
  );
}

OdometerReset reset(
  String id,
  int at,
  int odometer, {
  int? created,
  String? linked,
}) {
  return OdometerReset(
    id: id,
    occurredAtMs: at,
    odometerKm: odometer,
    createdAtMs: created ?? at,
    linkedFuelEntryId: linked,
  );
}
