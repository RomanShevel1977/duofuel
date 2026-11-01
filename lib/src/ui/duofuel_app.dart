import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'fuel_form_page.dart';
import 'home_page.dart';
import 'journal_page.dart';
import 'log_controller.dart';
import 'reset_form_page.dart';

class DuoFuelApp extends StatefulWidget {
  const DuoFuelApp({super.key, required this.controller});

  final LogController controller;

  @override
  State<DuoFuelApp> createState() => _DuoFuelAppState();
}

class _DuoFuelAppState extends State<DuoFuelApp> {
  @override
  void initState() {
    super.initState();
    widget.controller.reload();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'DuoFuel',
      locale: const Locale('ru'),
      supportedLocales: const [Locale('ru')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      home: _Shell(controller: widget.controller),
    );
  }
}

class _Shell extends StatefulWidget {
  const _Shell({required this.controller});

  final LogController controller;

  @override
  State<_Shell> createState() => _ShellState();
}

class _ShellState extends State<_Shell> {
  int _index = 0;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_rebuild);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_rebuild);
    super.dispose();
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    return Scaffold(
      appBar: AppBar(
        title: const Text('DuoFuel'),
        actions: [
          if (controller.canWrite)
            PopupMenuButton<String>(
              tooltip: 'Ещё',
              onSelected: (value) {
                if (value == 'reset') openReset(context, controller);
              },
              itemBuilder: (context) => const [
                PopupMenuItem(value: 'reset', child: Text('Замена панели')),
              ],
            ),
        ],
      ),
      body: _body(controller),
      bottomNavigationBar: Material(
        color: Theme.of(context).colorScheme.surfaceContainer,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (controller.canWrite)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
                child: FilledButton.icon(
                  key: const Key('fab-fuel'),
                  onPressed: () => openFuel(context, controller),
                  icon: const Icon(Icons.local_gas_station),
                  label: const Text('Заправка'),
                ),
              ),
            NavigationBar(
              selectedIndex: _index,
              onDestinationSelected: (value) => setState(() => _index = value),
              destinations: const [
                NavigationDestination(
                  icon: Icon(Icons.speed_outlined),
                  selectedIcon: Icon(Icons.speed),
                  label: 'Сводка',
                ),
                NavigationDestination(
                  icon: Icon(Icons.list_alt_outlined),
                  selectedIcon: Icon(Icons.list_alt),
                  label: 'Журнал',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _body(LogController controller) {
    if (controller.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (controller.readError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            controller.readError!,
            key: const Key('read-error'),
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    return _index == 0
        ? HomePage(controller: controller)
        : JournalPage(controller: controller);
  }
}

ThemeData _theme(Brightness brightness) {
  final color = ColorScheme.fromSeed(
    seedColor: const Color(0xFF1F6B4A),
    brightness: brightness,
  );
  final buttonShape = RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(12),
  );
  return ThemeData(
    colorScheme: color,
    useMaterial3: true,
    inputDecorationTheme: const InputDecorationTheme(
      filled: true,
      border: OutlineInputBorder(),
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: color.outlineVariant),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(56),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        shape: buttonShape,
      ),
    ),
    iconButtonTheme: IconButtonThemeData(
      style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(48, 48),
        shape: buttonShape,
      ),
    ),
    navigationBarTheme: const NavigationBarThemeData(height: 72),
  );
}
