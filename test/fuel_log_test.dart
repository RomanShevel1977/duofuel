import 'dart:io';

import 'package:duofuel/src/calc/fuel_cycle.dart';
import 'package:duofuel/src/data/file_fuel_log.dart';
import 'package:duofuel/src/data/fuel_log.dart';
import 'package:duofuel/src/data/memory_fuel_log.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/log_controller.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('file log keeps the fields the cycle needs', () async {
    final directory = await Directory.systemTemp.createTemp('duofuel-log');
    addTearDown(() => directory.delete(recursive: true));
    final log = FileFuelLog(File('${directory.path}/fuel_log.json'));
    expect(await File('${directory.path}/fuel_log.json').exists(), isFalse);
    final snapshot = FuelSnapshot(
      vehicleName: 'Ланос',
      entries: [
        const FuelEntry(
          id: 'open',
          fuelType: FuelType.petrol,
          occurredAtMs: 1,
          odometerKm: 1000,
          quantity: 40.5,
          unit: 'L',
          isFull: true,
          createdAtMs: 1,
        ),
        const FuelEntry(
          id: 'close',
          fuelType: FuelType.lpg,
          occurredAtMs: 2,
          odometerKm: 1300,
          quantity: 20,
          unit: 'L',
          isFull: true,
          createdAtMs: 3,
        ),
      ],
      resets: [
        const OdometerReset(
          id: 'panel',
          occurredAtMs: 4,
          odometerKm: 10,
          createdAtMs: 5,
        ),
      ],
    );
    await log.write(snapshot);
    final loaded = await log.read();
    expect(loaded.vehicleName, 'Ланос');
    expect(
      loaded.entries.singleWhere((entry) => entry.id == 'open').isFull,
      isTrue,
    );
    expect(
      loaded.entries.singleWhere((entry) => entry.id == 'open').quantity,
      40.5,
    );
    expect(
      loaded.entries.singleWhere((entry) => entry.id == 'close').fuelType,
      FuelType.lpg,
    );
    expect(loaded.resets.single.odometerKm, 10);
    expect(
      loaded.entries.singleWhere((entry) => entry.id == 'open').totalMinor,
      isNull,
    );
    final again = cycles(loaded.entries, loaded.resets, FuelType.petrol);
    expect(again, isEmpty);
  });

  test('corrupt journal is not replaced and is not read as zero', () async {
    final directory = await Directory.systemTemp.createTemp('duofuel-bad');
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/fuel_log.json');
    await file.writeAsString('{');
    final log = FileFuelLog(file);
    await expectLater(log.read(), throwsA(isA<FuelLogReadException>()));
    expect(await file.readAsString(), '{');
    final controller = LogController(log);
    await controller.reload();
    expect(controller.readError, fuelLogReadError);
    expect(controller.canWrite, isFalse);
    await controller.saveEntry(
      const FuelEntry(
        id: 'x',
        fuelType: FuelType.petrol,
        occurredAtMs: 1,
        odometerKm: 1,
        quantity: 1,
        unit: 'L',
        isFull: true,
        createdAtMs: 1,
      ),
    );
    expect(await file.readAsString(), '{');
    expect(
      controller.consumption(FuelType.petrol).average.litersPer100,
      isNull,
    );
  });

  test('memory log rename and delete feed the calculator', () async {
    final controller = LogController(MemoryFuelLog());
    await controller.reload();
    await controller.rename('Нива');
    expect(controller.vehicleName, 'Нива');
    await controller.saveEntry(
      const FuelEntry(
        id: 'a',
        fuelType: FuelType.lpg,
        occurredAtMs: 1,
        odometerKm: 0,
        quantity: 40,
        unit: 'L',
        isFull: true,
        createdAtMs: 1,
      ),
    );
    await controller.saveEntry(
      const FuelEntry(
        id: 'b',
        fuelType: FuelType.lpg,
        occurredAtMs: 2,
        odometerKm: 250,
        quantity: 20,
        unit: 'L',
        isFull: true,
        createdAtMs: 2,
      ),
    );
    expect(
      controller.consumption(FuelType.lpg).latest!.ratePer100,
      closeTo(8, 1e-9),
    );
    await controller.deleteEntry('b');
    expect(controller.consumption(FuelType.lpg).latest, isNull);
    expect(controller.consumption(FuelType.lpg).average.litersPer100, isNull);
  });

  test(
    'a receipt total round-trips and a bad total is not read as zero',
    () async {
      final directory = await Directory.systemTemp.createTemp('duofuel-price');
      addTearDown(() => directory.delete(recursive: true));
      final file = File('${directory.path}/fuel_log.json');
      final log = FileFuelLog(file);
      await log.write(
        FuelSnapshot(
          vehicleName: 'Ланос',
          entries: [
            const FuelEntry(
              id: 'paid',
              fuelType: FuelType.petrol,
              occurredAtMs: 1,
              odometerKm: 10,
              quantity: 40,
              unit: 'L',
              isFull: true,
              createdAtMs: 1,
              totalMinor: 220000,
              currency: 'UAH',
            ),
          ],
          resets: const [],
        ),
      );
      final loaded = await log.read();
      expect(loaded.entries.single.totalMinor, 220000);
      expect(loaded.entries.single.currency, 'UAH');

      await file.writeAsString(
        file.readAsStringSync().replaceAll('220000', '-5'),
      );
      await expectLater(log.read(), throwsA(isA<FuelLogReadException>()));
      expect(file.readAsStringSync().contains('-5'), isTrue);
    },
  );
}
