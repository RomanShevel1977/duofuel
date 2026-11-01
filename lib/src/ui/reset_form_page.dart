import 'package:flutter/material.dart';

import '../calc/validation.dart';
import '../model/ids.dart';
import '../model/records.dart';
import 'log_controller.dart';
import 'when_buttons.dart';

void openReset(
  BuildContext context,
  LogController controller, {
  OdometerReset? existing,
}) {
  Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (context) =>
          ResetFormPage(controller: controller, existing: existing),
    ),
  );
}

class ResetFormPage extends StatefulWidget {
  const ResetFormPage({super.key, required this.controller, this.existing});

  final LogController controller;
  final OdometerReset? existing;

  @override
  State<ResetFormPage> createState() => _ResetFormPageState();
}

class _ResetFormPageState extends State<ResetFormPage> {
  late DateTime _date = _initialDate();
  late TimeOfDay _time = TimeOfDay.fromDateTime(_date);
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
    _odometer.dispose();
    super.dispose();
  }

  DateTime get _when =>
      DateTime(_date.year, _date.month, _date.day, _time.hour, _time.minute);

  String? get _odometerIssue {
    for (final issue in _issues) {
      if (issue.field == odometerField) return issue.line;
    }
    return null;
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
    final issues = validateOdometer(_odometer.text);
    setState(() => _issues = issues);
    if (issues.isNotEmpty) return;
    final existing = widget.existing;
    final reset = OdometerReset(
      id: existing?.id ?? newId(),
      occurredAtMs: _when.millisecondsSinceEpoch,
      odometerKm: parseOdometer(_odometer.text)!,
      createdAtMs:
          existing?.createdAtMs ?? DateTime.now().millisecondsSinceEpoch,
      linkedFuelEntryId: existing?.linkedFuelEntryId,
    );
    final messenger = ScaffoldMessenger.of(context);
    await widget.controller.saveReset(reset);
    if (!mounted) return;
    Navigator.pop(context);
    messenger.showSnackBar(
      const SnackBar(content: Text('Замена панели сохранена')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Замена панели')),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: FilledButton(
            key: const Key('save-reset'),
            onPressed: _save,
            child: const Text('Сохранить'),
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Новое показание после замены панели. Цикл, который пересекает эту отметку, останется без расхода: старые и новые километры не складываются.',
            style: text.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: 16),
          TextField(
            key: const Key('reset-odometer'),
            controller: _odometer,
            decoration: InputDecoration(
              labelText: 'Одометр, км',
              suffixText: 'км',
              helperText: 'Целые километры с новой панели',
              errorText: _odometerIssue,
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
