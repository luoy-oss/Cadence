import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cadence/app.dart';

void main() {
  testWidgets('App launches correctly', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: CadenceApp()));
    await tester.pumpAndSettle();

    // 验证主页加载
    expect(find.text('Cadence'), findsOneWidget);
  });
}
