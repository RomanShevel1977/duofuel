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
  const JournalPage({super.key, required this.controller, this.onExport});

  final LogController controller;
  final Future<void> Function(String csv)? onExport;

  @override
  State<JournalPage> createState() => _JournalPageState();
}

class _JournalPageState extends State<JournalPage> {
  final TextEditingController _search = TextEditingController();
  JournalFuelFilter _fuel = JournalFuelFilter.all;
  DateTime? _day;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
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
    );
  }

  Future<void> _pickDay() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _day ?? DateTime.now(),
      firstDate: DateTime(1990),
      lastDate: DateTime(2100),
    );
    if (picked == null) return;
    setState(() => _day = DateTime(picked.year, picked.month, picked.day));
  }

  Future<void> _export() async {
    final csv = journalCsv(widget.controller.entries);
    final custom = widget.onExport;
    if (custom != null) {
      await custom(csv);
      return;
    }
    try {
      final file = await saveJournalCsv(csv);
      var shared = false;
      try {
        await shareJournalFile(file);
        shared = true;
      } catch (_) {}
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            shared
                ? 'Журнал сохранён и открыт для отправки'
                : 'Журнал сохранён на устройстве',
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
        if (filter.allowsReset(reset)) _Row.reset(reset),
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
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        _JournalTools(
          search: _search,
          fuel: _fuel,
          day: _day,
          onSearch: () => setState(() {}),
          onFuel: (value) => setState(() => _fuel = value),
          onPickDay: _pickDay,
          onClearDay: () => setState(() => _day = null),
          onExport: _export,
        ),
        if (!hasAny)
          const _EmptyJournal()
        else if (rows.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 32),
            child: Text('Ничего не найдено', textAlign: TextAlign.center),
          ),
        for (final group in groups) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 12, 4, 8),
            child: Text(
              dayHeading(group.day),
              style: text.titleSmall?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ),
          for (final row in group.rows) ...[
            _JournalTile(controller: controller, row: row),
            const SizedBox(height: 8),
          ],
        ],
      ],
    );
  }
}

class _JournalTools extends StatelessWidget {
  const _JournalTools({
    required this.search,
    required this.fuel,
    required this.day,
    required this.onSearch,
    required this.onFuel,
    required this.onPickDay,
    required this.onClearDay,
    required this.onExport,
  });

  final TextEditingController search;
  final JournalFuelFilter fuel;
  final DateTime? day;
  final VoidCallback onSearch;
  final ValueChanged<JournalFuelFilter> onFuel;
  final VoidCallback onPickDay;
  final VoidCallback onClearDay;
  final VoidCallback onExport;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          key: const Key('journal-search'),
          controller: search,
          onChanged: (_) => onSearch(),
          decoration: const InputDecoration(
            labelText: 'Поиск',
            hintText: 'Литры, одометр, топливо',
            prefixIcon: Icon(Icons.search),
          ),
        ),
        const SizedBox(height: 12),
        SegmentedButton<JournalFuelFilter>(
          key: const Key('journal-fuel'),
          style: const ButtonStyle(
            minimumSize: WidgetStatePropertyAll(Size(48, 48)),
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
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                key: const Key('journal-day'),
                onPressed: onPickDay,
                icon: const Icon(Icons.calendar_today_outlined),
                label: Text(day == null ? 'Все дни' : dayHeading(day!)),
              ),
            ),
            if (day != null) ...[
              const SizedBox(width: 8),
              IconButton(
                key: const Key('journal-day-clear'),
                tooltip: 'Сбросить день',
                onPressed: onClearDay,
                icon: const Icon(Icons.close),
              ),
            ],
          ],
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          key: const Key('journal-export'),
          onPressed: onExport,
          icon: const Icon(Icons.ios_share),
          label: const Text('Экспорт CSV'),
        ),
        const SizedBox(height: 4),
        Text(
          'Файл сохраняется на устройстве, его можно отправить.',
          style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
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
  _Row.entry(this.entry) : reset = null;
  _Row.reset(this.reset) : entry = null;

  final FuelEntry? entry;
  final OdometerReset? reset;

  SortKey get key => entry?.sortKey ?? reset!.sortKey;

  int get millis => entry?.occurredAtMs ?? reset!.occurredAtMs;
}

class _JournalTile extends StatelessWidget {
  const _JournalTile({required this.controller, required this.row});

  final LogController controller;
  final _Row row;

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
    return Card(
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
                      Text(whenLine, style: text.bodyMedium),
                    if (kind != null)
                      Text(
                        whenLine,
                        style: text.bodyMedium?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    if (paid != null)
                      Text(
                        formatMoney(paid),
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
                onPressed: () async {
                  final accepted = await confirmDelete(context);
                  if (!accepted) return;
                  if (entry != null) {
                    await controller.deleteEntry(entry.id);
                  } else if (reset != null) {
                    await controller.deleteReset(reset.id);
                  }
                },
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
