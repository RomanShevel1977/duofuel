import '../calc/fuel_cycle.dart';
import '../model/records.dart';

const formulaFootnote =
    'Расход цикла — сумма заправок после открывающей полной заправки, включая закрывающую, '
    'делённая на километры между их одометрами и умноженная на 100. '
    'Средний расход считает ту же формулу по сумме таких циклов.';

String fuelLabel(FuelType fuel) => switch (fuel) {
  FuelType.petrol => 'Бензин',
  FuelType.lpg => 'Газ',
};

String fuelMark(FuelType fuel) => switch (fuel) {
  FuelType.petrol => 'Б',
  FuelType.lpg => 'Г',
};

String fuelHint(FuelType fuel) => switch (fuel) {
  FuelType.petrol => 'Бензин',
  FuelType.lpg => 'LPG',
};

FuelType lastFuelType(List<FuelEntry> entries) {
  FuelEntry? latest;
  for (final entry in entries) {
    if (latest == null || entry.sortKey.compareTo(latest.sortKey) > 0) {
      latest = entry;
    }
  }
  return latest?.fuelType ?? FuelType.petrol;
}

int? previousOdometerKm(
  List<FuelEntry> entries,
  FuelType fuel, {
  String? exceptId,
}) {
  FuelEntry? latest;
  for (final entry in entries) {
    if (entry.fuelType != fuel || entry.id == exceptId) continue;
    if (latest == null || entry.sortKey.compareTo(latest.sortKey) > 0) {
      latest = entry;
    }
  }
  return latest?.odometerKm;
}

String formatRate(double value) =>
    '${value.toStringAsFixed(1).replaceAll('.', ',')} л/100 км';

String formatQuantityInput(double value) =>
    value.toString().replaceAll('.', ',');

String formatLiters(double value) {
  if ((value - value.roundToDouble()).abs() < 1e-9) {
    return value.round().toString();
  }
  var text = value.toStringAsFixed(3);
  text = text.replaceFirst(RegExp(r'0+$'), '');
  text = text.replaceFirst(RegExp(r'\.$'), '');
  return text.replaceAll('.', ',');
}

String formatWhen(DateTime date) {
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(date.day)}.${two(date.month)}.${date.year} ${formatClock(date)}';
}

String formatClock(DateTime date) {
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(date.hour)}:${two(date.minute)}';
}

String dayHeading(DateTime day) {
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final target = DateTime(day.year, day.month, day.day);
  if (target == today) return 'Сегодня';
  if (target == today.subtract(const Duration(days: 1))) return 'Вчера';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(target.day)}.${two(target.month)}.${target.year}';
}

String formatWhenMs(int milliseconds) =>
    formatWhen(DateTime.fromMillisecondsSinceEpoch(milliseconds));

String cyclesWord(int count) {
  final mod10 = count % 10;
  final mod100 = count % 100;
  if (mod10 == 1 && mod100 != 11) return 'цикл';
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return 'цикла';
  return 'циклов';
}

String latestValue(CycleResult? cycle) {
  final rate = cycle?.ratePer100;
  if (rate == null) return 'Недостаточно данных';
  return formatRate(rate);
}

String latestDetail(CycleResult? cycle) {
  if (cycle == null) {
    return 'Отметьте две полные заправки.';
  }
  final rate = cycle.ratePer100;
  final volume = cycle.volume;
  final distance = cycle.distanceKm;
  if (rate != null && volume != null && distance != null) {
    return 'Фактический · ${formatLiters(volume)} л на $distance км';
  }
  final reasons = <String>[
    if (cycle.rateWarnings.contains(CycleWarning.scaleBreak))
      'шкала одометра разорвана заменой панели',
    if (cycle.rateWarnings.contains(CycleWarning.distanceNonPositive))
      'дистанция цикла не положительная',
    if (cycle.rateWarnings.contains(CycleWarning.mixedUnits))
      'разные единицы не складываются',
  ];
  final volumeText = volume == null ? '' : '${formatLiters(volume)} л · ';
  final why = reasons.isEmpty ? 'расход не посчитан' : reasons.join(', ');
  return 'Недостаточно данных · $volumeText$why';
}

String averageValue(FuelAverage average) {
  final rate = average.litersPer100;
  if (rate == null) return 'Недостаточно данных';
  return formatRate(rate);
}

String averageDetail(FuelAverage average) {
  final volume = average.volumeLiters;
  final distance = average.distanceKm;
  if (average.litersPer100 == null || volume == null || distance == null) {
    return 'Появится после первого закрытого цикла.';
  }
  return 'Фактический · ${formatLiters(volume)} л на $distance км · ${average.cycleCount} ${cyclesWord(average.cycleCount)}';
}

String cycleLine(CycleResult cycle) {
  final when = formatWhenMs(cycle.endMs);
  final rate = cycle.ratePer100;
  final volume = cycle.volume;
  final distance = cycle.distanceKm;
  if (rate != null && volume != null && distance != null) {
    return '$when · ${formatRate(rate)} · ${formatLiters(volume)} л · $distance км · Фактический';
  }
  return '$when · ${latestDetail(cycle)}';
}

String unclosedLine(FuelType fuel) =>
    '${fuelLabel(fuel)}: незакрытый цикл не показан как фактический расход.';

String entryTitle(FuelEntry entry) {
  final fill = entry.isFull ? 'полный бак' : 'частичная';
  return '${fuelLabel(entry.fuelType)} · ${formatLiters(entry.quantity)} л · $fill';
}
