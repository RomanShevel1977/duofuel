import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

String journalExportFileName(DateTime time) {
  String two(int number) => number.toString().padLeft(2, '0');
  final millis = time.millisecond.toString().padLeft(3, '0');
  return 'duofuel-zhurnal-'
      '${time.year}${two(time.month)}${two(time.day)}-'
      '${two(time.hour)}${two(time.minute)}${two(time.second)}$millis.csv';
}

/// Каждый экспорт — новый файл. Прежний duofuel-zhurnal.csv не затирается.
Future<File> saveJournalCsv(String csv, {DateTime? now}) async {
  final dir = await getApplicationDocumentsDirectory();
  final folder = Directory('${dir.path}/duofuel-export');
  await folder.create(recursive: true);
  final name = journalExportFileName(now ?? DateTime.now());
  var file = File('${folder.path}/$name');
  var suffix = 2;
  while (await file.exists()) {
    final stamp = name.replaceFirst('.csv', '-$suffix.csv');
    file = File('${folder.path}/$stamp');
    suffix += 1;
  }
  await file.writeAsString(csv, flush: true);
  return file;
}

String snapshotExportFileName(DateTime time) {
  String two(int number) => number.toString().padLeft(2, '0');
  final millis = time.millisecond.toString().padLeft(3, '0');
  return 'duofuel-fuel-log-'
      '${time.year}${two(time.month)}${two(time.day)}-'
      '${two(time.hour)}${two(time.minute)}${two(time.second)}$millis.json';
}

Future<File> saveTextExport({
  required String contents,
  required String fileName,
}) async {
  final dir = await getApplicationDocumentsDirectory();
  final folder = Directory('${dir.path}/duofuel-export');
  await folder.create(recursive: true);
  final dot = fileName.lastIndexOf('.');
  final stem = dot < 0 ? fileName : fileName.substring(0, dot);
  final ext = dot < 0 ? '' : fileName.substring(dot);
  var file = File('${folder.path}/$fileName');
  var suffix = 2;
  while (await file.exists()) {
    file = File('${folder.path}/$stem-$suffix$ext');
    suffix += 1;
  }
  await file.writeAsString(contents, flush: true);
  return file;
}

Future<void> shareJournalFile(File file, {String? text}) {
  final name = file.uri.pathSegments.last;
  return Share.shareXFiles(
    [XFile(file.path, mimeType: 'text/csv', name: name)],
    subject: 'Журнал DuoFuel',
    text: text,
  );
}
