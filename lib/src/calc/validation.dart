class FieldIssue {
  const FieldIssue(this.field, this.problem);

  final String field;
  final String problem;

  String get line => '$field: $problem';
}

const quantityField = 'Количество';
const odometerField = 'Одометр, км';
const nameField = 'Имя';
const totalField = 'Сумма чека';

/// Гривна, две копейки. Так прежнее приложение хранило сумму чека.
const uahCurrency = 'UAH';
const uahFractionDigits = 2;

List<FieldIssue> validateQuantity(String text) {
  final trimmed = text.trim();
  if (trimmed.isEmpty) {
    return const [FieldIssue(quantityField, 'нужно заполнить')];
  }
  final compact = _compact(trimmed).replaceAll(',', '.');
  final value = double.tryParse(compact);
  if (value == null || !value.isFinite) {
    return const [FieldIssue(quantityField, 'не число')];
  }
  if (value < 0) {
    return const [
      FieldIssue(quantityField, 'отрицательное значение не принимается'),
    ];
  }
  if (value == 0) {
    return const [
      FieldIssue(quantityField, 'значение должно быть больше нуля'),
    ];
  }
  return const [];
}

double? parseQuantity(String text) {
  if (validateQuantity(text).isNotEmpty) return null;
  return double.parse(_compact(text).replaceAll(',', '.'));
}

List<FieldIssue> validateOdometer(String text) {
  final trimmed = text.trim();
  if (trimmed.isEmpty) {
    return const [FieldIssue(odometerField, 'нужно заполнить')];
  }
  final compact = _compact(trimmed);
  if (compact.startsWith('-')) {
    return const [
      FieldIssue(odometerField, 'отрицательное значение не принимается'),
    ];
  }
  if (!RegExp(r'^\d+$').hasMatch(compact) || int.tryParse(compact) == null) {
    return const [FieldIssue(odometerField, 'нужны целые километры')];
  }
  return const [];
}

int? parseOdometer(String text) {
  if (validateOdometer(text).isNotEmpty) return null;
  return int.parse(_compact(text));
}

/// Пустая сумма допустима: заправка без чека не становится нулевой оплатой.
List<FieldIssue> validateTotal(String text) {
  final trimmed = text.trim();
  if (trimmed.isEmpty) return const [];
  final compact = _compact(trimmed).replaceAll(',', '.');
  if (compact.startsWith('-')) {
    return const [
      FieldIssue(totalField, 'отрицательное значение не принимается'),
    ];
  }
  final parsed = _minorUnits(compact);
  if (parsed == null) {
    return const [FieldIssue(totalField, 'не число')];
  }
  if (parsed < 0) {
    return const [FieldIssue(totalField, 'лишние знаки после запятой')];
  }
  return const [];
}

int? parseTotalMinor(String text) {
  if (validateTotal(text).isNotEmpty) return null;
  if (text.trim().isEmpty) return null;
  final minor = _minorUnits(_compact(text).replaceAll(',', '.'));
  if (minor == null || minor < 0) return null;
  return minor;
}

List<FieldIssue> validateName(String text) {
  if (text.trim().isEmpty) {
    return const [FieldIssue(nameField, 'нужно заполнить')];
  }
  return const [];
}

String _compact(String text) =>
    text.trim().replaceAll(' ', '').replaceAll('\u00a0', '');

/// Копейки. Лишние знаки после отбрасывания конечных нулей — сигнал -1, не округление.
int? _minorUnits(String compact) {
  if (compact.isEmpty || compact.startsWith('+')) return null;
  final parts = compact.split('.');
  if (parts.length > 2) return null;
  final whole = parts[0].isEmpty ? '0' : parts[0];
  if (!RegExp(r'^\d+$').hasMatch(whole)) return null;
  var frac = parts.length == 2 ? parts[1] : '';
  if (frac.isNotEmpty && !RegExp(r'^\d+$').hasMatch(frac)) return null;
  frac = frac.replaceFirst(RegExp(r'0+$'), '');
  if (frac.length > uahFractionDigits) return -1;
  final padded = frac.padRight(uahFractionDigits, '0');
  return int.parse(whole) * 100 + int.parse(padded);
}
