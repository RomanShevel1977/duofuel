import '../model/records.dart';
import 'format.dart';

enum JournalFuelFilter { all, petrol, lpg }

const journalResetExportNote =
    'В файле замена панели остаётся, если подходят день, месяц и поиск.';

class JournalFilter {
  const JournalFilter({this.text = '', this.fuel, this.day, this.month});

  final String text;

  /// Пустое значение — и бензин, и газ.
  final FuelType? fuel;
  final DateTime? day;

  /// Любой день этого месяца. Вместе с [day] оба условия должны выполниться.
  final DateTime? month;

  bool get isActive =>
      text.trim().isNotEmpty || fuel != null || day != null || month != null;

  bool allowsEntry(FuelEntry entry) {
    if (fuel != null && entry.fuelType != fuel) return false;
    if (!_onDay(entry.occurredAtMs)) return false;
    if (!_inMonth(entry.occurredAtMs)) return false;
    return _contains(_entryHaystack(entry));
  }

  bool allowsReset(OdometerReset reset) {
    if (fuel != null) return false;
    return allowsResetExport(reset);
  }

  /// В CSV замена панели остаётся при фильтре топлива, если подходят день, месяц и поиск.
  bool allowsResetExport(OdometerReset reset) {
    if (!_onDay(reset.occurredAtMs) || !_inMonth(reset.occurredAtMs)) {
      return false;
    }
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

  bool _inMonth(int millis) {
    final selected = month;
    if (selected == null) return true;
    final when = DateTime.fromMillisecondsSinceEpoch(millis);
    return when.year == selected.year && when.month == selected.month;
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
