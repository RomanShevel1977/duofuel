import '../model/records.dart';
import 'validation.dart';

/// Средняя цена литра: сумма чеков / сумма литров.
/// Берутся только заправки этого топлива, у которых сумма указана.
class PriceAverage {
  const PriceAverage({
    this.currency,
    this.totalMinor,
    this.liters,
    this.fillCount = 0,
    this.mixedCurrency = false,
  });

  final String? currency;
  final int? totalMinor;
  final double? liters;
  final int fillCount;
  final bool mixedCurrency;

  bool get hasPrice {
    final money = totalMinor;
    final volume = liters;
    return !mixedCurrency &&
        currency == uahCurrency &&
        money != null &&
        volume != null &&
        volume > 0 &&
        volume.isFinite;
  }
}

PriceAverage averagePrice(List<FuelEntry> entries, FuelType fuel) {
  final priced = [
    for (final entry in entries)
      if (entry.fuelType == fuel && entry.totalMinor != null) entry,
  ];
  if (priced.isEmpty) return const PriceAverage();
  final currencies = priced.map((entry) => entry.currency).toSet();
  if (currencies.length != 1 || currencies.single != uahCurrency) {
    return PriceAverage(fillCount: priced.length, mixedCurrency: true);
  }
  var money = 0;
  var liters = 0.0;
  for (final entry in priced) {
    money += entry.totalMinor!;
    liters += entry.quantity;
  }
  if (!(liters > 0) || !liters.isFinite) return const PriceAverage();
  return PriceAverage(
    currency: uahCurrency,
    totalMinor: money,
    liters: liters,
    fillCount: priced.length,
  );
}
