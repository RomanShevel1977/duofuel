import '../model/records.dart';
import 'format.dart';

const journalCsvHeader =
    'Дата;Событие;Топливо;Литры;Сумма чека;Одометр;Полный бак';

const journalCsvFilteredLine =
    'Фильтр;в файле только строки, которые сейчас видны в журнале';

/// Журнал для таблицы. Расход сюда не входит: он считается отдельно.
String journalCsv(
  List<FuelEntry> entries, {
  List<OdometerReset> resets = const [],
  bool filtered = false,
}) {
  final lines = <_CsvLine>[
    for (final entry in entries)
      _CsvLine(entry.sortKey, [
        formatWhenMs(entry.occurredAtMs),
        'Заправка',
        fuelLabel(entry.fuelType),
        formatLiters(entry.quantity),
        entry.totalMinor == null ? '' : formatCsvMoney(entry.totalMinor!),
        '${entry.odometerKm}',
        entry.isFull ? 'да' : 'нет',
      ]),
    for (final reset in resets)
      _CsvLine(reset.sortKey, [
        formatWhenMs(reset.occurredAtMs),
        'Замена панели',
        '',
        '',
        '',
        '${reset.odometerKm}',
        '',
      ]),
  ]..sort((a, b) => a.key.compareTo(b.key));
  final body = <String>[
    if (filtered) journalCsvFilteredLine,
    journalCsvHeader,
    for (final line in lines) line.cells.map(_cell).join(';'),
  ];
  return '\uFEFF${body.join('\n')}\n';
}

String formatCsvMoney(int minor) {
  final abs = minor.abs();
  final whole = abs ~/ 100;
  final frac = (abs % 100).toString().padLeft(2, '0');
  final sign = minor < 0 ? '-' : '';
  return '$sign$whole,$frac';
}

String journalExportMessage({
  required bool filtered,
  required String fileName,
  required bool shared,
}) {
  final scope = filtered
      ? 'В файле только строки, которые сейчас видны в журнале.'
      : 'В файле весь журнал, включая замены панели.';
  if (shared) {
    return '$scope Сохраните «$fileName» через отправку — так файл можно найти.';
  }
  return '$scope Файл «$fileName» лежит в папке duofuel-export. Отправка не открылась.';
}

String _cell(String value) {
  if (value.contains(';') || value.contains('"') || value.contains('\n')) {
    return '"${value.replaceAll('"', '""')}"';
  }
  return value;
}

class _CsvLine {
  const _CsvLine(this.key, this.cells);

  final SortKey key;
  final List<String> cells;
}
