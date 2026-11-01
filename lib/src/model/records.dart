/// Записи расхода бензина и LPG и сумма чека заправки.
/// Сервис, CNG и второй автомобиль в эту модель не входят.
enum FuelType {
  petrol('PETROL'),
  lpg('LPG');

  const FuelType(this.wire);

  final String wire;

  static FuelType? fromWire(String wire) {
    for (final fuel in FuelType.values) {
      if (fuel.wire == wire) return fuel;
    }
    return null;
  }
}

enum Quality { factual, insufficient }

/// OPEN-SORT: occurred_at, одометр, created_at, id — все по возрастанию.
class SortKey implements Comparable<SortKey> {
  const SortKey(this.occurredAtMs, this.odometerKm, this.createdAtMs, this.id);

  final int occurredAtMs;
  final int odometerKm;
  final int createdAtMs;
  final String id;

  @override
  int compareTo(SortKey other) {
    final byTime = occurredAtMs.compareTo(other.occurredAtMs);
    if (byTime != 0) return byTime;
    final byOdometer = odometerKm.compareTo(other.odometerKm);
    if (byOdometer != 0) return byOdometer;
    final byCreated = createdAtMs.compareTo(other.createdAtMs);
    if (byCreated != 0) return byCreated;
    return id.compareTo(other.id);
  }
}

class FuelEntry {
  const FuelEntry({
    required this.id,
    required this.fuelType,
    required this.occurredAtMs,
    required this.odometerKm,
    required this.quantity,
    required this.unit,
    required this.isFull,
    required this.createdAtMs,
    this.totalMinor,
    this.currency,
  });

  final String id;
  final FuelType fuelType;
  final int occurredAtMs;
  final int odometerKm;
  final double quantity;
  final String unit;
  final bool isFull;
  final int createdAtMs;

  /// Сумма чека в минимальных единицах валюты. Пустое поле — не ноль.
  final int? totalMinor;

  /// Валюта суммы чека. Для новой записи это UAH, как в прежнем приложении.
  final String? currency;

  SortKey get sortKey => SortKey(occurredAtMs, odometerKm, createdAtMs, id);

  FuelEntry copyWith({
    FuelType? fuelType,
    int? occurredAtMs,
    int? odometerKm,
    double? quantity,
    String? unit,
    bool? isFull,
    int? totalMinor,
    String? currency,
  }) {
    return FuelEntry(
      id: id,
      fuelType: fuelType ?? this.fuelType,
      occurredAtMs: occurredAtMs ?? this.occurredAtMs,
      odometerKm: odometerKm ?? this.odometerKm,
      quantity: quantity ?? this.quantity,
      unit: unit ?? this.unit,
      isFull: isFull ?? this.isFull,
      createdAtMs: createdAtMs,
      totalMinor: totalMinor ?? this.totalMinor,
      currency: currency ?? this.currency,
    );
  }
}

/// Замена панели. Только RESET рвёт шкалу цикла; обычное показание заправки — на самой заправке.
class OdometerReset {
  const OdometerReset({
    required this.id,
    required this.occurredAtMs,
    required this.odometerKm,
    required this.createdAtMs,
    this.linkedFuelEntryId,
  });

  final String id;
  final int occurredAtMs;
  final int odometerKm;
  final int createdAtMs;

  /// Если сброс привязан к границе цикла, шкала этой границей не рвётся — как в DistanceCalculator.
  final String? linkedFuelEntryId;

  SortKey get sortKey => SortKey(occurredAtMs, odometerKm, createdAtMs, id);
}

class FuelSnapshot {
  FuelSnapshot({
    required this.vehicleName,
    required List<FuelEntry> entries,
    required List<OdometerReset> resets,
  }) : entries = List.unmodifiable(entries),
       resets = List.unmodifiable(resets);

  factory FuelSnapshot.initial() => FuelSnapshot(
    vehicleName: 'Автомобиль',
    entries: const [],
    resets: const [],
  );

  final String vehicleName;
  final List<FuelEntry> entries;
  final List<OdometerReset> resets;

  FuelSnapshot copyWith({
    String? vehicleName,
    List<FuelEntry>? entries,
    List<OdometerReset>? resets,
  }) {
    return FuelSnapshot(
      vehicleName: vehicleName ?? this.vehicleName,
      entries: entries ?? this.entries,
      resets: resets ?? this.resets,
    );
  }
}
