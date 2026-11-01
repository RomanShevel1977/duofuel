import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

enum ThemeChoice { system, light, dark }

ThemeMode themeModeOf(ThemeChoice choice) {
  return switch (choice) {
    ThemeChoice.system => ThemeMode.system,
    ThemeChoice.light => ThemeMode.light,
    ThemeChoice.dark => ThemeMode.dark,
  };
}

ThemeChoice parseThemeChoice(String raw) {
  return switch (raw.trim()) {
    'light' => ThemeChoice.light,
    'dark' => ThemeChoice.dark,
    _ => ThemeChoice.system,
  };
}

Future<ThemeChoice> readThemeChoice() async {
  try {
    final file = await _themeFile();
    if (!await file.exists()) return ThemeChoice.system;
    return parseThemeChoice(await file.readAsString());
  } catch (_) {
    return ThemeChoice.system;
  }
}

Future<void> writeThemeChoice(ThemeChoice choice) async {
  try {
    final file = await _themeFile();
    await file.writeAsString(choice.name, flush: true);
  } catch (_) {}
}

Future<File> _themeFile() async {
  final dir = await getApplicationDocumentsDirectory();
  return File('${dir.path}/duofuel-theme.txt');
}
