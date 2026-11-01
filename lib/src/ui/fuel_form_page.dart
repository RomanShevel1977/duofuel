import 'package:flutter/material.dart';

import '../calc/validation.dart';
import '../model/ids.dart';
import '../model/records.dart';
import 'format.dart';
import 'log_controller.dart';

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
  late FuelType _fuel = widget.existing?.fuelType ?? FuelType.petrol;
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
    messenger.showSnackBar(const SnackBar(content: Text('Запись сохранена')));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.existing == null ? 'Заправка' : 'Изменить заправку'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SegmentedButton<FuelType>(
            segments: const [
              ButtonSegment(
                value: FuelType.petrol,
                label: Text('Бензин'),
                tooltip: 'Бензин',
              ),
              ButtonSegment(
                value: FuelType.lpg,
                label: Text('LPG'),
                tooltip: 'LPG',
              ),
            ],
            selected: {_fuel},
            onSelectionChanged: (selected) =>
                setState(() => _fuel = selected.first),
          ),
          const SizedBox(height: 16),
          TextField(
            key: const Key('quantity'),
            controller: _quantity,
            decoration: const InputDecoration(
              labelText: 'Количество',
              suffixText: 'л',
            ),
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          const SizedBox(height: 12),
          TextField(
            key: const Key('odometer'),
            controller: _odometer,
            decoration: const InputDecoration(
              labelText: 'Одометр, км',
              suffixText: 'км',
            ),
            keyboardType: TextInputType.number,
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _pickDate,
            child: Text('Дата: ${formatWhen(_when).split(' ').first}'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _pickTime,
            child: Text('Время: ${formatWhen(_when).split(' ').last}'),
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            key: const Key('full-tank'),
            contentPadding: EdgeInsets.zero,
            title: const Text('Полный бак'),
            subtitle: const Text('Отметьте, если бак заправлен до отсечки.'),
            value: _full,
            onChanged: (value) => setState(() => _full = value),
          ),
          for (final issue in _issues)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(issue.line),
            ),
          const SizedBox(height: 8),
          FilledButton(
            key: const Key('save-fuel'),
            onPressed: _save,
            child: const Text('Сохранить'),
          ),
        ],
      ),
    );
  }
}
