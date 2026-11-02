import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../data/fuel_log.dart';
import 'fuel_form_page.dart';
import 'home_page.dart';
import 'journal_page.dart';
import 'log_controller.dart';
import 'share_journal.dart';
import 'snapshot_pick.dart';
import 'theme_choice.dart';

class DuoFuelApp extends StatefulWidget {
  const DuoFuelApp({
    super.key,
    required this.controller,
    this.onExportCsv,
    this.onShareText,
    this.pickSnapshot,
  });

  final LogController controller;
  final Future<void> Function(String csv)? onExportCsv;
  final Future<void> Function(String text)? onShareText;
  final Future<String?> Function()? pickSnapshot;

  @override
  State<DuoFuelApp> createState() => _DuoFuelAppState();
}

class _DuoFuelAppState extends State<DuoFuelApp> {
  ThemeChoice _themeChoice = ThemeChoice.system;

  @override
  void initState() {
    super.initState();
    widget.controller.reload();
    _loadTheme();
  }

  Future<void> _loadTheme() async {
    final choice = await readThemeChoice();
    if (!mounted) return;
    setState(() => _themeChoice = choice);
  }

  Future<void> _setTheme(ThemeChoice choice) async {
    setState(() => _themeChoice = choice);
    await writeThemeChoice(choice);
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
      themeMode: themeModeOf(_themeChoice),
      home: _Shell(
        controller: widget.controller,
        themeChoice: _themeChoice,
        onTheme: _setTheme,
        onExportCsv: widget.onExportCsv,
        onShareText: widget.onShareText,
        pickSnapshot: widget.pickSnapshot,
      ),
    );
  }
}

class _Shell extends StatefulWidget {
  const _Shell({
    required this.controller,
    required this.themeChoice,
    required this.onTheme,
    required this.onExportCsv,
    required this.onShareText,
    required this.pickSnapshot,
  });

  final LogController controller;
  final ThemeChoice themeChoice;
  final ValueChanged<ThemeChoice> onTheme;
  final Future<void> Function(String csv)? onExportCsv;
  final Future<void> Function(String text)? onShareText;
  final Future<String?> Function()? pickSnapshot;

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

  Future<void> _shareText(String text, {required String fileName}) async {
    final custom = widget.onShareText;
    if (custom != null) {
      await custom(text);
      return;
    }
    final file = await saveTextExport(contents: text, fileName: fileName);
    await shareJournalFile(
      file,
      text: 'Копия журнала DuoFuel. Её можно вернуть в приложение.',
    );
  }

  Future<void> _exportSnapshot() async {
    try {
      await _shareText(
        widget.controller.journalSnapshot(),
        fileName: snapshotExportFileName(DateTime.now()),
      );
    } catch (_) {
      if (!mounted) return;
      _snack(journalWriteError);
    }
  }

  Future<void> _importSnapshot() async {
    final pick = widget.pickSnapshot ?? pickSnapshotJson;
    final String? text;
    try {
      text = await pick();
    } catch (_) {
      if (!mounted) return;
      _snack('Не удалось открыть файл копии.');
      return;
    }
    if (text == null || !mounted) return;
    final replace = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Восстановить копию?'),
        content: const Text(
          'Текущие заправки и замены панели будут заменены записями из файла.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Отмена'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Заменить'),
          ),
        ],
      ),
    );
    if (replace != true || !mounted) return;
    try {
      final saved = await widget.controller.importSnapshot(text);
      if (!mounted) return;
      _snack(saved ? 'Журнал восстановлен из копии.' : journalWriteError);
    } on FuelLogReadException {
      if (!mounted) return;
      _snack('Это не копия журнала DuoFuel.');
    }
  }

  Future<void> _shareBroken() async {
    final raw = await widget.controller.rawJournal();
    if (!mounted) return;
    if (raw == null) {
      _snack('Файла журнала нет.');
      return;
    }
    try {
      await _shareText(raw, fileName: snapshotExportFileName(DateTime.now()));
    } catch (_) {
      if (!mounted) return;
      _snack('Не удалось отправить файл.');
    }
  }

  Future<void> _startEmpty() async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Начать пустой журнал?'),
        content: const Text(
          'Битый файл не удаляется. Он останется рядом под новым именем, а журнал начнётся пустым.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Отмена'),
          ),
          TextButton(
            key: const Key('confirm-empty-journal'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Начать пустой'),
          ),
        ],
      ),
    );
    if (accepted != true || !mounted) return;
    try {
      final kept = await widget.controller.startEmptyJournal();
      if (!mounted) return;
      _snack(
        kept == null
            ? 'Пустой журнал начат.'
            : 'Пустой журнал начат. Битый файл сохранён: $kept',
      );
    } catch (_) {
      if (!mounted) return;
      _snack(journalWriteError);
    }
  }

  void _snack(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    return Scaffold(
      appBar: AppBar(
        title: const Text('DuoFuel'),
        actions: [
          PopupMenuButton<String>(
            tooltip: 'Ещё',
            onSelected: (value) {
              if (value == 'export-snapshot') {
                _exportSnapshot();
                return;
              }
              if (value == 'import-snapshot') {
                _importSnapshot();
                return;
              }
              final choice = switch (value) {
                'theme-light' => ThemeChoice.light,
                'theme-dark' => ThemeChoice.dark,
                'theme-system' => ThemeChoice.system,
                _ => null,
              };
              if (choice != null) widget.onTheme(choice);
            },
            itemBuilder: (context) => [
              if (controller.canWrite) ...[
                const PopupMenuItem(
                  value: 'export-snapshot',
                  child: Text('Копия журнала'),
                ),
                const PopupMenuItem(
                  value: 'import-snapshot',
                  child: Text('Восстановить копию'),
                ),
              ],
              PopupMenuItem(
                value: 'theme-system',
                child: _ThemeRow(
                  label: 'Как в системе',
                  selected: widget.themeChoice == ThemeChoice.system,
                ),
              ),
              PopupMenuItem(
                value: 'theme-light',
                child: _ThemeRow(
                  label: 'Светлая',
                  selected: widget.themeChoice == ThemeChoice.light,
                ),
              ),
              PopupMenuItem(
                value: 'theme-dark',
                child: _ThemeRow(
                  label: 'Тёмная',
                  selected: widget.themeChoice == ThemeChoice.dark,
                ),
              ),
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
      return _BrokenJournal(
        message: controller.readError!,
        onShare: _shareBroken,
        onStartEmpty: _startEmpty,
      );
    }
    return IndexedStack(
      index: _index,
      children: [
        HomePage(controller: controller),
        JournalPage(controller: controller, onExport: widget.onExportCsv),
      ],
    );
  }
}

class _BrokenJournal extends StatelessWidget {
  const _BrokenJournal({
    required this.message,
    required this.onShare,
    required this.onStartEmpty,
  });

  final String message;
  final VoidCallback onShare;
  final VoidCallback onStartEmpty;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              message,
              key: const Key('read-error'),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            FilledButton(
              key: const Key('share-broken-journal'),
              onPressed: onShare,
              child: const Text('Отправить файл'),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              key: const Key('start-empty-journal'),
              onPressed: onStartEmpty,
              child: const Text('Начать пустой журнал'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ThemeRow extends StatelessWidget {
  const _ThemeRow({required this.label, required this.selected});

  final String label;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: Text(label)),
        if (selected) const Icon(Icons.check, size: 20),
      ],
    );
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
