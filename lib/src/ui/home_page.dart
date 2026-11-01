import 'package:flutter/material.dart';

import '../model/records.dart';
import 'dialogs.dart';
import 'format.dart';
import 'log_controller.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key, required this.controller});

  final LogController controller;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        Row(
          children: [
            CircleAvatar(
              backgroundColor: scheme.primaryContainer,
              foregroundColor: scheme.onPrimaryContainer,
              child: Text(_initial(controller.vehicleName)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(controller.vehicleName, style: text.titleLarge),
                  Text(
                    'Бензин и газ',
                    style: text.bodyMedium?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            IconButton(
              key: const Key('rename-vehicle'),
              tooltip: 'Имя автомобиля',
              onPressed: () => renameVehicle(context, controller),
              icon: const Icon(Icons.edit_outlined),
            ),
          ],
        ),
        if (controller.entries.isEmpty) ...[
          const SizedBox(height: 16),
          const _StartHint(),
        ],
        const SizedBox(height: 16),
        _FuelCard(controller: controller, fuel: FuelType.petrol),
        const SizedBox(height: 12),
        _FuelCard(controller: controller, fuel: FuelType.lpg),
        const SizedBox(height: 20),
        Text('Как считается', style: text.titleSmall),
        const SizedBox(height: 4),
        Text(
          formulaFootnote,
          style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
        ),
      ],
    );
  }
}

String _initial(String name) {
  final trimmed = name.trim();
  if (trimmed.isEmpty) return '?';
  return trimmed.substring(0, 1).toUpperCase();
}

class _StartHint extends StatelessWidget {
  const _StartHint();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Card(
      color: scheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.flag_outlined, color: scheme.onPrimaryContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'С чего начать',
                    style: text.titleMedium?.copyWith(
                      color: scheme.onPrimaryContainer,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Кнопка «Заправка» внизу. Нужны две полные заправки, и расход не подменяется нулём.',
                    style: text.bodyMedium?.copyWith(
                      color: scheme.onPrimaryContainer,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
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
    final scheme = Theme.of(context).colorScheme;
    final latest = _Readout(
      label: 'Последний цикл',
      valueKey: Key('consumption-${fuel.name}-latest'),
      value: latestValue(report.latest),
      detail: latestDetail(report.latest),
      factual: report.latest?.ratePer100 != null,
    );
    final average = _Readout(
      label: 'Средний расход',
      valueKey: Key('consumption-${fuel.name}-average'),
      value: averageValue(report.average),
      detail: averageDetail(report.average),
      factual: report.average.litersPer100 != null,
    );
    final price = controller.price(fuel);
    final priceReadout = _Readout(
      label: 'Средняя цена',
      valueKey: Key('price-${fuel.name}-average'),
      value: priceValue(price),
      detail: priceDetail(price),
      factual: price.hasPrice,
    );
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: scheme.secondaryContainer,
                  foregroundColor: scheme.onSecondaryContainer,
                  child: Text(fuelMark(fuel)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(fuelLabel(fuel), style: text.titleLarge),
                      if (fuel == FuelType.lpg)
                        Text(
                          fuelHint(fuel),
                          style: text.bodySmall?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            latest,
            const SizedBox(height: 12),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: average),
                const SizedBox(width: 12),
                Expanded(child: priceReadout),
              ],
            ),
            if (report.unclosed != null) ...[
              const SizedBox(height: 12),
              DecoratedBox(
                decoration: BoxDecoration(
                  color: scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.timelapse,
                        size: 20,
                        color: scheme.onSurfaceVariant,
                      ),
                      const SizedBox(width: 8),
                      Expanded(child: Text(unclosedLine(fuel))),
                    ],
                  ),
                ),
              ),
            ],
            if (report.cycles.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text('Закрытые циклы', style: text.titleSmall),
              const SizedBox(height: 4),
              for (final cycle in report.cycles)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    cycleLine(cycle),
                    style: text.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }
}

class _Readout extends StatelessWidget {
  const _Readout({
    required this.label,
    required this.valueKey,
    required this.value,
    required this.detail,
    required this.factual,
  });

  final String label;
  final Key valueKey;
  final String value;
  final String detail;
  final bool factual;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: text.titleSmall),
        const SizedBox(height: 4),
        Text(
          value,
          key: valueKey,
          style: (factual ? text.headlineMedium : text.titleMedium)?.copyWith(
            fontWeight: FontWeight.w600,
            color: factual ? scheme.primary : scheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          detail,
          style: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
        ),
      ],
    );
  }
}
