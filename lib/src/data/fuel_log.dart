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

  /// Содержимое файла как есть, даже если это не журнал. Null, если файла нет.
  Future<String?> rawJournal();

  /// Оставляет битый файл под новым именем и пишет пустой журнал на его место.
  Future<String?> keepBrokenAndStartEmpty();
}
