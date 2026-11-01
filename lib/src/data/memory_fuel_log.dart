import '../model/records.dart';
import 'fuel_log.dart';

class MemoryFuelLog implements FuelLog {
  FuelSnapshot snapshot = FuelSnapshot.initial();
  int writes = 0;

  @override
  Future<FuelSnapshot> read() async => snapshot;

  @override
  Future<void> write(FuelSnapshot snapshot) async {
    writes += 1;
    this.snapshot = snapshot;
  }
}
