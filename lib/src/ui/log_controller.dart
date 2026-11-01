import 'package:flutter/foundation.dart';

import '../calc/fuel_cycle.dart';
import '../calc/price_average.dart';
import '../data/fuel_log.dart';
import '../model/records.dart';

class LogController extends ChangeNotifier {
  LogController(this.log);

  final FuelLog log;
  bool loading = true;
  String? readError;
  String vehicleName = '';
  List<FuelEntry> entries = const [];
  List<OdometerReset> resets = const [];
  FuelSnapshot _snapshot = FuelSnapshot.initial();

  bool get canWrite => readError == null && !loading;

  FuelConsumption consumption(FuelType fuel) =>
      consumptionFor(entries, resets, fuel);

  PriceAverage price(FuelType fuel) => averagePrice(entries, fuel);

  Future<void> reload() async {
    try {
      final snapshot = await log.read();
      _apply(snapshot);
    } on FuelLogReadException catch (error) {
      readError = error.message;
      loading = false;
      notifyListeners();
    }
  }

  Future<void> rename(String name) async {
    if (!canWrite) return;
    await _commit(_snapshot.copyWith(vehicleName: name.trim()));
  }

  Future<void> saveEntry(FuelEntry entry) async {
    if (!canWrite) return;
    final next = [..._snapshot.entries];
    final index = next.indexWhere((item) => item.id == entry.id);
    if (index >= 0) {
      next[index] = entry;
    } else {
      next.add(entry);
    }
    await _commit(_snapshot.copyWith(entries: next));
  }

  Future<void> deleteEntry(String id) async {
    if (!canWrite) return;
    await _commit(
      _snapshot.copyWith(
        entries: _snapshot.entries.where((item) => item.id != id).toList(),
      ),
    );
  }

  Future<void> saveReset(OdometerReset reset) async {
    if (!canWrite) return;
    final next = [..._snapshot.resets];
    final index = next.indexWhere((item) => item.id == reset.id);
    if (index >= 0) {
      next[index] = reset;
    } else {
      next.add(reset);
    }
    await _commit(_snapshot.copyWith(resets: next));
  }

  Future<void> deleteReset(String id) async {
    if (!canWrite) return;
    await _commit(
      _snapshot.copyWith(
        resets: _snapshot.resets.where((item) => item.id != id).toList(),
      ),
    );
  }

  Future<void> _commit(FuelSnapshot snapshot) async {
    await log.write(snapshot);
    _apply(snapshot);
  }

  void _apply(FuelSnapshot snapshot) {
    _snapshot = snapshot;
    vehicleName = snapshot.vehicleName;
    entries = snapshot.entries;
    resets = snapshot.resets;
    readError = null;
    loading = false;
    notifyListeners();
  }
}
