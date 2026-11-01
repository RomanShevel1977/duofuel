import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

Future<File> saveJournalCsv(String csv) async {
  final dir = await getApplicationDocumentsDirectory();
  final file = File('${dir.path}/duofuel-zhurnal.csv');
  await file.writeAsString(csv, flush: true);
  return file;
}

Future<void> shareJournalFile(File file) {
  return Share.shareXFiles([
    XFile(file.path, mimeType: 'text/csv', name: 'duofuel-zhurnal.csv'),
  ], subject: 'Журнал DuoFuel');
}
