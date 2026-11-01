import '../model/records.dart';
import 'format.dart';

const journalCsvHeader = 'Дата;Топливо;Литры;Сумма чека;Одометр;Полный бак';

/// Журнал заправок для таблицы. Расход сюда не входит: он считается отдельно.
String journalCsv(List<FuelEntry> entries) {
  final sorted = [...entries]..sort((a, b) => a.sortKey.compareTo(b.sortKey));
  final lines = <String>[
    journalCsvHeader,
    for (final entry in sorted)
      [
        _cell(formatWhenMs(entry.occurredAtMs)),
        _cell(fuelLabel(entry.fuelType)),
        _cell(formatLiters(entry.quantity)),
        _cell(
          entry.totalMinor == null ? '' : formatCsvMoney(entry.totalMinor!),
        ),
        _cell('${entry.odometerKm}'),
        _cell(entry.isFull ? 'да' : 'нет'),
      ].join(';'),
  ];
  return '\uFEFF${lines.join('\n')}\n';
}

String formatCsvMoney(int minor) {
  final abs = minor.abs();
  final whole = abs ~/ 100;
  final frac = (abs % 100).toString().padLeft(2, '0');
  final sign = minor < 0 ? '-' : '';
  return '$sign$whole,$frac';
}

String _cell(String value) {
  if (value.contains(';') || value.contains('"') || value.contains('\n')) {
    return '"${value.replaceAll('"', '""')}"';
  }
  return value;
}
