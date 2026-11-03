import '../model/records.dart';

/// Последняя заправка топлива и километры после неё по той же шкале.
class SinceLastFill {
  const SinceLastFill({required this.entry, this.kmSince});

  final FuelEntry entry;

  /// Пусто, если более позднего показания нет, дистанция не положительная
  /// или между точками шкала разорвана заменой панели.
  final int? kmSince;
}

SinceLastFill? sinceLastFill({
  required List<FuelEntry> entries,
  required List<OdometerReset> resets,
  required FuelType fuel,
}) {
  FuelEntry? last;
  for (final entry in entries) {
    if (entry.fuelType != fuel) continue;
    if (last == null || entry.sortKey.compareTo(last.sortKey) > 0) {
      last = entry;
    }
  }
  if (last == null) return null;

  FuelEntry? newest;
  for (final entry in entries) {
    if (entry.sortKey.compareTo(last.sortKey) <= 0) continue;
    final broken = resets.any(
      (reset) =>
          reset.sortKey.compareTo(last!.sortKey) > 0 &&
          reset.sortKey.compareTo(entry.sortKey) < 0,
    );
    if (broken) continue;
    if (newest == null || entry.sortKey.compareTo(newest.sortKey) > 0) {
      newest = entry;
    }
  }
  int? kmSince;
  if (newest != null) {
    final delta = newest.odometerKm - last.odometerKm;
    if (delta > 0) kmSince = delta;
  }
  return SinceLastFill(entry: last, kmSince: kmSince);
}
