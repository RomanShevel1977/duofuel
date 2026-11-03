import 'dart:io';

import 'package:duofuel/src/data/file_fuel_log.dart';
import 'package:duofuel/src/data/fuel_log.dart';
import 'package:duofuel/src/data/memory_fuel_log.dart';
import 'package:duofuel/src/model/records.dart';
import 'package:duofuel/src/ui/duofuel_app.dart';
import 'package:duofuel/src/ui/fuel_form_page.dart';
import 'package:duofuel/src/ui/log_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('empty consumption is not zero', (tester) async {
    await _pump(tester);
    expect(find.text('Бензин · нет заправок'), findsOneWidget);
    expect(find.text('Газ · нет заправок'), findsOneWidget);
    expect(find.text('Недостаточно данных'), findsNothing);
    expect(find.textContaining('0,0'), findsNothing);
    expect(find.textContaining('0 л/100'), findsNothing);
    expect(find.text('CNG'), findsNothing);
    expect(find.text('Окупаемость'), findsNothing);
    expect(find.text('Сервис'), findsNothing);
    expect(find.textContaining('включая закрывающую'), findsNothing);

    await tester.tap(find.byKey(const Key('fuel-folded-petrol')));
    await tester.pumpAndSettle();
    expect(_text(tester, 'consumption-petrol-latest'), 'Недостаточно данных');
    expect(_text(tester, 'consumption-petrol-average'), 'Недостаточно данных');
    expect(_text(tester, 'price-petrol-average'), 'Недостаточно данных');
    expect(find.text('Нужны две полные заправки бензина.'), findsNWidgets(2));
    expect(
      find.text(
        'Укажите сумму чека. Средняя цена — сумма чеков, делённая на литры.',
      ),
      findsOneWidget,
    );
    expect(
      find.textContaining('две полные заправки бензина. Цена'),
      findsNothing,
    );

    await tester.tap(find.text('Как считается'));
    await tester.pumpAndSettle();
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
    expect(find.text('Газ · нет заправок'), findsOneWidget);
    expect(find.textContaining('Бензин: незакрытый цикл'), findsNothing);
    expect(_text(tester, 'price-petrol-average'), 'Недостаточно данных');
    expect(
      find.textContaining('Нужны две полные заправки бензина'),
      findsNothing,
    );
    expect(find.text('Нужны две полные заправки газа.'), findsNothing);
    expect(
      find.text(
        'Укажите сумму чека. Средняя цена — сумма чеков, делённая на литры.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining(' · 10,0 л/100 км · '), findsNothing);
    await tester.tap(find.text('Закрытые циклы · 1'));
    await tester.pumpAndSettle();
    expect(find.textContaining(' · 10,0 л/100 км · '), findsOneWidget);
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

  testWidgets('a journal row opens the same fill and keeps its id', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    await _saveFill(tester, quantity: '10', odometer: '1200');
    await _saveFill(tester, quantity: '20', odometer: '1300', full: true);
    final edited = controller.entries.singleWhere(
      (entry) => entry.quantity == 10,
    );

    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Удалить'), findsNWidgets(3));
    final row = find.ancestor(
      of: find.text('10 л'),
      matching: find.byType(Card),
    );
    await tester.tap(
      find.descendant(of: row, matching: find.byTooltip('Удалить')),
    );
    await tester.pumpAndSettle();
    expect(find.text('Удалить запись?'), findsOneWidget);
    await tester.tap(find.text('Отмена'));
    await tester.pumpAndSettle();
    expect(controller.entries, hasLength(3));

    await tester.tap(find.text('10 л'));
    await tester.pumpAndSettle();
    expect(find.text('Изменить заправку'), findsOneWidget);
    expect(_field(tester, 'quantity'), '10,0');
    expect(_field(tester, 'odometer'), '1200');
    expect(
      find.text(
        'Прошлое показание автомобиля: 1000 км. Общая шкала, не последняя заправка этого топлива.',
      ),
      findsOneWidget,
    );
    await tester.enterText(find.byKey(const Key('quantity')), '20');
    await tester.enterText(find.byKey(const Key('total')), '800');
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pumpAndSettle();

    expect(controller.entries, hasLength(3));
    final saved = controller.entries.singleWhere(
      (entry) => entry.id == edited.id,
    );
    expect(saved.quantity, 20);
    expect(saved.totalMinor, 80000);
    expect(saved.createdAtMs, edited.createdAtMs);
    expect(saved.odometerKm, 1200);
    expect(saved.isFull, isFalse);
    await tester.tap(find.text('Сводка'));
    await tester.pumpAndSettle();
    expect(_text(tester, 'consumption-petrol-latest'), '13,3 л/100 км');
    expect(_text(tester, 'consumption-petrol-average'), '13,3 л/100 км');
  });

  testWidgets('previous odometer is a hint and a lower reading does not save', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    await tester.tap(find.byKey(const Key('fab-fuel')));
    await tester.pumpAndSettle();
    expect(_field(tester, 'odometer'), '');
    expect(
      find.text(
        'Прошлое показание автомобиля: 1000 км. Общая шкала, не последняя заправка этого топлива.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('0,00'), findsNothing);
    await tester.enterText(find.byKey(const Key('quantity')), '10');
    await tester.tap(find.byKey(const Key('not-full')));
    await tester.pump();
    await tester.enterText(find.byKey(const Key('odometer')), '900');
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pump();
    expect(
      find.text(
        'Одометр, км: показание меньше предыдущего. Без замены панели так сохранить нельзя',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('save-fuel')), findsOneWidget);
    expect(_field(tester, 'odometer'), '900');
    expect(controller.entries, hasLength(1));
  });

  testWidgets('the form shows the price per litre and does not store it', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await tester.tap(find.byKey(const Key('fab-fuel')));
    await tester.pumpAndSettle();
    expect(find.text('Цена за литр\nиз суммы и литров'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('quantity')), '40');
    await tester.enterText(find.byKey(const Key('total')), '2200');
    await tester.pump();
    expect(find.text('Цена за литр\n55,00 грн/л'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('odometer')), '1000');
    await tester.tap(find.byKey(const Key('not-full')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pumpAndSettle();
    final saved = controller.entries.single;
    expect(saved.totalMinor, 220000);
    expect(saved.currency, 'UAH');
    expect(_text(tester, 'price-petrol-average'), '55,00 грн/л');
  });

  testWidgets('an edit can change fuel, date, odometer and full tank', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await _saveFill(tester, quantity: '15', odometer: '1000', total: '300');
    final original = controller.entries.single;

    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('15 л'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('LPG'));
    await tester.pump();
    await tester.enterText(find.byKey(const Key('quantity')), '16');
    await tester.enterText(find.byKey(const Key('odometer')), '1050');
    await tester.enterText(find.byKey(const Key('total')), '320');
    await tester.tap(find.byKey(const Key('full-tank')));
    await tester.pump();
    final targetDay =
        DateTime.fromMillisecondsSinceEpoch(original.occurredAtMs).day == 15
        ? 14
        : 15;
    await tester.tap(find.byIcon(Icons.calendar_today_outlined));
    await tester.pumpAndSettle();
    await tester.tap(find.text('$targetDay').last);
    await tester.tap(find.text('ОК'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pumpAndSettle();

    expect(controller.entries, hasLength(1));
    final saved = controller.entries.singleWhere(
      (entry) => entry.id == original.id,
    );
    expect(saved.fuelType, FuelType.lpg);
    expect(saved.quantity, 16);
    expect(saved.odometerKm, 1050);
    expect(saved.totalMinor, 32000);
    expect(saved.isFull, isTrue);
    expect(saved.createdAtMs, original.createdAtMs);
    expect(
      DateTime.fromMillisecondsSinceEpoch(saved.occurredAtMs).day,
      targetDay,
    );
    await tester.tap(find.text('Сводка'));
    await tester.pumpAndSettle();
    expect(_text(tester, 'consumption-lpg-latest'), 'Недостаточно данных');
    expect(find.text('Нужны две полные заправки газа.'), findsWidgets);
  });

  testWidgets('a new fill asks for a full tank before it is saved', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await tester.tap(find.byKey(const Key('fab-fuel')));
    await tester.pumpAndSettle();
    expect(find.text(fullTankPrompt), findsOneWidget);
    expect(find.text(fullTankChosen), findsNothing);
    await tester.enterText(find.byKey(const Key('quantity')), '40');
    await tester.enterText(find.byKey(const Key('odometer')), '1000');
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pump();
    expect(controller.entries, isEmpty);
    expect(find.byKey(const Key('full-tank-hint')), findsOneWidget);
    await tester.tap(find.byKey(const Key('full-tank')));
    await tester.pump();
    expect(find.text(fullTankChosen), findsOneWidget);
    await tester.tap(find.byKey(const Key('save-fuel')));
    await tester.pumpAndSettle();
    expect(controller.entries.single.isFull, isTrue);
  });

  testWidgets('panel replacement sits by the odometer, not by the theme', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await tester.tap(find.byTooltip('Ещё'));
    await tester.pumpAndSettle();
    expect(find.text('Замена панели'), findsNothing);
    expect(find.text('Тёмная'), findsOneWidget);
    await tester.tap(find.text('Тёмная'));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('fab-fuel')));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Если меняли панель, новое показание вводится здесь, а не в заправке.',
      ),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const Key('open-reset')));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Новое показание после замены панели'),
      findsOneWidget,
    );
    await tester.enterText(find.byKey(const Key('reset-odometer')), '50');
    await tester.tap(find.byKey(const Key('save-reset')));
    await tester.pumpAndSettle();
    expect(find.text('Заправка'), findsWidgets);
    expect(controller.resets, hasLength(1));
    expect(controller.resets.single.odometerKm, 50);
    expect(controller.entries, isEmpty);
  });

  testWidgets('an open cycle asks for another full fill of that fuel', (
    tester,
  ) async {
    await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    await _saveFill(tester, quantity: '10', odometer: '1100');
    expect(
      find.text('Бензин: нужна ещё одна полная заправка бензина.'),
      findsOneWidget,
    );
    expect(find.textContaining('незакрытый цикл'), findsNothing);
    expect(_text(tester, 'consumption-petrol-latest'), 'Недостаточно данных');
    expect(find.text('Последний цикл без числа'), findsNothing);
    expect(find.text('Газ · нет заправок'), findsOneWidget);
    expect(
      find.text(
        'Укажите сумму чека. Средняя цена — сумма чеков, делённая на литры.',
      ),
      findsOneWidget,
    );
  });

  testWidgets(
    'a closed cycle without a rate is not a zero beside the average',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final log = MemoryFuelLog();
      await log.write(
        FuelSnapshot(
          vehicleName: 'Автомобиль',
          entries: const [
            FuelEntry(
              id: 'open',
              fuelType: FuelType.petrol,
              occurredAtMs: 1,
              odometerKm: 1000,
              quantity: 40,
              unit: 'L',
              isFull: true,
              createdAtMs: 1,
            ),
            FuelEntry(
              id: 'close',
              fuelType: FuelType.petrol,
              occurredAtMs: 3,
              odometerKm: 1200,
              quantity: 20,
              unit: 'L',
              isFull: true,
              createdAtMs: 3,
            ),
            FuelEntry(
              id: 'next',
              fuelType: FuelType.petrol,
              occurredAtMs: 4,
              odometerKm: 1200,
              quantity: 10,
              unit: 'L',
              isFull: true,
              createdAtMs: 4,
            ),
            FuelEntry(
              id: 'after',
              fuelType: FuelType.petrol,
              occurredAtMs: 6,
              odometerKm: 1500,
              quantity: 15,
              unit: 'L',
              isFull: true,
              createdAtMs: 6,
            ),
          ],
          resets: const [
            OdometerReset(
              id: 'panel',
              occurredAtMs: 5,
              odometerKm: 0,
              createdAtMs: 5,
            ),
          ],
        ),
      );
      final controller = LogController(log);
      await controller.reload();
      await _pump(tester, controller: controller);
      expect(_text(tester, 'consumption-petrol-latest'), 'Недостаточно данных');
      expect(_text(tester, 'consumption-petrol-average'), '10,0 л/100 км');
      expect(find.text('Последний цикл без числа'), findsOneWidget);
      expect(find.textContaining('Не ноль:'), findsWidgets);
      expect(find.text('0,0 л/100 км'), findsNothing);
      expect(
        find.bySemanticsLabel(RegExp('разрыв шкалы, расход не посчитан')),
        findsOneWidget,
      );
      semantics.dispose();
    },
  );

  testWidgets(
    'a corrupt journal can be shared and emptied only after confirm',
    (tester) async {
      final log = _BrokenLog();
      String? shared;
      final controller = LogController(log);
      await controller.reload();
      await _pump(
        tester,
        controller: controller,
        onShareText: (text) async => shared = text,
      );
      expect(find.text('Отправить файл'), findsOneWidget);
      expect(find.text('Начать пустой журнал'), findsOneWidget);
      await tester.tap(find.byKey(const Key('share-broken-journal')));
      await tester.pumpAndSettle();
      expect(shared, log.raw);
      expect(log.emptied, isFalse);

      await tester.tap(find.byKey(const Key('start-empty-journal')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('confirm-empty-journal')), findsOneWidget);
      await tester.tap(find.text('Отмена'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('read-error')), findsOneWidget);
      expect(log.emptied, isFalse);

      await tester.tap(find.byKey(const Key('start-empty-journal')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('confirm-empty-journal')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(log.emptied, isTrue);
      expect(log.keptName, 'fuel_log.json.broken-1');
      expect(find.textContaining('fuel_log.json.broken-1'), findsOneWidget);
      expect(find.byKey(const Key('read-error')), findsNothing);
      expect(find.byKey(const Key('fab-fuel')), findsOneWidget);
    },
  );

  testWidgets(
    'a failed save, reset, or rename says the journal was not written',
    (tester) async {
      final controller = LogController(_FailingWriteLog());
      await controller.reload();
      await _pump(tester, controller: controller);

      await tester.tap(find.byKey(const Key('fab-fuel')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('quantity')), '40');
      await tester.enterText(find.byKey(const Key('odometer')), '1000');
      await tester.tap(find.byKey(const Key('full-tank')));
      await tester.pump();
      await tester.tap(find.byKey(const Key('save-fuel')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text(journalWriteError), findsOneWidget);
      expect(find.byKey(const Key('save-fuel')), findsOneWidget);
      expect(controller.entries, isEmpty);

      await tester.tap(find.byKey(const Key('open-reset')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('reset-odometer')), '50');
      await tester.tap(find.byKey(const Key('save-reset')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text(journalWriteError), findsWidgets);
      expect(find.byKey(const Key('save-reset')), findsOneWidget);
      expect(controller.resets, isEmpty);

      await tester.tap(find.byTooltip('Назад'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Назад'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('rename-vehicle')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('vehicle-name')), 'Нива');
      await tester.tap(find.text('Сохранить'));
      await tester.pump();
      expect(find.text('Имя автомобиля'), findsOneWidget);
      expect(find.text(journalWriteError), findsWidgets);
      expect(controller.vehicleName, 'Автомобиль');
    },
  );

  testWidgets('the card shows kilometres since the last fill of that fuel', (
    tester,
  ) async {
    await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    await _saveFill(
      tester,
      quantity: '8',
      odometer: '1400',
      full: true,
      fuelLpg: true,
    );
    expect(find.textContaining('1000 км · с тех пор 400 км'), findsOneWidget);
    final gas = tester
        .widget<Text>(find.byKey(const Key('since-last-lpg')))
        .data!;
    expect(gas.contains('1400 км'), isTrue);
    expect(gas.contains('с тех пор'), isFalse);
  });

  testWidgets('a journal row shows the litre price and the fill can return', (
    tester,
  ) async {
    final controller = await _pump(tester);
    await _saveFill(tester, quantity: '40', odometer: '1000', total: '2200');
    final id = controller.entries.single.id;
    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    expect(find.text('2200,00 грн · 55,00 грн/л'), findsOneWidget);
    await tester.tap(find.byTooltip('Удалить'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Удалить'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.entries, isEmpty);
    expect(find.text('Заправка удалена'), findsOneWidget);
    await tester.tap(find.byKey(const Key('undo-delete')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.entries.single.id, id);
    expect(controller.entries.single.totalMinor, 220000);
  });

  testWidgets('a journal copy can be read back and replaces the current log', (
    tester,
  ) async {
    String? shared;
    final copy = snapshotText(
      FuelSnapshot(
        vehicleName: 'Нива',
        entries: const [
          FuelEntry(
            id: 'copy-1',
            fuelType: FuelType.petrol,
            occurredAtMs: 1,
            odometerKm: 1000,
            quantity: 40,
            unit: 'L',
            isFull: true,
            createdAtMs: 1,
          ),
        ],
        resets: const [
          OdometerReset(
            id: 'panel-copy',
            occurredAtMs: 4,
            odometerKm: 12,
            createdAtMs: 4,
            linkedFuelEntryId: 'copy-1',
          ),
        ],
      ),
    );
    var pick = copy;
    final controller = await _pump(
      tester,
      onShareText: (text) async => shared = text,
      pickSnapshot: () async => pick,
    );
    await _saveFill(tester, quantity: '40', odometer: '1000', full: true);
    tester
        .state<ScaffoldMessengerState>(find.byType(ScaffoldMessenger))
        .hideCurrentSnackBar();
    await tester.pumpAndSettle();
    final oldId = controller.entries.single.id;
    await tester.tap(find.text('Журнал'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('journal-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('journal-snapshot')));
    await tester.pumpAndSettle();
    expect(decodeSnapshot(shared!).entries.single.id, oldId);

    await tester.tap(find.byKey(const Key('journal-import')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Отмена'));
    await tester.pumpAndSettle();
    expect(controller.entries.single.id, oldId);

    await tester.tap(find.byKey(const Key('journal-import')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Заменить'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(controller.vehicleName, 'Нива');
    expect(controller.entries.single.id, 'copy-1');
    expect(controller.resets.single.linkedFuelEntryId, 'copy-1');
    expect(find.text('Журнал восстановлен из копии.'), findsOneWidget);
    tester
        .state<ScaffoldMessengerState>(find.byType(ScaffoldMessenger))
        .hideCurrentSnackBar();
    await tester.pumpAndSettle();

    pick = '{нет';
    await tester.tap(find.byKey(const Key('journal-import')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Заменить'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Это не копия журнала DuoFuel.'), findsOneWidget);
    expect(controller.entries.single.id, 'copy-1');
  });
}

class _BrokenLog implements FuelLog {
  int writes = 0;
  bool emptied = false;
  String? keptName;
  final raw = '{не журнал';

  @override
  Future<FuelSnapshot> read() async {
    if (emptied) return FuelSnapshot.initial();
    throw const FuelLogReadException(fuelLogReadError);
  }

  @override
  Future<void> write(FuelSnapshot snapshot) async {
    writes += 1;
  }

  @override
  Future<String?> rawJournal() async => emptied ? null : raw;

  @override
  Future<String?> keepBrokenAndStartEmpty() async {
    keptName = 'fuel_log.json.broken-1';
    emptied = true;
    return keptName;
  }
}

class _FailingWriteLog implements FuelLog {
  FuelSnapshot snapshot = FuelSnapshot.initial();

  @override
  Future<FuelSnapshot> read() async => snapshot;

  @override
  Future<void> write(FuelSnapshot snapshot) async {
    throw const FileSystemException('disk');
  }

  @override
  Future<String?> rawJournal() async => null;

  @override
  Future<String?> keepBrokenAndStartEmpty() async => null;
}

Future<LogController> _pump(
  WidgetTester tester, {
  LogController? controller,
  Future<void> Function(String text)? onShareText,
  Future<String?> Function()? pickSnapshot,
}) async {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final active = controller ?? LogController(MemoryFuelLog());
  if (controller == null) await active.reload();
  await tester.pumpWidget(
    DuoFuelApp(
      controller: active,
      onShareText: onShareText,
      pickSnapshot: pickSnapshot,
    ),
  );
  await tester.pumpAndSettle(
    const Duration(milliseconds: 50),
    EnginePhase.sendSemanticsUpdate,
    const Duration(seconds: 5),
  );
  return active;
}

String _field(WidgetTester tester, String key) {
  return tester.widget<TextField>(find.byKey(Key(key))).controller!.text;
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

String _text(WidgetTester tester, String key) {
  return tester.widget<Text>(find.byKey(Key(key))).data!;
}
