import 'package:duofuel/src/data/file_fuel_log.dart';
import 'package:duofuel/src/data/fuel_log.dart';
import 'package:duofuel/src/data/memory_fuel_log.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/duofuel_app.dart';
import 'package:duofuel/src/ui/log_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('empty consumption is not zero', (tester) async {
    await _pump(tester);
    expect(_text(tester, 'consumption-petrol-latest'), 'Недостаточно данных');
    expect(_text(tester, 'consumption-petrol-average'), 'Недостаточно данных');
    expect(_text(tester, 'consumption-lpg-latest'), 'Недостаточно данных');
    expect(_text(tester, 'consumption-lpg-average'), 'Недостаточно данных');
    expect(_text(tester, 'price-petrol-average'), 'Недостаточно данных');
    expect(_text(tester, 'price-lpg-average'), 'Недостаточно данных');
    expect(find.textContaining('0,0'), findsNothing);
    expect(find.textContaining('0 л/100'), findsNothing);
    expect(find.text('CNG'), findsNothing);
    expect(find.text('Окупаемость'), findsNothing);
    expect(find.text('Сервис'), findsNothing);
    expect(find.textContaining('включая закрывающую'), findsOneWidget);
  });

  testWidgets('a closed petrol cycle shows its rate and the same average', (
    tester,
  ) async {
    await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    await _saveFill(tester, quantity: '10', odometer: '1200', full: false);
    await _saveFill(tester, quantity: '20', odometer: '1300', full: true);

    expect(_text(tester, 'consumption-petrol-latest'), '10,0 л/100 км');
    expect(_text(tester, 'consumption-petrol-average'), '10,0 л/100 км');
    expect(find.text('Фактический · 30 л на 300 км'), findsOneWidget);
    expect(find.text('Фактический · 30 л на 300 км · 1 цикл'), findsOneWidget);
    expect(_text(tester, 'consumption-lpg-latest'), 'Недостаточно данных');
    expect(_text(tester, 'consumption-lpg-average'), 'Недостаточно данных');
    expect(find.textContaining('Бензин: незакрытый цикл'), findsNothing);
    expect(_text(tester, 'price-petrol-average'), 'Недостаточно данных');
  });

  testWidgets('average price is per litre and skips a fill without a sum', (
    tester,
  ) async {
    await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', total: '2200');
    await _saveFill(tester, quantity: '20', odometer: '1100', total: '1100');
    await _saveFill(tester, quantity: '10', odometer: '1200');
    await _saveFill(
      tester,
      quantity: '8',
      odometer: '1300',
      total: '240',
      fuelLpg: true,
    );

    expect(_text(tester, 'price-petrol-average'), '55,00 грн/л');
    expect(find.text('3300,00 грн на 60 л · 2 заправки'), findsOneWidget);
    expect(_text(tester, 'price-lpg-average'), '30,00 грн/л');
    expect(_text(tester, 'consumption-petrol-latest'), 'Недостаточно данных');
  });

  testWidgets('empty quantity and zero quantity name the field', (
    tester,
  ) async {
    await _pump(tester);
    await tester.tap(find.byKey(const Key('fab-fuel')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pump();
    expect(find.text('Количество: нужно заполнить'), findsOneWidget);
    expect(find.text('Одометр, км: нужно заполнить'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('quantity')), '0');
    await tester.enterText(find.byKey(const Key('odometer')), '100');
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pump();
    expect(
      find.text('Количество: значение должно быть больше нуля'),
      findsOneWidget,
    );
    expect(find.text('10,0 л/100 км'), findsNothing);
  });

  testWidgets('a broken journal is an error instead of a zero consumption', (
    tester,
  ) async {
    final log = _BrokenLog();
    final controller = LogController(log);
    await controller.reload();
    await _pump(tester, controller: controller);
    expect(find.byKey(const Key('read-error')), findsOneWidget);
    expect(find.textContaining('0,0'), findsNothing);
    expect(find.byKey(const Key('fab-fuel')), findsNothing);
    expect(log.writes, 0);
  });
}

class _BrokenLog implements FuelLog {
  int writes = 0;

  @override
  Future<FuelSnapshot> read() async {
    throw const FuelLogReadException(fuelLogReadError);
  }

  @override
  Future<void> write(FuelSnapshot snapshot) async {
    writes += 1;
  }
}

Future<void> _pump(WidgetTester tester, {LogController? controller}) async {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final active = controller ?? LogController(MemoryFuelLog());
  if (controller == null) await active.reload();
  await tester.pumpWidget(DuoFuelApp(controller: active));
  await tester.pumpAndSettle(
    const Duration(milliseconds: 50),
    EnginePhase.sendSemanticsUpdate,
    const Duration(seconds: 5),
  );
}

Future<void> _saveFill(
  WidgetTester tester, {
  required String quantity,
  required String odometer,
  bool full = false,
  String? total,
  bool fuelLpg = false,
}) async {
  await tester.tap(find.byKey(const Key('fab-fuel')));
  await tester.pumpAndSettle();
  if (fuelLpg) {
    await tester.tap(find.byTooltip('LPG'));
    await tester.pump();
  }
  await tester.enterText(find.byKey(const Key('quantity')), quantity);
  await tester.enterText(find.byKey(const Key('odometer')), odometer);
  if (total != null) {
    await tester.enterText(find.byKey(const Key('total')), total);
  }
  if (full) {
    await tester.tap(find.byKey(const Key('full-tank')));
    await tester.pump();
  }
  await tester.tap(find.byKey(const Key('save-fuel')));
  await tester.pumpAndSettle();
}

String _text(WidgetTester tester, String key) {
  return tester.widget<Text>(find.byKey(Key(key))).data!;
}
