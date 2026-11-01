import 'package:flutter/material.dart';

import '../model/records.dart';
import 'dialogs.dart';
import 'format.dart';
import 'log_controller.dart';
import 'reset_form_page.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key, required this.controller});

  final LogController controller;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
      children: [
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(controller.vehicleName, style: text.titleLarge),
                  const SizedBox(height: 4),
                  const Text('Бензин и LPG'),
                ],
              ),
            ),
            IconButton(
              key: const Key('rename-vehicle'),
              tooltip: 'Имя автомобиля',
              onPressed: () => renameVehicle(context, controller),
              icon: const Icon(Icons.edit),
            ),
          ],
        ),
        const SizedBox(height: 12),
        _FuelCard(controller: controller, fuel: FuelType.petrol),
        const SizedBox(height: 12),
        _FuelCard(controller: controller, fuel: FuelType.lpg),
        const SizedBox(height: 16),
        Text(formulaFootnote, style: text.bodySmall),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton(
            onPressed: () => openReset(context, controller),
            child: const Text('Замена панели'),
          ),
        ),
      ],
    );
  }
}

class _FuelCard extends StatelessWidget {
  const _FuelCard({required this.controller, required this.fuel});

  final LogController controller;
  final FuelType fuel;

  @override
  Widget build(BuildContext context) {
    final report = controller.consumption(fuel);
    final text = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(fuelLabel(fuel), style: text.titleLarge),
            const SizedBox(height: 12),
            Text('Последний цикл', style: text.titleSmall),
            const SizedBox(height: 4),
            _Readout(
              valueKey: Key('consumption-${fuel.name}-latest'),
              value: latestValue(report.latest),
              detail: latestDetail(report.latest),
            ),
            const SizedBox(height: 16),
            Text('Средний расход', style: text.titleSmall),
            const SizedBox(height: 4),
            _Readout(
              valueKey: Key('consumption-${fuel.name}-average'),
              value: averageValue(report.average),
              detail: averageDetail(report.average),
            ),
            if (report.unclosed != null) ...[
              const SizedBox(height: 12),
              Text(unclosedLine(fuel)),
            ],
            if (report.cycles.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text('Закрытые циклы', style: text.titleSmall),
              const SizedBox(height: 4),
              for (final cycle in report.cycles) ...[
                Text(cycleLine(cycle)),
                const SizedBox(height: 4),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

class _Readout extends StatelessWidget {
  const _Readout({
    required this.valueKey,
    required this.value,
    required this.detail,
  });

  final Key valueKey;
  final String value;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          value,
          key: valueKey,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 4),
        Text(detail),
      ],
    );
  }
}
