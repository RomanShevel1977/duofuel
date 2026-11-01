import 'package:duofuel/src/calc/fuel_cycle.dart';
import 'package:duofuel/src/calc/price_average.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/format.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'average price is the sum of receipts over the litres that have one',
    () {
      final entries = [
        _fill('a', FuelType.petrol, 40, 4000),
        _fill('b', FuelType.petrol, 10, 3000),
        _fill('c', FuelType.petrol, 100, null),
        _fill('d', FuelType.lpg, 8, 24000),
      ];

      final petrol = averagePrice(entries, FuelType.petrol);
      expect(priceValue(petrol), '1,40 грн/л');
      expect(petrol.fillCount, 2);
      expect(petrol.liters, 50);
      expect(priceValue(averagePrice(entries, FuelType.lpg)), '30,00 грн/л');
      expect(
        priceValue(averagePrice(const [], FuelType.petrol)),
        'Недостаточно данных',
      );
    },
  );

  test('a zero receipt counts and a missing receipt is not a zero', () {
    final withZero = [
      _fill('a', FuelType.petrol, 40, 4000),
      _fill('b', FuelType.petrol, 10, 0),
    ];
    expect(priceValue(averagePrice(withZero, FuelType.petrol)), '0,80 грн/л');

    final missing = [_fill('a', FuelType.petrol, 40, null)];
    final absent = averagePrice(missing, FuelType.petrol);
    expect(absent.hasPrice, isFalse);
    expect(priceValue(absent), 'Недостаточно данных');
    expect(priceValue(absent).contains('0,00'), isFalse);
  });

  test('mixed currencies are not averaged', () {
    final entries = [
      _fill('a', FuelType.petrol, 10, 1000),
      _fill('b', FuelType.petrol, 10, 1000, currency: 'EUR'),
    ];
    final average = averagePrice(entries, FuelType.petrol);
    expect(average.mixedCurrency, isTrue);
    expect(priceValue(average), 'Недостаточно данных');
  });

  test('a receipt does not change the litre cycle', () {
    final entries = [
      _fill('open', FuelType.petrol, 40, 200000, odometer: 1000, full: true),
      _fill('mid', FuelType.petrol, 10, 50000, odometer: 1200),
      _fill('close', FuelType.petrol, 20, 110000, odometer: 1300, full: true),
    ];
    final cycle = cycles(entries, const [], FuelType.petrol).single;
    expect(cycle.volume, 30);
    expect(cycle.ratePer100, closeTo(10, 1e-9));
  });
}

FuelEntry _fill(
  String id,
  FuelType fuel,
  double quantity,
  int? totalMinor, {
  int odometer = 1,
  bool full = false,
  String? currency,
}) {
  return FuelEntry(
    id: id,
    fuelType: fuel,
    occurredAtMs: 1,
    odometerKm: odometer,
    quantity: quantity,
    unit: 'L',
    isFull: full,
    createdAtMs: id.hashCode,
    totalMinor: totalMinor,
    currency: totalMinor == null ? null : (currency ?? 'UAH'),
  );
}
