import 'package:flutter/material.dart';

import '../calc/odometer_order.dart';
import '../calc/price_average.dart';
import '../calc/validation.dart';
import '../model/ids.dart';
import '../model/records.dart';
import 'format.dart';
import 'log_controller.dart';
import 'reset_form_page.dart';
import 'when_buttons.dart';

const fullTankPrompt =
    'Отметьте полный бак до сохранения. Без него расход останется «Недостаточно данных».';
const fullTankChosen = 'Полный бак. Эта заправка может закрыть цикл.';
const partialTankChosen = 'Не полный бак. Эта заправка не закроет цикл.';

void openFuel(
  BuildContext context,
  LogController controller, {
  FuelEntry? existing,
}) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (context) =>
          FuelFormPage(controller: controller, existing: existing),
    ),
  );
}

class FuelFormPage extends StatefulWidget {
  const FuelFormPage({super.key, required this.controller, this.existing});

  final LogController controller;
  final FuelEntry? existing;

  @override
  State<FuelFormPage> createState() => _FuelFormPageState();
}

class _FuelFormPageState extends State<FuelFormPage> {
  late final String _entryId = widget.existing?.id ?? newId();
  late final int _createdAtMs =
      widget.existing?.createdAtMs ?? DateTime.now().millisecondsSinceEpoch;
  late FuelType _fuel =
      widget.existing?.fuelType ?? lastFuelType(widget.controller.entries);
  late bool? _full = widget.existing?.isFull;
  bool _missingFull = false;
  late DateTime _date = _initialDate();
  late TimeOfDay _time = TimeOfDay.fromDateTime(_date);
  late final TextEditingController _quantity = TextEditingController(
    text: widget.existing == null
        ? ''
        : formatQuantityInput(widget.existing!.quantity),
  );
  late final TextEditingController _odometer = TextEditingController(
    text: widget.existing == null ? '' : widget.existing!.odometerKm.toString(),
  );
  late final TextEditingController _total = TextEditingController(
    text: widget.existing?.totalMinor == null
        ? ''
        : formatTotalInput(widget.existing!.totalMinor!),
  );
  List<FieldIssue> _issues = const [];

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_refreshDraft);
    _quantity.addListener(_refreshDraft);
    _total.addListener(_refreshDraft);
    _odometer.addListener(_refreshDraft);
  }

  void _refreshDraft() {
    if (mounted) setState(() {});
  }

  DateTime _initialDate() {
    final existing = widget.existing;
    if (existing == null) return DateTime.now();
    return DateTime.fromMillisecondsSinceEpoch(existing.occurredAtMs);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_refreshDraft);
    _quantity.removeListener(_refreshDraft);
    _total.removeListener(_refreshDraft);
    _odometer.removeListener(_refreshDraft);
    _quantity.dispose();
    _odometer.dispose();
    _total.dispose();
    super.dispose();
  }

  DateTime get _when =>
      DateTime(_date.year, _date.month, _date.day, _time.hour, _time.minute);

  String? _issueFor(String field) {
    for (final issue in _issues) {
      if (issue.field == field) return issue.line;
    }
    return null;
  }

  String _totalHelp() {
    final priced = [
      for (final entry in widget.controller.entries)
        if (entry.fuelType == _fuel &&
            entry.id != widget.existing?.id &&
            entry.totalMinor != null)
          entry,
    ];
    if (priced.isEmpty) return 'Необязательно. Сумма чека в гривнах.';
    priced.sort((a, b) => b.sortKey.compareTo(a.sortKey));
    final last = priced.first;
    final text = priceValue(averagePrice([last], _fuel));
    if (text == 'Недостаточно данных') {
      return 'Необязательно. Сумма чека в гривнах.';
    }
    return 'Прошлая цена: $text. Сумму можно не указывать.';
  }

  String _odometerHelp() {
    final previous = previousOdometerHint(
      entries: widget.controller.entries,
      resets: widget.controller.resets,
      occurredAtMs: _when.millisecondsSinceEpoch,
      createdAtMs: _createdAtMs,
      exceptEntryId: _entryId,
      savedOdometerKm: widget.existing?.odometerKm,
    );
    if (previous == null) return 'Целые километры с панели';
    return 'Прошлое показание: $previous км';
  }

  String _enteredPriceText() {
    final price = formatEnteredUnitPrice(
      parseTotalMinor(_total.text),
      parseQuantity(_quantity.text),
    );
    if (price == null) return 'Цена за литр\nиз суммы и литров';
    return 'Цена за литр\n$price';
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(1990),
      lastDate: DateTime(2100),
    );
    if (picked == null) return;
    setState(() => _date = picked);
  }

  Future<void> _pickTime() async {
    final picked = await showTimePicker(
      context: context,
      initialTime: _time,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
        child: child ?? const SizedBox.shrink(),
      ),
    );
    if (picked == null) return;
    setState(() => _time = picked);
  }

  Future<void> _save() async {
    final issues = [
      ...validateQuantity(_quantity.text),
      ...validateOdometer(_odometer.text),
      ...validateTotal(_total.text),
    ];
    if (issues.isEmpty) {
      issues.addAll(
        validateOdometerNotLower(
          odometerKm: parseOdometer(_odometer.text)!,
          occurredAtMs: _when.millisecondsSinceEpoch,
          createdAtMs: _createdAtMs,
          id: _entryId,
          entries: widget.controller.entries,
          resets: widget.controller.resets,
          savedOdometerKm: widget.existing?.odometerKm,
        ),
      );
    }
    final full = _full;
    setState(() {
      _issues = issues;
      _missingFull = full == null;
    });
    if (full == null || issues.isNotEmpty) return;
    final existing = widget.existing;
    final totalMinor = parseTotalMinor(_total.text);
    final entry = FuelEntry(
      id: _entryId,
      fuelType: _fuel,
      occurredAtMs: _when.millisecondsSinceEpoch,
      odometerKm: parseOdometer(_odometer.text)!,
      quantity: parseQuantity(_quantity.text)!,
      unit: existing?.unit ?? 'L',
      isFull: full,
      createdAtMs: _createdAtMs,
      totalMinor: totalMinor,
      currency: totalMinor == null ? null : uahCurrency,
    );
    final messenger = ScaffoldMessenger.of(context);
    await widget.controller.saveEntry(entry);
    if (!mounted) return;
    Navigator.pop(context);
    messenger.showSnackBar(const SnackBar(content: Text('Заправка сохранена')));
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.existing == null ? 'Заправка' : 'Изменить заправку'),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Полный бак?', style: text.titleSmall),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: _TankChoice(
                      key: const Key('full-tank'),
                      selected: _full == true,
                      label: 'Полный бак',
                      onPressed: () => setState(() {
                        _full = true;
                        _missingFull = false;
                      }),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _TankChoice(
                      key: const Key('not-full'),
                      selected: _full == false,
                      label: 'Не полный',
                      onPressed: () => setState(() {
                        _full = false;
                        _missingFull = false;
                      }),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                switch (_full) {
                  true => fullTankChosen,
                  false => partialTankChosen,
                  null => fullTankPrompt,
                },
                key: const Key('full-tank-hint'),
                style: text.bodyMedium?.copyWith(
                  color: _missingFull ? scheme.error : scheme.onSurface,
                ),
              ),
              const SizedBox(height: 12),
              FilledButton(
                key: const Key('save-fuel'),
                onPressed: _save,
                child: const Text('Сохранить'),
              ),
            ],
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
        children: [
          Text('Топливо', style: text.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<FuelType>(
            style: const ButtonStyle(
              minimumSize: WidgetStatePropertyAll(Size(48, 56)),
              visualDensity: VisualDensity.comfortable,
            ),
            segments: const [
              ButtonSegment(
                value: FuelType.petrol,
                label: Text('Бензин'),
                icon: Icon(Icons.local_gas_station_outlined),
                tooltip: 'Бензин',
              ),
              ButtonSegment(
                value: FuelType.lpg,
                label: Text('Газ'),
                icon: Icon(Icons.propane_tank_outlined),
                tooltip: 'LPG',
              ),
            ],
            selected: {_fuel},
            onSelectionChanged: (selected) =>
                setState(() => _fuel = selected.first),
          ),
          const SizedBox(height: 16),
          Text('Запись', style: text.titleSmall),
          const SizedBox(height: 8),
          TextField(
            key: const Key('quantity'),
            controller: _quantity,
            decoration: InputDecoration(
              labelText: 'Количество',
              suffixText: 'л',
              helperText: 'Литры, можно с запятой',
              errorText: _issueFor(quantityField),
            ),
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextField(
                  key: const Key('total'),
                  controller: _total,
                  decoration: InputDecoration(
                    labelText: 'Сумма чека',
                    suffixText: 'грн',
                    helperText: _totalHelp(),
                    errorText: _issueFor(totalField),
                  ),
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              SizedBox(
                width: 124,
                child: Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(
                    _enteredPriceText(),
                    key: const Key('entered-unit-price'),
                    style: text.bodyMedium,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            key: const Key('odometer'),
            controller: _odometer,
            decoration: InputDecoration(
              labelText: 'Одометр, км',
              suffixText: 'км',
              helperText: _odometerHelp(),
              errorText: _issueFor(odometerField),
            ),
            keyboardType: TextInputType.number,
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const Key('open-reset'),
              onPressed: () => openReset(context, widget.controller),
              icon: const Icon(Icons.speed_outlined),
              label: const Text('Замена панели'),
            ),
          ),
          Text(
            'Если меняли панель, новое показание вводится здесь, а не в заправке.',
            style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
          WhenButtons(
            when: _when,
            onPickDate: _pickDate,
            onPickTime: _pickTime,
          ),
        ],
      ),
    );
  }
}

class _TankChoice extends StatelessWidget {
  const _TankChoice({
    super.key,
    required this.selected,
    required this.label,
    required this.onPressed,
  });

  final bool selected;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final style = ButtonStyle(
      minimumSize: const WidgetStatePropertyAll(Size(48, 48)),
      textStyle: const WidgetStatePropertyAll(
        TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    );
    final child = Text(label, textAlign: TextAlign.center);
    if (selected) {
      return FilledButton(style: style, onPressed: onPressed, child: child);
    }
    return OutlinedButton(style: style, onPressed: onPressed, child: child);
  }
}
