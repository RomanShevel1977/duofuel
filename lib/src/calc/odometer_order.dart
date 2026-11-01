import '../model/records.dart';
import 'validation.dart';

/// Точка шкалы: заправка или замена панели.
class _Point {
  const _Point({
    required this.sortKey,
    required this.odometerKm,
    required this.isReset,
    required this.id,
  });

  final SortKey sortKey;
  final int odometerKm;
  final bool isReset;
  final String id;
}

List<_Point> _timeline({
  required List<FuelEntry> entries,
  required List<OdometerReset> resets,
  required String exceptEntryId,
  _Point? draft,
}) {
  return [
    for (final entry in entries)
      if (entry.id != exceptEntryId)
        _Point(
          sortKey: entry.sortKey,
          odometerKm: entry.odometerKm,
          isReset: false,
          id: entry.id,
        ),
    for (final reset in resets)
      _Point(
        sortKey: reset.sortKey,
        odometerKm: reset.odometerKm,
        isReset: true,
        id: reset.id,
      ),
    ?draft,
  ]..sort((a, b) => a.sortKey.compareTo(b.sortKey));
}

/// Новая заправка в ту же минуту встаёт после уже сохранённых показаний.
const newFillSortKm = 0x7fffffff;

_Point _draft({
  required String id,
  required int occurredAtMs,
  required int createdAtMs,
  required int positionOdometerKm,
  required int odometerKm,
}) {
  return _Point(
    sortKey: SortKey(occurredAtMs, positionOdometerKm, createdAtMs, id),
    odometerKm: odometerKm,
    isReset: false,
    id: id,
  );
}

/// Предыдущее показание перед этой заправкой. Число в поле не подставляется.
int? previousOdometerHint({
  required List<FuelEntry> entries,
  required List<OdometerReset> resets,
  required int occurredAtMs,
  required int createdAtMs,
  required String exceptEntryId,
  int? savedOdometerKm,
}) {
  final position = savedOdometerKm ?? newFillSortKm;
  final points = _timeline(
    entries: entries,
    resets: resets,
    exceptEntryId: exceptEntryId,
    draft: _draft(
      id: exceptEntryId,
      occurredAtMs: occurredAtMs,
      createdAtMs: createdAtMs,
      positionOdometerKm: position,
      odometerKm: position,
    ),
  );
  final index = points.indexWhere((point) => point.id == exceptEntryId);
  if (index <= 0) return null;
  return points[index - 1].odometerKm;
}

/// Меньшее показание без замены панели между точками не сохраняется.
List<FieldIssue> validateOdometerNotLower({
  required int odometerKm,
  required int occurredAtMs,
  required int createdAtMs,
  required String id,
  required List<FuelEntry> entries,
  required List<OdometerReset> resets,
  int? savedOdometerKm,
}) {
  final points = _timeline(
    entries: entries,
    resets: resets,
    exceptEntryId: id,
    draft: _draft(
      id: id,
      occurredAtMs: occurredAtMs,
      createdAtMs: createdAtMs,
      positionOdometerKm: savedOdometerKm ?? newFillSortKm,
      odometerKm: odometerKm,
    ),
  );
  final index = points.indexWhere((point) => point.id == id && !point.isReset);
  if (index < 0) return const [];
  if (index > 0 && odometerKm < points[index - 1].odometerKm) {
    return const [
      FieldIssue(
        odometerField,
        'показание меньше предыдущего. Без замены панели так сохранить нельзя',
      ),
    ];
  }
  final nextIndex = index + 1;
  if (nextIndex < points.length &&
      !points[nextIndex].isReset &&
      points[nextIndex].odometerKm < odometerKm) {
    return const [
      FieldIssue(
        odometerField,
        'после этого показания уже есть меньшее. Без замены панели так сохранить нельзя',
      ),
    ];
  }
  return const [];
}
