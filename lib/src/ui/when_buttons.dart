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
    final text = Theme.of(context).textTheme;
    return Row(
      children: [
        Expanded(
          child: _WhenButton(
            caption: 'Дата',
            value: parts.first,
            icon: Icons.calendar_today_outlined,
            onPressed: onPickDate,
            valueStyle: text.titleMedium,
            captionStyle: text.labelMedium,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _WhenButton(
            caption: 'Время',
            value: parts.last,
            icon: Icons.schedule,
            onPressed: onPickTime,
            valueStyle: text.titleMedium,
            captionStyle: text.labelMedium,
          ),
        ),
      ],
    );
  }
}

class _WhenButton extends StatelessWidget {
  const _WhenButton({
    required this.caption,
    required this.value,
    required this.icon,
    required this.onPressed,
    required this.valueStyle,
    required this.captionStyle,
  });

  final String caption;
  final String value;
  final IconData icon;
  final VoidCallback onPressed;
  final TextStyle? valueStyle;
  final TextStyle? captionStyle;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(48, 64),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      onPressed: onPressed,
      child: Row(
        children: [
          Icon(icon),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(caption, style: captionStyle),
                Text(value, style: valueStyle),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
