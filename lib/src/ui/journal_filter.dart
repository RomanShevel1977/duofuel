import '../model/records.dart';
import 'format.dart';

enum JournalFuelFilter { all, petrol, lpg }

class JournalFilter {
  const JournalFilter({this.text = '', this.fuel, this.day});

  final String text;

  /// Пустое значение — и бензин, и газ.
  final FuelType? fuel;
  final DateTime? day;

  bool allowsEntry(FuelEntry entry) {
    if (fuel != null && entry.fuelType != fuel) return false;
    if (!_onDay(entry.occurredAtMs)) return false;
    return _contains(_entryHaystack(entry));
  }

  bool allowsReset(OdometerReset reset) {
    if (fuel != null) return false;
    if (!_onDay(reset.occurredAtMs)) return false;
    final haystack =
        'замена панели ${reset.odometerKm} ${formatWhenMs(reset.occurredAtMs)}';
    return _contains(haystack);
  }

  bool _onDay(int millis) {
    final selected = day;
    if (selected == null) return true;
    final when = DateTime.fromMillisecondsSinceEpoch(millis);
    return when.year == selected.year &&
        when.month == selected.month &&
        when.day == selected.day;
  }

  bool _contains(String haystack) {
    final query = text.trim().toLowerCase();
    if (query.isEmpty) return true;
    return haystack.toLowerCase().contains(query);
  }
}

String _entryHaystack(FuelEntry entry) {
  final total = entry.totalMinor == null ? '' : formatMoney(entry.totalMinor!);
  return [
    fuelLabel(entry.fuelType),
    fuelHint(entry.fuelType),
    formatLiters(entry.quantity),
    '${entry.odometerKm}',
    formatWhenMs(entry.occurredAtMs),
    total,
    entry.isFull ? 'полный бак' : 'частичная',
  ].join(' ');
}
