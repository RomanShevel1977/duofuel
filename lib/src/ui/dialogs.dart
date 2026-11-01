import 'package:flutter/material.dart';

import '../calc/validation.dart';
import 'log_controller.dart';

Future<bool> confirmDelete(BuildContext context) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text('Удалить запись?'),
      content: const Text('Расход пересчитается по оставшимся заправкам.'),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Отмена'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, true),
          child: const Text('Удалить'),
        ),
      ],
    ),
  );
  return result ?? false;
}

Future<void> renameVehicle(
  BuildContext context,
  LogController controller,
) async {
  await showDialog<void>(
    context: context,
    builder: (context) => _RenameDialog(controller: controller),
  );
}

class _RenameDialog extends StatefulWidget {
  const _RenameDialog({required this.controller});

  final LogController controller;

  @override
  State<_RenameDialog> createState() => _RenameDialogState();
}

class _RenameDialogState extends State<_RenameDialog> {
  late final TextEditingController _name = TextEditingController(
    text: widget.controller.vehicleName,
  );
  String? _issue;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final issues = validateName(_name.text);
    if (issues.isNotEmpty) {
      setState(() => _issue = issues.first.line);
      return;
    }
    await widget.controller.rename(_name.text);
    if (!mounted) return;
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Имя автомобиля'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            key: const Key('vehicle-name'),
            controller: _name,
            decoration: const InputDecoration(labelText: 'Имя'),
            autofocus: true,
            onSubmitted: (_) => _save(),
          ),
          if (_issue != null) ...[const SizedBox(height: 8), Text(_issue!)],
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Отмена'),
        ),
        TextButton(onPressed: _save, child: const Text('Сохранить')),
      ],
    );
  }
}
