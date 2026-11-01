import 'package:flutter/material.dart';

import '../calc/odometer_order.dart';
import '../calc/price_average.dart';
import '../calc/validation.dart';
import '../model/ids.dart';
import '../model/records.dart';
import 'format.dart';
import 'log_controller.dart';
import 'when_buttons.dart';

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
  late bool _full = widget.existing?.isFull ?? false;
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
    setState(() => _issues = issues);
    if (issues.isNotEmpty) return;
    final existing = widget.existing;
    final totalMinor = parseTotalMinor(_total.text);
    final entry = FuelEntry(
      id: _entryId,
      fuelType: _fuel,
      occurredAtMs: _when.millisecondsSinceEpoch,
      odometerKm: parseOdometer(_odometer.text)!,
      quantity: parseQuantity(_quantity.text)!,
      unit: existing?.unit ?? 'L',
      isFull: _full,
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
          child: FilledButton(
            key: const Key('save-fuel'),
            onPressed: _save,
            child: const Text('Сохранить'),
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
          Text('Чтобы посчитать расход', style: text.titleSmall),
          const SizedBox(height: 8),
          Material(
            color: _full
                ? scheme.primaryContainer
                : scheme.surfaceContainerHighest,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: BorderSide(
                color: _full ? scheme.primary : scheme.outlineVariant,
              ),
            ),
            child: SwitchListTile(
              key: const Key('full-tank'),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 8,
              ),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              title: const Text('Полный бак'),
              subtitle: const Text(
                'До отсечки. Иначе эта заправка не закроет цикл.',
              ),
              value: _full,
              onChanged: (value) => setState(() => _full = value),
            ),
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
