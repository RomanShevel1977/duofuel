import '../model/records.dart';

class FuelLogReadException implements Exception {
  const FuelLogReadException(this.message);

  final String message;

  @override
  String toString() => message;
}

abstract class FuelLog {
  Future<FuelSnapshot> read();

  Future<void> write(FuelSnapshot snapshot);
}
