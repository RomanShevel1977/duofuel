import 'package:duofuel/src/calc/fuel_cycle.dart';
import 'package:duofuel/src/data/memory_fuel_log.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/cycle_chart.dart';
import 'package:duofuel/src/ui/duofuel_app.dart';
import 'package:duofuel/src/ui/journal_csv.dart';
import 'package:duofuel/src/ui/journal_filter.dart';
import 'package:duofuel/src/ui/log_controller.dart';
import 'package:duofuel/src/ui/share_journal.dart';
import 'package:duofuel/src/ui/theme_choice.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'csv lists fills and leaves the receipt empty when it was not entered',
    () {
      final csv = journalCsv(
        [
          _fill('b', FuelType.lpg, 2, 1100, 8, false, total: 24000),
          _fill('a', FuelType.petrol, 1, 1000, 40.5, true, total: 220000),
          _fill('c', FuelType.petrol, 3, 1200, 10, false),
        ],
        resets: const [
          OdometerReset(
            id: 'panel',
            occurredAtMs: 4,
            odometerKm: 50,
            createdAtMs: 4,
          ),
        ],
      );
      expect(csv.startsWith('\uFEFF$journalCsvHeader\n'), isTrue);
      expect(csv.contains(journalCsvFilteredLine), isFalse);
      final lines = csv.replaceFirst('\uFEFF', '').trim().split('\n');
      expect(lines, hasLength(5));
      expect(lines[1], contains('Заправка;Бензин;40,5;2200,00;1000;да'));
      expect(lines[2], contains('Заправка;Газ;8;240,00;1100;нет'));
      expect(lines[3], contains('Заправка;Бензин;10;;1200;нет'));
      expect(lines[4], contains('Замена панели;;;;50;'));
      expect(csv.contains('л/100'), isFalse);
      expect(csv.contains('грн/л'), isFalse);
      final filtered = journalCsv([
        _fill('a', FuelType.petrol, 1, 1000, 40, true),
      ], filtered: true);
      expect(filtered.startsWith('\uFEFF$journalCsvFilteredLine\n'), isTrue);
      expect(filtered.contains(journalCsvHeader), isTrue);
      expect(
        journalExportFileName(DateTime(2026, 11, 1, 20, 45, 12, 3)),
        'duofuel-zhurnal-20261101-204512003.csv',
      );
      expect(
        journalExportFileName(DateTime(2026, 11, 1, 20, 45, 12, 3))
            .contains('duofuel-zhurnal.csv'),
        isFalse,
      );
    },
  );

  test('journal filter keeps fuel, text and day apart', () {
    final petrol = _fill('a', FuelType.petrol, 1, 1000, 40, true);
    final lpg = _fill('b', FuelType.lpg, 2, 1300, 7, false);
    final day = DateTime.fromMillisecondsSinceEpoch(1);
    const petrolOnly = JournalFilter(fuel: FuelType.petrol);
    expect(petrolOnly.allowsEntry(petrol), isTrue);
    expect(petrolOnly.allowsEntry(lpg), isFalse);
    expect(
      petrolOnly.allowsReset(
        const OdometerReset(
          id: 'r',
          occurredAtMs: 1,
          odometerKm: 5,
          createdAtMs: 1,
        ),
      ),
      isFalse,
    );
    final byText = JournalFilter(text: '1300');
    expect(byText.allowsEntry(petrol), isFalse);
    expect(byText.allowsEntry(lpg), isTrue);
    final byDay = JournalFilter(day: DateTime(day.year, day.month, day.day));
    expect(byDay.allowsEntry(petrol), isTrue);
    expect(
      byDay.allowsEntry(
        _fill(
          'later',
          FuelType.petrol,
          day.millisecondsSinceEpoch + 86400000,
          1400,
          5,
          true,
        ),
      ),
      isFalse,
    );
  });

  test('the chart uses closed-cycle rates and skips a cycle without one', () {
    final entries = [
      _fill('open', FuelType.petrol, 1, 1000, 40, true),
      _fill('mid', FuelType.petrol, 2, 1200, 10, false),
      _fill('close', FuelType.petrol, 3, 1300, 20, true),
      _fill('lpg-open', FuelType.lpg, 4, 1300, 8, true),
      _fill('lpg-close', FuelType.lpg, 5, 1500, 4, true),
      _fill('flat-open', FuelType.petrol, 6, 1600, 10, true),
      _fill('flat-close', FuelType.petrol, 7, 1600, 10, true),
    ];
    final points = cycleChartPoints(entries, const []);
    final closed = [
      for (final fuel in FuelType.values) ...cycles(entries, const [], fuel),
    ];
    final factual = [
      for (final cycle in closed)
        if (cycle.ratePer100 != null) cycle,
    ];
    expect(closed.any((cycle) => cycle.ratePer100 == null), isTrue);
    expect(points, hasLength(factual.length));
    for (final point in points) {
      expect(
        factual.any(
          (cycle) =>
              cycle.fuel == point.fuel &&
              cycle.endMs == point.endMs &&
              cycle.ratePer100 == point.ratePer100,
        ),
        isTrue,
      );
    }
    expect(points.first.ratePer100, closeTo(10, 1e-9));
    expect(cycleChartPoints(const [], const []), isEmpty);
    final scale = chartYMax(const [10, 10.5]);
    expect(scale, greaterThan(10.5));
    expect((10.5 - 10) / scale, lessThan(0.2));
    expect(axisLabelsThatFit(const [0, 10, 40], const [30, 30, 30], gap: 8), [
      0,
      2,
    ]);
    expect(cycleChartSemantics(points), contains('л/100 км'));
    expect(
      cycleChartSemantics(points),
      contains(formatChartDate(points.first.endMs)),
    );
  });

  test('theme choice parses the three modes', () {
    expect(parseThemeChoice('light'), ThemeChoice.light);
    expect(parseThemeChoice('dark'), ThemeChoice.dark);
    expect(parseThemeChoice('system'), ThemeChoice.system);
    expect(parseThemeChoice(''), ThemeChoice.system);
    expect(themeModeOf(ThemeChoice.dark), ThemeMode.dark);
    expect(themeModeOf(ThemeChoice.system), ThemeMode.system);
  });

  testWidgets('journal search and fuel filter hide the other rows', (
    tester,
  ) async {
    await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000');
    await _saveFill(tester, quantity: '7', odometer: '1300', fuelLpg: true);
    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    expect(find.text('Все дни'), findsOneWidget);
    await tester.tap(find.byTooltip('Только газ'));
    await tester.pump();
    expect(find.text('7 л'), findsOneWidget);
    expect(find.text('40 л'), findsNothing);
    await tester.tap(find.text('Все'));
    await tester.pump();
    await tester.enterText(find.byKey(const Key('journal-search')), '40');
    await tester.pump();
    expect(find.text('40 л'), findsOneWidget);
    expect(find.text('7 л'), findsNothing);
    expect(find.text('Ничего не найдено'), findsNothing);
  });

  testWidgets('export sends the journal csv and the chart shows both series', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    String? exported;
    await _pump(tester, onExportCsv: (csv) async => exported = csv);
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    await _saveFill(tester, quantity: '10', odometer: '1200');
    await _saveFill(
      tester,
      quantity: '20',
      odometer: '1300',
      full: true,
      total: '1100',
    );
    await _saveFill(
      tester,
      quantity: '8',
      odometer: '1400',
      full: true,
      fuelLpg: true,
    );
    await _saveFill(
      tester,
      quantity: '4',
      odometer: '1600',
      full: true,
      fuelLpg: true,
      total: '120',
    );

    expect(find.byKey(const Key('cycle-chart')), findsOneWidget);
    expect(find.byKey(const Key('cycle-series-petrol')), findsOneWidget);
    expect(find.byKey(const Key('cycle-series-lpg')), findsOneWidget);
    expect(find.byKey(const Key('cycle-axis-date')), findsOneWidget);
    expect(
      find.bySemanticsLabel(
        RegExp(
          r'График расхода закрытых циклов.*л/100 км.*\d{2}\.\d{2}\.\d{4}',
        ),
      ),
      findsOneWidget,
    );
    expect(_text(tester, 'consumption-petrol-latest'), '10,0 л/100 км');
    expect(_text(tester, 'price-lpg-average'), '30,00 грн/л');

    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('journal-export')));
    await tester.pump();
    expect(exported, isNotNull);
    expect(exported!, contains(journalCsvHeader));
    expect(exported!, contains('Бензин;20;1100,00;1300;да'));
    expect(exported!, contains('Газ;4;120,00;1600;да'));
    expect(exported!.contains('л/100'), isFalse);
    expect(exported!.contains(journalCsvFilteredLine), isFalse);
    expect(exported!, contains('Заправка;Бензин;20;1100,00;1300;да'));

    await tester.tap(find.byTooltip('Только газ'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('journal-export')));
    await tester.pump();
    expect(exported!, contains(journalCsvFilteredLine));
    expect(exported!, contains('Заправка;Газ;4;120,00;1600;да'));
    expect(exported!.contains('Бензин'), isFalse);
    semantics.dispose();
  });

  testWidgets('fuel filter stays after opening the summary', (tester) async {
    await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000');
    await _saveFill(tester, quantity: '7', odometer: '1300', fuelLpg: true);
    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Только газ'));
    await tester.pump();
    await tester.tap(find.text('Сводка'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    expect(find.text('7 л'), findsOneWidget);
    expect(find.text('40 л'), findsNothing);
  });

  testWidgets('theme follows light, dark and system', (tester) async {
    await _pump(tester);
    expect(_themeMode(tester), ThemeMode.system);
    await tester.tap(find.byTooltip('Ещё'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Тёмная'));
    await tester.pumpAndSettle();
    expect(_themeMode(tester), ThemeMode.dark);
    expect(
      Theme.of(tester.element(find.text('DuoFuel'))).brightness,
      Brightness.dark,
    );
    await tester.tap(find.byTooltip('Ещё'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Светлая'));
    await tester.pumpAndSettle();
    expect(_themeMode(tester), ThemeMode.light);
    expect(
      Theme.of(tester.element(find.text('DuoFuel'))).brightness,
      Brightness.light,
    );
    await tester.tap(find.byTooltip('Ещё'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Как в системе'));
    await tester.pumpAndSettle();
    expect(_themeMode(tester), ThemeMode.system);
  });
}

ThemeMode _themeMode(WidgetTester tester) {
  return tester.widget<MaterialApp>(find.byType(MaterialApp)).themeMode!;
}

String _text(WidgetTester tester, String key) {
  return tester.widget<Text>(find.byKey(Key(key))).data!;
}

Future<void> _pump(
  WidgetTester tester, {
  Future<void> Function(String csv)? onExportCsv,
}) async {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final controller = LogController(MemoryFuelLog());
  await controller.reload();
  await tester.pumpWidget(
    DuoFuelApp(controller: controller, onExportCsv: onExportCsv),
  );
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
  await tester.tap(find.byKey(Key(full ? 'full-tank' : 'not-full')));
  await tester.pump();
  await tester.tap(find.byKey(const Key('save-fuel')));
  await tester.pumpAndSettle();
}

FuelEntry _fill(
  String id,
  FuelType fuel,
  int at,
  int odometer,
  double quantity,
  bool full, {
  int? total,
}) {
  return FuelEntry(
    id: id,
    fuelType: fuel,
    occurredAtMs: at,
    odometerKm: odometer,
    quantity: quantity,
    unit: 'L',
    isFull: full,
    createdAtMs: at,
    totalMinor: total,
    currency: total == null ? null : 'UAH',
  );
}
