import 'package:flutter/material.dart';

import '../model/records.dart';
import 'dialogs.dart';
import 'format.dart';
import 'fuel_form_page.dart';
import 'log_controller.dart';
import 'reset_form_page.dart';

class JournalPage extends StatelessWidget {
  const JournalPage({super.key, required this.controller});

  final LogController controller;

  @override
  Widget build(BuildContext context) {
    final rows = <_Row>[
      for (final entry in controller.entries) _Row.entry(entry),
      for (final reset in controller.resets) _Row.reset(reset),
    ]..sort((a, b) => b.key.compareTo(a.key));
    if (rows.isEmpty) return const _EmptyJournal();
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
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 128),
      children: [
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

class _EmptyJournal extends StatelessWidget {
  const _EmptyJournal();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
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
    final scheme = Theme.of(context).colorScheme;
    final title = entry != null ? entryTitle(entry) : 'Замена панели';
    final when = DateTime.fromMillisecondsSinceEpoch(row.millis);
    final odometer = entry?.odometerKm ?? reset!.odometerKm;
    final gas = entry?.fuelType == FuelType.lpg;
    final subtitle =
        '${gas ? 'LPG · ' : ''}${formatClock(when)} · $odometer км';
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
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: avatarColor,
          foregroundColor: avatarInk,
          child: Text(entry == null ? 'П' : fuelMark(entry.fuelType)),
        ),
        title: Text(title),
        subtitle: Text(subtitle),
        onTap: () {
          if (entry != null) {
            openFuel(context, controller, existing: entry);
          } else {
            openReset(context, controller, existing: reset);
          }
        },
        trailing: IconButton(
          tooltip: 'Удалить',
          color: scheme.error,
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
      ),
    );
  }
}
