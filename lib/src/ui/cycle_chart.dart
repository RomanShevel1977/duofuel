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

/// Верх шкалы. Ноль всегда внизу, чтобы небольшой разброс не занимал весь график.
double chartYMax(Iterable<double> rates) {
  var maxRate = 0.0;
  for (final rate in rates) {
    if (rate.isFinite && rate > maxRate) maxRate = rate;
  }
  if (maxRate <= 0) return 1;
  return maxRate * 1.25;
}

String formatChartDate(int milliseconds) {
  final date = DateTime.fromMillisecondsSinceEpoch(milliseconds);
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(date.day)}.${two(date.month)}.${date.year}';
}

String formatChartRate(double value) =>
    value.toStringAsFixed(1).replaceAll('.', ',');

/// Подписи, которые не пересекаются. Первая и последняя остаются, даже если тесно.
List<int> axisLabelsThatFit(
  List<double> centers,
  List<double> widths, {
  double gap = 8,
  double minLeft = 0,
  double maxRight = double.infinity,
}) {
  if (centers.isEmpty) return const [];
  bool fits(int index, List<int> chosen) {
    final left = math.max(minLeft, centers[index] - widths[index] / 2);
    final right = math.min(maxRight, centers[index] + widths[index] / 2);
    if (right - left < 4) return false;
    for (final other in chosen) {
      final otherLeft = centers[other] - widths[other] / 2;
      final otherRight = centers[other] + widths[other] / 2;
      if (left < otherRight + gap && right > otherLeft - gap) return false;
    }
    return true;
  }

  if (centers.length == 1) return fits(0, const []) ? const [0] : const [];
  final chosen = <int>[];
  if (fits(0, chosen)) chosen.add(0);
  final last = centers.length - 1;
  if (fits(last, chosen)) chosen.add(last);
  for (var index = 1; index < last; index++) {
    if (fits(index, chosen)) chosen.add(index);
  }
  chosen.sort();
  return chosen;
}

String cycleChartSemantics(List<CycleChartPoint> points) {
  final lines = <String>[
    'График расхода закрытых циклов, шкала от нуля, л/100 км.',
    for (final point in points)
      '${fuelLabel(point.fuel)}, ${formatChartDate(point.endMs)}, ${formatRate(point.ratePer100)}',
  ];
  return lines.join(' ');
}

List<String> chartAxisDates(List<CycleChartPoint> points) {
  if (points.isEmpty) return const [];
  final first = formatChartDate(points.first.endMs);
  final last = formatChartDate(points.last.endMs);
  if (points.length < 3 || first == last) {
    return first == last ? [first] : [first, last];
  }
  final middle = formatChartDate(points[points.length ~/ 2].endMs);
  if (middle == first || middle == last) return [first, last];
  return [first, middle, last];
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
    final dates = chartAxisDates(points);
    return Semantics(
      container: true,
      label: cycleChartSemantics(points),
      child: ExcludeSemantics(
        child: Card(
          key: const Key('cycle-chart'),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Расход по циклам', style: text.titleMedium),
                Text(
                  'Даты закрытия, шкала от нуля, л/100 км.',
                  style: text.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 8),
                SizedBox(
                  height: 140,
                  width: double.infinity,
                  child: CustomPaint(
                    painter: _CycleChartPainter(
                      points: points,
                      petrol: scheme.primary,
                      lpg: scheme.tertiary,
                      axis: scheme.outline,
                      label: text.labelSmall,
                    ),
                  ),
                ),
                Row(
                  children: [
                    for (var index = 0; index < dates.length; index++)
                      Expanded(
                        child: Text(
                          dates[index],
                          key: index == 0 ? const Key('cycle-axis-date') : null,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          textAlign: dates.length == 1
                              ? TextAlign.center
                              : index == 0
                              ? TextAlign.left
                              : index == dates.length - 1
                              ? TextAlign.right
                              : TextAlign.center,
                          style: text.labelMedium,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 16,
                  runSpacing: 4,
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
    final plot = Rect.fromLTWH(28, 8, size.width - 36, size.height - 12);
    final yMax = chartYMax(points.map((point) => point.ratePer100));
    canvas.drawLine(
      plot.bottomLeft,
      plot.bottomRight,
      Paint()
        ..color = axis
        ..strokeWidth = 1,
    );
    canvas.drawLine(
      plot.bottomLeft,
      plot.topLeft,
      Paint()
        ..color = axis
        ..strokeWidth = 1,
    );
    _tick(canvas, plot, '0', plot.bottom);
    _tick(canvas, plot, formatChartRate(yMax), plot.top);

    var minTime = points.first.endMs;
    var maxTime = points.first.endMs;
    for (final point in points) {
      minTime = math.min(minTime, point.endMs);
      maxTime = math.max(maxTime, point.endMs);
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
      final y = plot.bottom - plot.height * (point.ratePer100 / yMax);
      return Offset(x, y);
    }

    void series(FuelType fuel, Color color) {
      final indexed = <int>[];
      for (var index = 0; index < points.length; index++) {
        if (points[index].fuel == fuel) indexed.add(index);
      }
      final spots = [for (final index in indexed) place(points[index], index)];
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
      final texts = <TextPainter>[];
      final centers = <double>[];
      final widths = <double>[];
      for (final spot in spots) {
        final painter = TextPainter(
          text: TextSpan(
            text: formatChartRate(points[indexed[texts.length]].ratePer100),
            style: label?.copyWith(color: color),
          ),
          textDirection: TextDirection.ltr,
          maxLines: 1,
        )..layout();
        texts.add(painter);
        centers.add(spot.dx);
        widths.add(painter.width);
      }
      final visible = axisLabelsThatFit(
        centers,
        widths,
        minLeft: plot.left,
        maxRight: plot.right,
      ).toSet();
      for (var index = 0; index < spots.length; index++) {
        final spot = spots[index];
        canvas.drawCircle(spot, 5, Paint()..color = color);
        if (!visible.contains(index)) continue;
        final painter = texts[index];
        final dx = (spot.dx - painter.width / 2)
            .clamp(plot.left, plot.right - painter.width)
            .toDouble();
        painter.paint(canvas, Offset(dx, spot.dy - painter.height - 4));
      }
    }

    series(FuelType.petrol, petrol);
    series(FuelType.lpg, lpg);
  }

  void _tick(Canvas canvas, Rect plot, String text, double y) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: label),
      textDirection: TextDirection.ltr,
      maxLines: 1,
    )..layout(maxWidth: 32);
    painter.paint(canvas, Offset(0, y - painter.height / 2));
  }

  @override
  bool shouldRepaint(covariant _CycleChartPainter oldDelegate) {
    return oldDelegate.points != points ||
        oldDelegate.petrol != petrol ||
        oldDelegate.lpg != lpg;
  }
}
