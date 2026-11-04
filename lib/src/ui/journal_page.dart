import 'dart:async';

import 'package:flutter/material.dart';

import '../model/records.dart';
import 'dialogs.dart';
import 'format.dart';
import 'fuel_form_page.dart';
import 'journal_csv.dart';
import 'journal_filter.dart';
import 'log_controller.dart';
import 'reset_form_page.dart';
import 'share_journal.dart';

class JournalPage extends StatefulWidget {
  const JournalPage({
    super.key,
    required this.controller,
    this.onExport,
    this.onExportSnapshot,
    this.onImportSnapshot,
  });

  final LogController controller;
  final Future<void> Function(String csv)? onExport;
  final Future<void> Function()? onExportSnapshot;
  final Future<void> Function()? onImportSnapshot;

  @override
  State<JournalPage> createState() => _JournalPageState();
}

class _RemovedRow {
  const _RemovedRow({this.entry, this.reset});

  final FuelEntry? entry;
  final OdometerReset? reset;

  String get label =>
      entry != null ? 'Заправка удалена' : 'Замена панели удалена';
}

class _JournalPageState extends State<JournalPage> {
  final TextEditingController _search = TextEditingController();
  JournalFuelFilter _fuel = JournalFuelFilter.all;
  DateTime? _day;
  DateTime? _month;
  _RemovedRow? _removed;
  Timer? _undoTimer;

  @override
  void dispose() {
    _undoTimer?.cancel();
    _search.dispose();
    super.dispose();
  }

  void _offerUndo(FuelEntry? entry, OdometerReset? reset) {
    _undoTimer?.cancel();
    setState(() => _removed = _RemovedRow(entry: entry, reset: reset));
    _undoTimer = Timer(const Duration(seconds: 4), () {
      if (!mounted) return;
      setState(() => _removed = null);
    });
  }

  Future<void> _restoreRemoved() async {
    final removed = _removed;
    if (removed == null) return;
    _undoTimer?.cancel();
    setState(() => _removed = null);
    final entry = removed.entry;
    final reset = removed.reset;
    final restored = entry != null
        ? await widget.controller.saveEntry(entry)
        : reset != null
        ? await widget.controller.saveReset(reset)
        : true;
    if (restored || !mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text(journalWriteError)));
  }

  JournalFilter get _filter {
    return JournalFilter(
      text: _search.text,
      fuel: switch (_fuel) {
        JournalFuelFilter.all => null,
        JournalFuelFilter.petrol => FuelType.petrol,
        JournalFuelFilter.lpg => FuelType.lpg,
      },
      day: _day,
      month: _month,
    );
  }

  Future<void> _pickDay() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _day ?? _month ?? DateTime.now(),
      firstDate: DateTime(1990),
      lastDate: DateTime(2100),
    );
    if (picked == null) return;
    setState(() {
      _day = DateTime(picked.year, picked.month, picked.day);
      _month = null;
    });
  }

  Future<void> _pickMonth() async {
    final picked = await showDialog<DateTime>(
      context: context,
      builder: (context) => _MonthDialog(initial: _month ?? DateTime.now()),
    );
    if (picked == null) return;
    setState(() {
      _month = DateTime(picked.year, picked.month);
      _day = null;
    });
  }

  Future<void> _export() async {
    final controller = widget.controller;
    final filter = _filter;
    final filtered = filter.isActive;
    final csv = journalCsv(
      [
        for (final entry in controller.entries)
          if (filter.allowsEntry(entry)) entry,
      ],
      resets: [
        for (final reset in controller.resets)
          if (filter.allowsResetExport(reset)) reset,
      ],
      filterLine: journalFilterLine(
        fuel: filter.fuel,
        day: filter.day,
        month: filter.month,
        text: filter.text,
      ),
    );
    final custom = widget.onExport;
    if (custom != null) {
      await custom(csv);
      return;
    }
    try {
      final file = await saveJournalCsv(csv);
      final name = file.uri.pathSegments.last;
      var shared = false;
      try {
        await shareJournalFile(
          file,
          text: journalExportMessage(
            filtered: filtered,
            fileName: name,
            shared: true,
          ),
        );
        shared = true;
      } catch (_) {}
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            journalExportMessage(
              filtered: filtered,
              fileName: name,
              shared: shared,
            ),
          ),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Не удалось сохранить журнал')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final filter = _filter;
    final rows = <_Row>[
      for (final entry in controller.entries)
        if (filter.allowsEntry(entry)) _Row.entry(entry),
      for (final reset in controller.resets)
        if (filter.allowsReset(reset))
          _Row.reset(reset)
        else if (filter.allowsResetExport(reset))
          _Row.reset(reset, dimmed: true),
    ]..sort((a, b) => b.key.compareTo(a.key));
    final hasAny =
        controller.entries.isNotEmpty || controller.resets.isNotEmpty;
    final groups = <_DayGroup>[];
    for (final row in rows) {
      final when = DateTime.fromMillisecondsSinceEpoch(row.millis);
      final day = DateTime(when.year, when.month, when.day);
      if (groups.isEmpty || groups.last.day != day) {
        groups.add(_DayGroup(day, [row]));
      } else {
        groups.last.rows.add(row);
      }
    }
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final items = <_JournalItem>[
      const _JournalItem.tools(),
      if (!hasAny)
        const _JournalItem.empty()
      else if (rows.isEmpty)
        const _JournalItem.none(),
      for (final group in groups) ...[
        _JournalItem.day(group.day),
        for (final row in group.rows) _JournalItem.tile(row),
      ],
    ];
    final removed = _removed;
    return Column(
      children: [
        if (removed != null)
          Material(
            color: scheme.inverseSurface,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      removed.label,
                      style: text.bodyMedium?.copyWith(
                        color: scheme.onInverseSurface,
                      ),
                    ),
                  ),
                  TextButton(
                    key: const Key('undo-delete'),
                    onPressed: _restoreRemoved,
                    child: Text(
                      'Вернуть',
                      style: TextStyle(color: scheme.inversePrimary),
                    ),
                  ),
                ],
              ),
            ),
          ),
        Expanded(
          child: ListView.builder(
            key: const Key('journal-list'),
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            itemCount: items.length,
            itemBuilder: (context, index) {
              final item = items[index];
              return switch (item.kind) {
                _JournalKind.tools => _JournalTools(
                  search: _search,
                  fuel: _fuel,
                  day: _day,
                  onSearch: () => setState(() {}),
                  onFuel: (value) => setState(() => _fuel = value),
                  month: _month,
                  onPickDay: _pickDay,
                  onClearDay: () => setState(() => _day = null),
                  onPickMonth: _pickMonth,
                  onClearMonth: () => setState(() => _month = null),
                  onExport: _export,
                  onExportSnapshot: widget.onExportSnapshot,
                  onImportSnapshot: widget.onImportSnapshot,
                  onReset: () => openReset(context, controller),
                ),
                _JournalKind.empty => const _EmptyJournal(),
                _JournalKind.none => const Padding(
                  padding: EdgeInsets.symmetric(vertical: 32),
                  child: Text('Ничего не найдено', textAlign: TextAlign.center),
                ),
                _JournalKind.day => Padding(
                  padding: const EdgeInsets.fromLTRB(4, 12, 4, 8),
                  child: Text(
                    dayHeading(item.day!),
                    style: text.titleSmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
                _JournalKind.tile => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _JournalTile(
                    controller: controller,
                    row: item.row!,
                    onRemoved: _offerUndo,
                  ),
                ),
              };
            },
          ),
        ),
      ],
    );
  }
}

enum _JournalKind { tools, empty, none, day, tile }

class _JournalItem {
  const _JournalItem.tools()
    : kind = _JournalKind.tools,
      day = null,
      row = null;

  const _JournalItem.empty()
    : kind = _JournalKind.empty,
      day = null,
      row = null;

  const _JournalItem.none() : kind = _JournalKind.none, day = null, row = null;

  const _JournalItem.day(this.day) : kind = _JournalKind.day, row = null;

  const _JournalItem.tile(this.row) : kind = _JournalKind.tile, day = null;

  final _JournalKind kind;
  final DateTime? day;
  final _Row? row;
}

class _JournalTools extends StatelessWidget {
  const _JournalTools({
    required this.search,
    required this.fuel,
    required this.day,
    required this.month,
    required this.onSearch,
    required this.onFuel,
    required this.onPickDay,
    required this.onClearDay,
    required this.onPickMonth,
    required this.onClearMonth,
    required this.onExport,
    required this.onExportSnapshot,
    required this.onImportSnapshot,
    required this.onReset,
  });

  final TextEditingController search;
  final JournalFuelFilter fuel;
  final DateTime? day;
  final DateTime? month;
  final VoidCallback onSearch;
  final ValueChanged<JournalFuelFilter> onFuel;
  final VoidCallback onPickDay;
  final VoidCallback onClearDay;
  final VoidCallback onPickMonth;
  final VoidCallback onClearMonth;
  final VoidCallback onExport;
  final Future<void> Function()? onExportSnapshot;
  final Future<void> Function()? onImportSnapshot;
  final VoidCallback onReset;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextField(
                key: const Key('journal-search'),
                controller: search,
                onChanged: (_) => onSearch(),
                style: text.bodyMedium,
                decoration: const InputDecoration(
                  isDense: true,
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  hintText: 'Поиск: литры, одометр',
                  prefixIcon: Icon(Icons.search),
                  prefixIconConstraints: BoxConstraints(
                    minWidth: 40,
                    minHeight: 36,
                  ),
                ),
              ),
            ),
            IconButton(
              key: const Key('journal-export'),
              tooltip: 'Таблица CSV. Назад не читается',
              onPressed: onExport,
              icon: const Icon(Icons.ios_share),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SegmentedButton<JournalFuelFilter>(
          key: const Key('journal-fuel'),
          style: const ButtonStyle(
            visualDensity: VisualDensity.compact,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            minimumSize: WidgetStatePropertyAll(Size(36, 36)),
          ),
          segments: const [
            ButtonSegment(value: JournalFuelFilter.all, label: Text('Все')),
            ButtonSegment(
              value: JournalFuelFilter.petrol,
              label: Text('Бензин'),
              tooltip: 'Только бензин',
            ),
            ButtonSegment(
              value: JournalFuelFilter.lpg,
              label: Text('Газ'),
              tooltip: 'Только газ',
            ),
          ],
          selected: {fuel},
          onSelectionChanged: (selected) => onFuel(selected.first),
        ),
        if (fuel != JournalFuelFilter.all)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              journalResetExportNote,
              key: const Key('journal-reset-export-note'),
              style: text.bodySmall,
            ),
          ),
        _JournalExtras(
          onExportSnapshot: onExportSnapshot,
          onImportSnapshot: onImportSnapshot,
          onReset: onReset,
        ),
        Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            TextButton.icon(
              key: const Key('journal-day'),
              style: TextButton.styleFrom(
                visualDensity: VisualDensity.compact,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                minimumSize: const Size(0, 36),
              ),
              onPressed: onPickDay,
              icon: const Icon(Icons.calendar_today_outlined, size: 18),
              label: Text(day == null ? 'Все дни' : dayHeading(day!)),
            ),
            if (day != null)
              IconButton(
                key: const Key('journal-day-clear'),
                tooltip: 'Сбросить день',
                visualDensity: VisualDensity.compact,
                onPressed: onClearDay,
                icon: const Icon(Icons.close),
              ),
            TextButton.icon(
              key: const Key('journal-month'),
              style: TextButton.styleFrom(
                visualDensity: VisualDensity.compact,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                minimumSize: const Size(0, 36),
              ),
              onPressed: onPickMonth,
              icon: const Icon(Icons.calendar_month_outlined, size: 18),
              label: Text(month == null ? 'Все месяцы' : monthHeading(month!)),
            ),
            if (month != null)
              IconButton(
                key: const Key('journal-month-clear'),
                tooltip: 'Сбросить месяц',
                visualDensity: VisualDensity.compact,
                onPressed: onClearMonth,
                icon: const Icon(Icons.close),
              ),
          ],
        ),
      ],
    );
  }
}

class _MonthDialog extends StatefulWidget {
  const _MonthDialog({required this.initial});

  final DateTime initial;

  @override
  State<_MonthDialog> createState() => _MonthDialogState();
}

class _MonthDialogState extends State<_MonthDialog> {
  late int _year = widget.initial.year;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('$_year', key: const Key('journal-month-year')),
      content: SizedBox(
        width: 320,
        child: Wrap(
          spacing: 4,
          children: [
            for (var month = 1; month <= 12; month++)
              TextButton(
                key: Key('journal-month-$month'),
                onPressed: () => Navigator.pop(context, DateTime(_year, month)),
                child: Text(monthNames[month - 1]),
              ),
          ],
        ),
      ),
      actions: [
        IconButton(
          key: const Key('journal-month-prev'),
          tooltip: 'Предыдущий год',
          onPressed: _year > 1990 ? () => setState(() => _year -= 1) : null,
          icon: const Icon(Icons.chevron_left),
        ),
        IconButton(
          key: const Key('journal-month-next'),
          tooltip: 'Следующий год',
          onPressed: _year < 2100 ? () => setState(() => _year += 1) : null,
          icon: const Icon(Icons.chevron_right),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Отмена'),
        ),
      ],
    );
  }
}

class _JournalExtras extends StatefulWidget {
  const _JournalExtras({
    required this.onExportSnapshot,
    required this.onImportSnapshot,
    required this.onReset,
  });

  final Future<void> Function()? onExportSnapshot;
  final Future<void> Function()? onImportSnapshot;
  final VoidCallback onReset;

  @override
  State<_JournalExtras> createState() => _JournalExtrasState();
}

class _JournalExtrasState extends State<_JournalExtras> {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextButton.icon(
          key: const Key('journal-more'),
          onPressed: () => setState(() => _open = !_open),
          icon: Icon(_open ? Icons.expand_less : Icons.expand_more),
          label: const Text('Копия и панель'),
        ),
        if (_open)
          Wrap(
            spacing: 4,
            children: [
              TextButton(
                key: const Key('journal-snapshot'),
                onPressed: widget.onExportSnapshot,
                child: const Text('Копия журнала'),
              ),
              TextButton(
                key: const Key('journal-import'),
                onPressed: widget.onImportSnapshot,
                child: const Text('Восстановить'),
              ),
              TextButton.icon(
                key: const Key('journal-reset'),
                onPressed: widget.onReset,
                icon: const Icon(Icons.speed_outlined),
                label: const Text('Замена панели'),
              ),
            ],
          ),
      ],
    );
  }
}

class _EmptyJournal extends StatelessWidget {
  const _EmptyJournal();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(
        children: [
          Icon(
            Icons.local_gas_station_outlined,
            size: 56,
            color: scheme.onSurfaceVariant,
          ),
          const SizedBox(height: 12),
          Text('Пока нет заправок', style: text.titleMedium),
          const SizedBox(height: 8),
          Text(
            'Нажмите «Заправка» внизу. Отметьте полный бак, если заправились до отсечки.',
            style: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

class _DayGroup {
  _DayGroup(this.day, this.rows);

  final DateTime day;
  final List<_Row> rows;
}

class _Row {
  _Row.entry(this.entry) : reset = null, dimmed = false;
  _Row.reset(this.reset, {this.dimmed = false}) : entry = null;

  final FuelEntry? entry;
  final OdometerReset? reset;
  final bool dimmed;

  SortKey get key => entry?.sortKey ?? reset!.sortKey;

  int get millis => entry?.occurredAtMs ?? reset!.occurredAtMs;
}

String _paidLine(int totalMinor, double? liters) {
  final money = formatMoney(totalMinor);
  final unit = formatEnteredUnitPrice(totalMinor, liters);
  if (unit == null) return money;
  return '$money · $unit';
}

Future<void> _deleteRow(
  BuildContext context, {
  required LogController controller,
  required FuelEntry? entry,
  required OdometerReset? reset,
  required void Function(FuelEntry? entry, OdometerReset? reset) onRemoved,
}) async {
  final accepted = await confirmDelete(context);
  if (!accepted || !context.mounted) return;
  final messenger = ScaffoldMessenger.of(context);
  final wrote = entry != null
      ? await controller.deleteEntry(entry.id)
      : reset != null
      ? await controller.deleteReset(reset.id)
      : true;
  if (!wrote) {
    messenger.showSnackBar(const SnackBar(content: Text(journalWriteError)));
    return;
  }
  onRemoved(entry, reset);
}

class _JournalTile extends StatelessWidget {
  const _JournalTile({
    required this.controller,
    required this.row,
    required this.onRemoved,
  });

  final LogController controller;
  final _Row row;
  final void Function(FuelEntry? entry, OdometerReset? reset) onRemoved;

  @override
  Widget build(BuildContext context) {
    final entry = row.entry;
    final reset = row.reset;
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final when = DateTime.fromMillisecondsSinceEpoch(row.millis);
    final odometer = entry?.odometerKm ?? reset!.odometerKm;
    final paid = entry?.totalMinor;
    final title = entry == null
        ? 'Замена панели'
        : '${formatLiters(entry.quantity)} л';
    final kind = entry == null
        ? null
        : '${fuelLabel(entry.fuelType)}${entry.fuelType == FuelType.lpg ? ' · LPG' : ''} · ${entry.isFull ? 'полный бак' : 'частичная'}';
    final whenLine = '${formatClock(when)} · $odometer км';
    final avatarColor = entry == null
        ? scheme.tertiaryContainer
        : entry.isFull
        ? scheme.primaryContainer
        : scheme.surfaceContainerHighest;
    final avatarInk = entry == null
        ? scheme.onTertiaryContainer
        : entry.isFull
        ? scheme.onPrimaryContainer
        : scheme.onSurface;
    final tile = Card(
      child: InkWell(
        onTap: () {
          if (entry != null) {
            openFuel(context, controller, existing: entry);
          } else {
            openReset(context, controller, existing: reset);
          }
        },
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 4, 8),
          child: Row(
            children: [
              CircleAvatar(
                backgroundColor: avatarColor,
                foregroundColor: avatarInk,
                child: Text(entry == null ? 'П' : fuelMark(entry.fuelType)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: text.titleLarge),
                    if (kind != null)
                      Text(kind, style: text.bodyMedium)
                    else
                      Text(
                        row.dimmed ? '$whenLine · в таблице CSV' : whenLine,
                        style: text.bodyMedium,
                      ),
                    if (kind != null)
                      Text(
                        whenLine,
                        style: text.bodyMedium?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    if (paid != null)
                      Text(
                        _paidLine(paid, entry?.quantity),
                        style: text.titleSmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Удалить',
                color: scheme.error,
                style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: () => _deleteRow(
                  context,
                  controller: controller,
                  entry: entry,
                  reset: reset,
                  onRemoved: onRemoved,
                ),
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
        ),
      ),
    );
    if (!row.dimmed) return tile;
    return Opacity(opacity: 0.55, child: tile);
  }
}
