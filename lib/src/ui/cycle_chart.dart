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

const chartPlotHeight = 140.0;
const chartPlotTop = 8.0;
const chartPlotBottomInset = 28.0;
const chartPlotLeft = 4.0;
const chartPlotRightInset = 8.0;
const chartMinPointGap = 64.0;
const chartHitRadius = 28.0;

/// Ширина полотна. При длинной истории она шире экрана, точки не сжимаются в одну строку.
double chartScrollWidth({required int count, required double viewportWidth}) {
  final viewport = math.max(viewportWidth, 1.0);
  if (count <= 1) return viewport;
  final needed =
      chartPlotLeft + chartPlotRightInset + (count - 1) * chartMinPointGap;
  return math.max(viewport, needed);
}

class ChartSpot {
  const ChartSpot({required this.x, required this.y, required this.point});

  final double x;
  final double y;
  final CycleChartPoint point;
}

Rect chartPlot(Size size) {
  return Rect.fromLTWH(
    chartPlotLeft,
    chartPlotTop,
    math.max(1, size.width - chartPlotLeft - chartPlotRightInset),
    math.max(1, size.height - chartPlotTop - chartPlotBottomInset),
  );
}

List<ChartSpot> chartSpots({
  required List<CycleChartPoint> points,
  required double width,
  required double height,
}) {
  if (points.isEmpty) return const [];
  final plot = chartPlot(Size(width, height));
  final yMax = chartYMax([
    for (final point in points)
      if (point.ratePer100 != null) point.ratePer100!,
  ]);
  var minTime = points.first.endMs;
  var maxTime = points.first.endMs;
  for (final point in points) {
    minTime = math.min(minTime, point.endMs);
    maxTime = math.max(maxTime, point.endMs);
  }
  return [
    for (var index = 0; index < points.length; index++)
      ChartSpot(
        x: chartPointX(
          endMs: points[index].endMs,
          index: index,
          count: points.length,
          minTime: minTime,
          maxTime: maxTime,
          left: plot.left,
          width: plot.width,
        ),
        y: points[index].scaleBreak || points[index].ratePer100 == null
            ? plot.center.dy
            : plot.bottom - plot.height * (points[index].ratePer100! / yMax),
        point: points[index],
      ),
  ];
}

int? chartHitIndex({
  required List<double> xs,
  required List<double> ys,
  required double x,
  required double y,
  double radius = chartHitRadius,
}) {
  int? best;
  var bestDistance = radius;
  for (var index = 0; index < xs.length; index++) {
    final dx = xs[index] - x;
    final dy = ys[index] - y;
    final distance = math.sqrt(dx * dx + dy * dy);
    if (distance <= bestDistance) {
      best = index;
      bestDistance = distance;
    }
  }
  return best;
}

String chartPointDetail(CycleChartPoint point) {
  final date = formatChartDate(point.endMs);
  if (point.scaleBreak || point.ratePer100 == null) {
    return '$date · разрыв шкалы, расход не посчитан';
  }
  return '$date · ${formatRate(point.ratePer100!)}';
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

class CycleChart extends StatefulWidget {
  const CycleChart({super.key, required this.points});

  final List<CycleChartPoint> points;

  @override
  State<CycleChart> createState() => _CycleChartState();
}

class _CycleChartState extends State<CycleChart> {
  int? _selected;

  @override
  void didUpdateWidget(covariant CycleChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.points != widget.points) _selected = null;
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final points = widget.points;
    final petrol = points.any((point) => point.fuel == FuelType.petrol);
    final lpg = points.any((point) => point.fuel == FuelType.lpg);
    final selected = _selected;
    final yMax = chartYMax([
      for (final point in points)
        if (point.ratePer100 != null) point.ratePer100!,
    ]);
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
                  'Даты закрытия, шкала от нуля, л/100 км. Нажмите точку.',
                  style: text.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                if (selected != null &&
                    selected >= 0 &&
                    selected < points.length)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      chartPointDetail(points[selected]),
                      key: const Key('chart-point-detail'),
                      style: text.titleSmall,
                    ),
                  ),
                const SizedBox(height: 8),
                SizedBox(
                  height: chartPlotHeight,
                  width: double.infinity,
                  child: Row(
                    children: [
                      SizedBox(
                        width: 36,
                        child: Padding(
                          padding: const EdgeInsets.only(top: 4, bottom: 24),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                formatChartRate(yMax),
                                style: text.labelSmall,
                              ),
                              Text('0', style: text.labelSmall),
                            ],
                          ),
                        ),
                      ),
                      Expanded(
                        child: LayoutBuilder(
                          builder: (context, constraints) {
                            final width = chartScrollWidth(
                              count: points.length,
                              viewportWidth: constraints.maxWidth,
                            );
                            return SingleChildScrollView(
                              key: const Key('cycle-chart-scroll'),
                              scrollDirection: Axis.horizontal,
                              child: GestureDetector(
                                key: const Key('cycle-chart-plot'),
                                onTapUp: (details) =>
                                    _select(details.localPosition, width),
                                child: CustomPaint(
                                  size: Size(width, chartPlotHeight),
                                  painter: _CycleChartPainter(
                                    points: points,
                                    selected: selected,
                                    petrol: scheme.primary,
                                    lpg: scheme.tertiary,
                                    axis: scheme.outline,
                                    label: text.labelSmall,
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ],
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

  void _select(Offset position, double width) {
    final spots = chartSpots(
      points: widget.points,
      width: width,
      height: chartPlotHeight,
    );
    final index = chartHitIndex(
      xs: [for (final spot in spots) spot.x],
      ys: [for (final spot in spots) spot.y],
      x: position.dx,
      y: position.dy,
    );
    setState(() => _selected = index);
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
    required this.selected,
    required this.petrol,
    required this.lpg,
    required this.axis,
    required this.label,
  });

  final List<CycleChartPoint> points;
  final int? selected;
  final Color petrol;
  final Color lpg;
  final Color axis;
  final TextStyle? label;

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty) return;
    final plot = chartPlot(size);
    final placed = chartSpots(
      points: points,
      width: size.width,
      height: size.height,
    );
    canvas.drawLine(
      plot.bottomLeft,
      plot.bottomRight,
      Paint()
        ..color = axis
        ..strokeWidth = 1,
    );
    double xOf(int index) => placed[index].x;
    double yOf(int index) => placed[index].y;

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
        final spot = Offset(x, yOf(index));
        segment.add(spot);
        canvas.drawCircle(spot, 5, Paint()..color = color);
        if (selected == index) {
          canvas.drawCircle(
            spot,
            9,
            Paint()
              ..color = color
              ..style = PaintingStyle.stroke
              ..strokeWidth = 2,
          );
        }
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

  @override
  bool shouldRepaint(covariant _CycleChartPainter oldDelegate) {
    return oldDelegate.points != points ||
        oldDelegate.selected != selected ||
        oldDelegate.petrol != petrol ||
        oldDelegate.lpg != lpg;
  }
}
