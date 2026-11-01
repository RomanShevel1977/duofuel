import 'dart:convert';
import 'dart:io';

import '../model/records.dart';
import 'fuel_log.dart';

const fuelLogReadError = 'Не удалось прочитать журнал. Файл не изменён.';

class FileFuelLog implements FuelLog {
  FileFuelLog(this.file);

  final File file;
  Future<void> _tail = Future<void>.value();

  Future<T> _sync<T>(Future<T> Function() action) {
    final next = _tail.then((_) => action());
    _tail = next.then((_) {}, onError: (_) {});
    return next;
  }

  @override
  Future<FuelSnapshot> read() {
    return _sync(() async {
      if (!await file.exists()) return FuelSnapshot.initial();
      final text = await file.readAsString();
      return decodeSnapshot(text);
    });
  }

  @override
  Future<void> write(FuelSnapshot snapshot) {
    return _sync(() async {
      final encoded = '${jsonEncode(encodeSnapshot(snapshot))}\n';
      final temporary = File('${file.path}.tmp');
      await temporary.writeAsString(encoded, flush: true);
      await temporary.rename(file.path);
    });
  }
}

Map<String, Object?> encodeSnapshot(FuelSnapshot snapshot) {
  return {
    'schema': 1,
    'vehicleName': snapshot.vehicleName,
    'entries': [
      for (final entry in snapshot.entries)
        {
          'id': entry.id,
          'fuelType': entry.fuelType.wire,
          'occurredAtMs': entry.occurredAtMs,
          'odometerKm': entry.odometerKm,
          'quantity': entry.quantity,
          'unit': entry.unit,
          'isFull': entry.isFull,
          'createdAtMs': entry.createdAtMs,
        },
    ],
    'resets': [
      for (final reset in snapshot.resets)
        {
          'id': reset.id,
          'occurredAtMs': reset.occurredAtMs,
          'odometerKm': reset.odometerKm,
          'createdAtMs': reset.createdAtMs,
        },
    ],
  };
}

FuelSnapshot decodeSnapshot(String text) {
  final Object? raw;
  try {
    raw = jsonDecode(text);
  } catch (_) {
    throw const FuelLogReadException(fuelLogReadError);
  }
  if (raw is! Map) throw const FuelLogReadException(fuelLogReadError);
  final map = raw;
  if (map['schema'] != 1) throw const FuelLogReadException(fuelLogReadError);
  final name = map['vehicleName'];
  final entriesRaw = map['entries'];
  final resetsRaw = map['resets'];
  if (name is! String || name.trim().isEmpty) {
    throw const FuelLogReadException(fuelLogReadError);
  }
  if (entriesRaw is! List || resetsRaw is! List) {
    throw const FuelLogReadException(fuelLogReadError);
  }
  try {
    return FuelSnapshot(
      vehicleName: name,
      entries: [for (final item in entriesRaw) _entry(item)],
      resets: [for (final item in resetsRaw) _reset(item)],
    );
  } on FuelLogReadException {
    rethrow;
  } catch (_) {
    throw const FuelLogReadException(fuelLogReadError);
  }
}

FuelEntry _entry(Object? raw) {
  if (raw is! Map) throw const FuelLogReadException(fuelLogReadError);
  final id = raw['id'];
  final fuel = raw['fuelType'];
  final occurred = raw['occurredAtMs'];
  final odometer = raw['odometerKm'];
  final quantity = raw['quantity'];
  final unit = raw['unit'];
  final full = raw['isFull'];
  final created = raw['createdAtMs'];
  final parsedFuel = fuel is String ? FuelType.fromWire(fuel) : null;
  if (id is! String ||
      id.isEmpty ||
      parsedFuel == null ||
      occurred is! int ||
      odometer is! int ||
      quantity is! num ||
      unit is! String ||
      unit.isEmpty ||
      full is! bool ||
      created is! int) {
    throw const FuelLogReadException(fuelLogReadError);
  }
  final liters = quantity.toDouble();
  if (!(liters > 0) || !liters.isFinite) {
    throw const FuelLogReadException(fuelLogReadError);
  }
  return FuelEntry(
    id: id,
    fuelType: parsedFuel,
    occurredAtMs: occurred,
    odometerKm: odometer,
    quantity: liters,
    unit: unit,
    isFull: full,
    createdAtMs: created,
  );
}

OdometerReset _reset(Object? raw) {
  if (raw is! Map) throw const FuelLogReadException(fuelLogReadError);
  final id = raw['id'];
  final occurred = raw['occurredAtMs'];
  final odometer = raw['odometerKm'];
  final created = raw['createdAtMs'];
  if (id is! String ||
      id.isEmpty ||
      occurred is! int ||
      odometer is! int ||
      created is! int) {
    throw const FuelLogReadException(fuelLogReadError);
  }
  return OdometerReset(
    id: id,
    occurredAtMs: occurred,
    odometerKm: odometer,
    createdAtMs: created,
  );
}
