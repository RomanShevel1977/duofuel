import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';

Future<String?> pickSnapshotJson() async {
  final result = await FilePicker.pickFiles(
    type: FileType.custom,
    allowedExtensions: const ['json'],
    withData: true,
  );
  if (result == null || result.files.isEmpty) return null;
  final picked = result.files.single;
  final bytes = picked.bytes;
  if (bytes != null) return utf8.decode(bytes);
  final path = picked.path;
  if (path == null) return null;
  return File(path).readAsString();
}
