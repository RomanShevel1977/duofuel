import 'package:duofuel/src/calc/validation.dart';
import 'package:duofuel/src/ui/format.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('quantity rules name the field', () {
    expect(validateQuantity('').single.line, 'Количество: нужно заполнить');
    expect(validateQuantity('abc').single.line, 'Количество: не число');
    expect(
      validateQuantity('0').single.line,
      'Количество: значение должно быть больше нуля',
    );
    expect(
      validateQuantity('-1').single.line,
      'Количество: отрицательное значение не принимается',
    );
    expect(parseQuantity('10,5'), 10.5);
    expect(parseQuantity('1 0.5'), 10.5);
    expect(parseQuantity('0'), isNull);
  });

  test('odometer stays whole kilometres', () {
    expect(validateOdometer('').single.line, 'Одометр, км: нужно заполнить');
    expect(
      validateOdometer('10,5').single.line,
      'Одометр, км: нужны целые километры',
    );
    expect(
      validateOdometer('-3').single.line,
      'Одометр, км: отрицательное значение не принимается',
    );
    expect(parseOdometer('1 300'), 1300);
    expect(validateName('  ').single.line, 'Имя: нужно заполнить');
  });

  test('rates use a decimal comma and do not invent a zero', () {
    expect(formatRate(10), '10,0 л/100 км');
    expect(formatRate(8), '8,0 л/100 км');
    expect(formatLiters(30), '30');
    expect(latestValue(null), 'Недостаточно данных');
    expect(latestValue(null).contains('0,0'), isFalse);
  });
}
