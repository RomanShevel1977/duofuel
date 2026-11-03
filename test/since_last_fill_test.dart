import 'package:duofuel/src/calc/since_last_fill.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/format.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('kilometres since the last fill stay on one odometer scale', () {
    final petrol = _fill('p', FuelType.petrol, 1, 1000);
    final gas = _fill('g', FuelType.lpg, 2, 1400);
    final open = sinceLastFill(
      entries: [petrol, gas],
      resets: const [],
      fuel: FuelType.petrol,
    )!;
    expect(open.entry.id, 'p');
    expect(open.kmSince, 400);
    expect(sinceLastFillLine(open), contains('1000 км · с тех пор 400 км'));

    final latestGas = sinceLastFill(
      entries: [petrol, gas],
      resets: const [],
      fuel: FuelType.lpg,
    )!;
    expect(latestGas.kmSince, isNull);
    expect(sinceLastFillLine(latestGas), contains('1400 км'));
    expect(sinceLastFillLine(latestGas).contains('с тех пор'), isFalse);

    final reset = OdometerReset(
      id: 'panel',
      occurredAtMs: 3,
      odometerKm: 10,
      createdAtMs: 3,
    );
    final afterReset = _fill('later', FuelType.petrol, 4, 80);
    final broken = sinceLastFill(
      entries: [petrol, gas, afterReset],
      resets: [reset],
      fuel: FuelType.lpg,
    )!;
    expect(broken.entry.id, 'g');
    expect(broken.kmSince, isNull);

    final same = sinceLastFill(
      entries: [petrol, _fill('flat', FuelType.lpg, 2, 1000)],
      resets: const [],
      fuel: FuelType.petrol,
    )!;
    expect(same.kmSince, isNull);
    expect(
      sinceLastFill(entries: const [], resets: const [], fuel: FuelType.petrol),
      isNull,
    );
  });
}

FuelEntry _fill(String id, FuelType fuel, int at, int odometer) {
  return FuelEntry(
    id: id,
    fuelType: fuel,
    occurredAtMs: at,
    odometerKm: odometer,
    quantity: 10,
    unit: 'L',
    isFull: true,
    createdAtMs: at,
  );
}
