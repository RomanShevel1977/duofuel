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
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton(
            onPressed: () => openReset(context, controller),
            child: const Text('Замена панели'),
          ),
        ),
        const SizedBox(height: 12),
        if (rows.isEmpty)
          const Text('Пока нет заправок.')
        else
          for (final row in rows) ...[
            _JournalTile(controller: controller, row: row),
            const SizedBox(height: 8),
          ],
      ],
    );
  }
}

class _Row {
  _Row.entry(this.entry) : reset = null;
  _Row.reset(this.reset) : entry = null;

  final FuelEntry? entry;
  final OdometerReset? reset;

  SortKey get key => entry?.sortKey ?? reset!.sortKey;
}

class _JournalTile extends StatelessWidget {
  const _JournalTile({required this.controller, required this.row});

  final LogController controller;
  final _Row row;

  @override
  Widget build(BuildContext context) {
    final entry = row.entry;
    final reset = row.reset;
    final title = entry != null ? entryTitle(entry) : 'Замена панели';
    final when = entry?.occurredAtMs ?? reset!.occurredAtMs;
    final odometer = entry?.odometerKm ?? reset!.odometerKm;
    return Card(
      child: ListTile(
        title: Text(title),
        subtitle: Text('${formatWhenMs(when)} · $odometer км'),
        onTap: () {
          if (entry != null) {
            openFuel(context, controller, existing: entry);
          } else {
            openReset(context, controller, existing: reset);
          }
        },
        trailing: IconButton(
          tooltip: 'Удалить',
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
