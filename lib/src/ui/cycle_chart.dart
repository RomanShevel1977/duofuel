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
    this.scaleBreak = false,
  });

  final FuelType fuel;
  final int endMs;

  /// Фактический расход цикла. Пусто у разрыва шкалы: это не ноль.
  final double? ratePer100;
  final bool scaleBreak;
}

List<CycleChartPoint> cycleChartPoints(
  List<FuelEntry> entries,
  List<OdometerReset> resets,
) {
  final points = <CycleChartPoint>[];
  for (final fuel in FuelType.values) {
    for (final cycle in cycles(entries, resets, fuel)) {
      final rate = cycle.ratePer100;
      if (rate != null && rate.isFinite) {
        points.add(
          CycleChartPoint(fuel: fuel, endMs: cycle.endMs, ratePer100: rate),
        );
      } else if (cycle.rateWarnings.contains(CycleWarning.scaleBreak)) {
        points.add(
          CycleChartPoint(
            fuel: fuel,
            endMs: cycle.endMs,
            ratePer100: null,
            scaleBreak: true,
          ),
        );
      }
    }
  }
  points.sort((a, b) {
    final byTime = a.endMs.compareTo(b.endMs);
    if (byTime != 0) return byTime;
    return a.fuel.index.compareTo(b.fuel.index);
  });
  return points;
}

/// Та же координата X, что у точки. Даты рисуются на ней, а не равномерно по ширине.
double chartPointX({
  required int endMs,
  required int index,
  required int count,
  required int minTime,
  required int maxTime,
  required double left,
  required double width,
}) {
  if (count <= 1 || minTime == maxTime) {
    return left + width * ((index + 1) / (count + 1));
  }
  return left + width * ((endMs - minTime) / (maxTime - minTime));
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
      point.scaleBreak
          ? '${fuelLabel(point.fuel)}, ${formatChartDate(point.endMs)}, разрыв шкалы, расход не посчитан'
          : '${fuelLabel(point.fuel)}, ${formatChartDate(point.endMs)}, ${formatRate(point.ratePer100!)}',
  ];
  return lines.join(' ');
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
    final plot = Rect.fromLTWH(28, 8, size.width - 36, size.height - 28);
    final yMax = chartYMax([
      for (final point in points)
        if (point.ratePer100 != null) point.ratePer100!,
    ]);
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
    double xOf(int index) {
      return chartPointX(
        endMs: points[index].endMs,
        index: index,
        count: points.length,
        minTime: minTime,
        maxTime: maxTime,
        left: plot.left,
        width: plot.width,
      );
    }

    void series(FuelType fuel, Color color) {
      final stroke = Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3
        ..strokeCap = StrokeCap.round;
      final segment = <Offset>[];
      final texts = <TextPainter>[];
      final spots = <Offset>[];
      final centers = <double>[];
      final widths = <double>[];
      void flush() {
        if (segment.length >= 2) {
          final path = Path()..moveTo(segment.first.dx, segment.first.dy);
          for (final spot in segment.skip(1)) {
            path.lineTo(spot.dx, spot.dy);
          }
          canvas.drawPath(path, stroke);
        }
        segment.clear();
      }

      for (var index = 0; index < points.length; index++) {
        final point = points[index];
        if (point.fuel != fuel) continue;
        final x = xOf(index);
        if (point.scaleBreak || point.ratePer100 == null) {
          flush();
          if (point.scaleBreak) _gap(canvas, plot, x);
          continue;
        }
        final y = plot.bottom - plot.height * (point.ratePer100! / yMax);
        final spot = Offset(x, y);
        segment.add(spot);
        canvas.drawCircle(spot, 5, Paint()..color = color);
        final painter = TextPainter(
          text: TextSpan(
            text: formatChartRate(point.ratePer100!),
            style: label?.copyWith(color: color),
          ),
          textDirection: TextDirection.ltr,
          maxLines: 1,
        )..layout();
        texts.add(painter);
        spots.add(spot);
        centers.add(spot.dx);
        widths.add(painter.width);
      }
      flush();
      final visible = axisLabelsThatFit(
        centers,
        widths,
        minLeft: plot.left,
        maxRight: plot.right,
      ).toSet();
      for (var index = 0; index < texts.length; index++) {
        if (!visible.contains(index)) continue;
        final painter = texts[index];
        final spot = spots[index];
        final dx = (spot.dx - painter.width / 2)
            .clamp(plot.left, plot.right - painter.width)
            .toDouble();
        painter.paint(canvas, Offset(dx, spot.dy - painter.height - 4));
      }
    }

    series(FuelType.petrol, petrol);
    series(FuelType.lpg, lpg);
    _dates(canvas, plot, xOf);
  }

  void _dates(Canvas canvas, Rect plot, double Function(int index) xOf) {
    final painters = <TextPainter>[];
    final centers = <double>[];
    final widths = <double>[];
    for (var index = 0; index < points.length; index++) {
      final painter = TextPainter(
        text: TextSpan(
          text: formatChartDate(points[index].endMs),
          style: label,
        ),
        textDirection: TextDirection.ltr,
        maxLines: 1,
      )..layout();
      painters.add(painter);
      centers.add(xOf(index));
      widths.add(painter.width);
    }
    final visible = axisLabelsThatFit(
      centers,
      widths,
      minLeft: plot.left,
      maxRight: plot.right,
    );
    for (final index in visible) {
      final painter = painters[index];
      final dx = (centers[index] - painter.width / 2)
          .clamp(plot.left, plot.right - painter.width)
          .toDouble();
      painter.paint(canvas, Offset(dx, plot.bottom + 4));
    }
  }

  void _gap(Canvas canvas, Rect plot, double x) {
    final paint = Paint()
      ..color = axis
      ..strokeWidth = 2;
    final mid = plot.center.dy;
    canvas.drawLine(Offset(x, plot.top + 16), Offset(x, mid - 6), paint);
    canvas.drawLine(Offset(x, mid + 6), Offset(x, plot.bottom), paint);
    canvas.drawLine(Offset(x - 5, mid - 5), Offset(x + 5, mid + 5), paint);
    canvas.drawLine(Offset(x - 5, mid + 5), Offset(x + 5, mid - 5), paint);
    final painter = TextPainter(
      text: TextSpan(text: 'разрыв', style: label),
      textDirection: TextDirection.ltr,
      maxLines: 1,
    )..layout();
    final dx = (x - painter.width / 2)
        .clamp(plot.left, plot.right - painter.width)
        .toDouble();
    painter.paint(canvas, Offset(dx, plot.top));
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
