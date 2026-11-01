import 'package:duofuel/src/calc/odometer_order.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/format.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('a lower reading needs a panel reset between the points', () {
    final entries = [_fill('a', 1000, at: 1000), _fill('b', 1300, at: 1300)];
    expect(
      previousOdometerHint(
        entries: entries,
        resets: const [],
        occurredAtMs: 1400,
        createdAtMs: 1,
        exceptEntryId: 'draft',
      ),
      1300,
    );
    expect(
      validateOdometerNotLower(
        odometerKm: 900,
        occurredAtMs: 1400,
        createdAtMs: 1,
        id: 'draft',
        entries: entries,
        resets: const [],
      ),
      isNotEmpty,
    );
    expect(
      validateOdometerNotLower(
        odometerKm: 1300,
        occurredAtMs: 1400,
        createdAtMs: 1,
        id: 'draft',
        entries: entries,
        resets: const [],
      ),
      isEmpty,
    );

    final reset = OdometerReset(
      id: 'panel',
      occurredAtMs: 1350,
      odometerKm: 50,
      createdAtMs: 1,
    );
    expect(
      validateOdometerNotLower(
        odometerKm: 80,
        occurredAtMs: 1400,
        createdAtMs: 1,
        id: 'draft',
        entries: entries,
        resets: [reset],
      ),
      isEmpty,
    );
    expect(
      previousOdometerHint(
        entries: entries,
        resets: [reset],
        occurredAtMs: 1400,
        createdAtMs: 1,
        exceptEntryId: 'draft',
      ),
      50,
    );
    expect(
      validateOdometerNotLower(
        odometerKm: 40,
        occurredAtMs: 1500,
        createdAtMs: 1,
        id: 'later',
        entries: [...entries, _fill('after', 80, at: 1400)],
        resets: [reset],
      ),
      isNotEmpty,
    );
    expect(
      validateOdometerNotLower(
        odometerKm: 900,
        occurredAtMs: 1000,
        createdAtMs: 2,
        id: 'same-minute',
        entries: [_fill('a', 1000, at: 1000)],
        resets: const [],
      ),
      isNotEmpty,
    );
  });

  test('raising an earlier reading above a later one does not save', () {
    final entries = [_fill('a', 1000, at: 1000), _fill('b', 1300, at: 1300)];
    final issues = validateOdometerNotLower(
      odometerKm: 1400,
      occurredAtMs: 1000,
      createdAtMs: 1,
      id: 'a',
      entries: entries,
      resets: const [],
      savedOdometerKm: 1000,
    );
    expect(issues.single.line, contains('уже есть меньшее'));
    expect(
      previousOdometerHint(
        entries: entries,
        resets: const [],
        occurredAtMs: 1000,
        createdAtMs: 1,
        exceptEntryId: 'a',
        savedOdometerKm: 1000,
      ),
      isNull,
    );
  });

  test('the hint follows the shared timeline, not one fuel', () {
    final entries = [
      _fill('petrol', 1000, at: 1000),
      _fill('lpg', 1200, at: 1200, fuel: FuelType.lpg),
    ];
    expect(
      previousOdometerHint(
        entries: entries,
        resets: const [],
        occurredAtMs: 1300,
        createdAtMs: 1,
        exceptEntryId: 'draft',
      ),
      1200,
    );
    expect(formatEnteredUnitPrice(220000, 40), '55,00 грн/л');
    expect(formatEnteredUnitPrice(null, 40), isNull);
    expect(formatEnteredUnitPrice(1000, 0), isNull);
    expect(twoFullFillsDetail(FuelType.lpg), 'Нужны две полные заправки газа.');
  });
}

FuelEntry _fill(
  String id,
  int odometer, {
  required int at,
  FuelType fuel = FuelType.petrol,
}) {
  return FuelEntry(
    id: id,
    fuelType: fuel,
    occurredAtMs: at,
    odometerKm: odometer,
    quantity: 10,
    unit: 'L',
    isFull: true,
    createdAtMs: 1,
  );
}
