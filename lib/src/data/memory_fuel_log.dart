import '../model/records.dart';
import 'fuel_log.dart';

class MemoryFuelLog implements FuelLog {
  FuelSnapshot snapshot = FuelSnapshot.initial();
  int writes = 0;
  String? brokenRaw;
  String? keptBrokenName;

  @override
  Future<FuelSnapshot> read() async => snapshot;

  @override
  Future<void> write(FuelSnapshot snapshot) async {
    writes += 1;
    this.snapshot = snapshot;
  }

  @override
  Future<String?> rawJournal() async => brokenRaw;

  @override
  Future<String?> keepBrokenAndStartEmpty() async {
    keptBrokenName = brokenRaw == null ? null : 'fuel_log.json.broken';
    brokenRaw = null;
    snapshot = FuelSnapshot.initial();
    writes += 1;
    return keptBrokenName;
  }
}
