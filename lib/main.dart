import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import 'src/data/file_fuel_log.dart';
import 'src/ui/duofuel_app.dart';
import 'src/ui/log_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final directory = await getApplicationDocumentsDirectory();
  final log = FileFuelLog(File('${directory.path}/fuel_log.json'));
  runApp(DuoFuelApp(controller: LogController(log)));
}
