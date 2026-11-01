import 'package:flutter/material.dart';

import 'format.dart';

class WhenButtons extends StatelessWidget {
  const WhenButtons({
    super.key,
    required this.when,
    required this.onPickDate,
    required this.onPickTime,
  });

  final DateTime when;
  final VoidCallback onPickDate;
  final VoidCallback onPickTime;

  @override
  Widget build(BuildContext context) {
    final parts = formatWhen(when).split(' ');
    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: onPickDate,
            icon: const Icon(Icons.calendar_today_outlined),
            label: Text(parts.first),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton.icon(
            onPressed: onPickTime,
            icon: const Icon(Icons.schedule),
            label: Text(parts.last),
          ),
        ),
      ],
    );
  }
}
