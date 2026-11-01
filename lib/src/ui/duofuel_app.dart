import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'fuel_form_page.dart';
import 'home_page.dart';
import 'journal_page.dart';
import 'log_controller.dart';

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
      appBar: AppBar(title: const Text('DuoFuel')),
      body: _body(controller),
      floatingActionButton: controller.canWrite
          ? FloatingActionButton.extended(
              key: const Key('fab-fuel'),
              onPressed: () => openFuel(context, controller),
              icon: const Icon(Icons.local_gas_station),
              label: const Text('Заправка'),
            )
          : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.speed), label: 'Сводка'),
          NavigationDestination(icon: Icon(Icons.list_alt), label: 'Журнал'),
        ],
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
  return ThemeData(
    colorScheme: color,
    useMaterial3: true,
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
    ),
  );
}
