import 'package:flutter/material.dart';

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
  List<FieldIssue> _issues = const [];

  DateTime _initialDate() {
    final existing = widget.existing;
    if (existing == null) return DateTime.now();
    return DateTime.fromMillisecondsSinceEpoch(existing.occurredAtMs);
  }

  @override
  void dispose() {
    _quantity.dispose();
    _odometer.dispose();
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

  String _odometerHelp() {
    final previous = previousOdometerKm(
      widget.controller.entries,
      _fuel,
      exceptId: widget.existing?.id,
    );
    if (previous == null) return 'Целые километры с панели';
    return 'Прошлое показание: $previous км';
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
    ];
    setState(() => _issues = issues);
    if (issues.isNotEmpty) return;
    final existing = widget.existing;
    final entry = FuelEntry(
      id: existing?.id ?? newId(),
      fuelType: _fuel,
      occurredAtMs: _when.millisecondsSinceEpoch,
      odometerKm: parseOdometer(_odometer.text)!,
      quantity: parseQuantity(_quantity.text)!,
      unit: existing?.unit ?? 'L',
      isFull: _full,
      createdAtMs:
          existing?.createdAtMs ?? DateTime.now().millisecondsSinceEpoch,
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
          SegmentedButton<FuelType>(
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
