import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../calc/fuel_cycle.dart';
import '../model/records.dart';
import 'format.dart';

class CycleChartPoint {
  const CycleChartPoint({
    required this.fuel,
    required this.endMs,
    required this.ratePer100,
  });

  final FuelType fuel;
  final int endMs;

  /// Уже посчитанный фактический расход цикла. Новая формула здесь не считается.
  final double ratePer100;
}

List<CycleChartPoint> cycleChartPoints(
  List<FuelEntry> entries,
  List<OdometerReset> resets,
) {
  final points = <CycleChartPoint>[];
  for (final fuel in FuelType.values) {
    for (final cycle in cycles(entries, resets, fuel)) {
      final rate = cycle.ratePer100;
      if (rate == null || !rate.isFinite) continue;
      points.add(
        CycleChartPoint(fuel: fuel, endMs: cycle.endMs, ratePer100: rate),
      );
    }
  }
  points.sort((a, b) {
    final byTime = a.endMs.compareTo(b.endMs);
    if (byTime != 0) return byTime;
    return a.fuel.index.compareTo(b.fuel.index);
  });
  return points;
}

class CycleChart extends StatelessWidget {
  const CycleChart({super.key, required this.points});

  final List<CycleChartPoint> points;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final petrol = points.any((point) => point.fuel == FuelType.petrol);
    final lpg = points.any((point) => point.fuel == FuelType.lpg);
    return Card(
      key: const Key('cycle-chart'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Расход по циклам', style: text.titleLarge),
            const SizedBox(height: 4),
            Text(
              'Точки — фактический расход закрытых циклов.',
              style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 180,
              width: double.infinity,
              child: CustomPaint(
                painter: _CycleChartPainter(
                  points: points,
                  petrol: scheme.primary,
                  lpg: scheme.tertiary,
                  axis: scheme.outlineVariant,
                  label: text.labelSmall?.copyWith(fontWeight: FontWeight.w600),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 16,
              runSpacing: 8,
              children: [
                if (petrol)
                  _Legend(
                    key: const Key('cycle-series-petrol'),
                    color: scheme.primary,
                    label: 'Бензин',
                  ),
                if (lpg)
                  _Legend(
                    key: const Key('cycle-series-lpg'),
                    color: scheme.tertiary,
                    label: 'Газ',
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  const _Legend({super.key, required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 8),
        Text(label),
      ],
    );
  }
}

class _CycleChartPainter extends CustomPainter {
  const _CycleChartPainter({
    required this.points,
    required this.petrol,
    required this.lpg,
    required this.axis,
    required this.label,
  });

  final List<CycleChartPoint> points;
  final Color petrol;
  final Color lpg;
  final Color axis;
  final TextStyle? label;

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty) return;
    final plot = Rect.fromLTWH(8, 20, size.width - 16, size.height - 28);
    canvas.drawRect(
      plot,
      Paint()
        ..color = axis
        ..style = PaintingStyle.stroke,
    );
    var minRate = points.first.ratePer100;
    var maxRate = points.first.ratePer100;
    var minTime = points.first.endMs;
    var maxTime = points.first.endMs;
    for (final point in points) {
      minRate = math.min(minRate, point.ratePer100);
      maxRate = math.max(maxRate, point.ratePer100);
      minTime = math.min(minTime, point.endMs);
      maxTime = math.max(maxTime, point.endMs);
    }
    if ((maxRate - minRate).abs() < 1e-9) {
      minRate -= 1;
      maxRate += 1;
    }
    Offset place(CycleChartPoint point, int index) {
      final double x;
      if (points.length == 1 || minTime == maxTime) {
        x = plot.left + plot.width * ((index + 1) / (points.length + 1));
      } else {
        x =
            plot.left +
            plot.width * ((point.endMs - minTime) / (maxTime - minTime));
      }
      final y =
          plot.bottom -
          plot.height * ((point.ratePer100 - minRate) / (maxRate - minRate));
      return Offset(x, y);
    }

    void series(FuelType fuel, Color color) {
      final spots = <Offset>[];
      for (var index = 0; index < points.length; index++) {
        final point = points[index];
        if (point.fuel != fuel) continue;
        spots.add(place(point, index));
      }
      if (spots.length >= 2) {
        final path = Path()..moveTo(spots.first.dx, spots.first.dy);
        for (final spot in spots.skip(1)) {
          path.lineTo(spot.dx, spot.dy);
        }
        canvas.drawPath(
          path,
          Paint()
            ..color = color
            ..style = PaintingStyle.stroke
            ..strokeWidth = 3
            ..strokeCap = StrokeCap.round,
        );
      }
      var spotIndex = 0;
      for (var index = 0; index < points.length; index++) {
        final point = points[index];
        if (point.fuel != fuel) continue;
        final spot = spots[spotIndex++];
        canvas.drawCircle(spot, 5, Paint()..color = color);
        final painter = TextPainter(
          text: TextSpan(text: formatRate(point.ratePer100), style: label),
          textDirection: TextDirection.ltr,
        )..layout(maxWidth: plot.width);
        final dx = (spot.dx - painter.width / 2)
            .clamp(plot.left, plot.right - painter.width)
            .toDouble();
        painter.paint(canvas, Offset(dx, spot.dy - painter.height - 8));
      }
    }

    series(FuelType.petrol, petrol);
    series(FuelType.lpg, lpg);
  }

  @override
  bool shouldRepaint(covariant _CycleChartPainter oldDelegate) {
    return oldDelegate.points != points ||
        oldDelegate.petrol != petrol ||
        oldDelegate.lpg != lpg;
  }
}
