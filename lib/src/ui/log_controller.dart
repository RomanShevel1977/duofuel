import 'package:flutter/foundation.dart';

import '../calc/fuel_cycle.dart';
import '../calc/price_average.dart';
import '../data/file_fuel_log.dart';
import '../data/fuel_log.dart';
import '../model/records.dart';

const journalWriteError = 'Не удалось записать журнал.';

class LogController extends ChangeNotifier {
  LogController(this.log);

  final FuelLog log;
  bool loading = true;
  String? readError;
  String? writeError;
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

  String journalSnapshot() => snapshotText(_snapshot);

  Future<String?> rawJournal() => log.rawJournal();

  Future<String?> startEmptyJournal() async {
    final kept = await log.keepBrokenAndStartEmpty();
    await reload();
    return kept;
  }

  Future<bool> importSnapshot(String text) async {
    final snapshot = decodeSnapshot(text);
    return _commit(snapshot);
  }

  Future<bool> rename(String name) async {
    if (!canWrite) return false;
    return _commit(_snapshot.copyWith(vehicleName: name.trim()));
  }

  Future<bool> saveEntry(FuelEntry entry) async {
    if (!canWrite) return false;
    final next = [..._snapshot.entries];
    final index = next.indexWhere((item) => item.id == entry.id);
    if (index >= 0) {
      next[index] = entry;
    } else {
      next.add(entry);
    }
    return _commit(_snapshot.copyWith(entries: next));
  }

  Future<bool> deleteEntry(String id) async {
    if (!canWrite) return false;
    return _commit(
      _snapshot.copyWith(
        entries: _snapshot.entries.where((item) => item.id != id).toList(),
      ),
    );
  }

  Future<bool> saveReset(OdometerReset reset) async {
    if (!canWrite) return false;
    final next = [..._snapshot.resets];
    final index = next.indexWhere((item) => item.id == reset.id);
    if (index >= 0) {
      next[index] = reset;
    } else {
      next.add(reset);
    }
    return _commit(_snapshot.copyWith(resets: next));
  }

  Future<bool> deleteReset(String id) async {
    if (!canWrite) return false;
    return _commit(
      _snapshot.copyWith(
        resets: _snapshot.resets.where((item) => item.id != id).toList(),
      ),
    );
  }

  Future<bool> _commit(FuelSnapshot snapshot) async {
    try {
      await log.write(snapshot);
    } catch (_) {
      writeError = journalWriteError;
      notifyListeners();
      return false;
    }
    writeError = null;
    _apply(snapshot);
    return true;
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
