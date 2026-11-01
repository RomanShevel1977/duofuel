class FieldIssue {
  const FieldIssue(this.field, this.problem);

  final String field;
  final String problem;

  String get line => '$field: $problem';
}

const quantityField = 'Количество';
const odometerField = 'Одометр, км';
const nameField = 'Имя';

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

List<FieldIssue> validateName(String text) {
  if (text.trim().isEmpty) {
    return const [FieldIssue(nameField, 'нужно заполнить')];
  }
  return const [];
}

String _compact(String text) =>
    text.trim().replaceAll(' ', '').replaceAll('\u00a0', '');
