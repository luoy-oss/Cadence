import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'core/constants/app_colors.dart';
import 'screens/game_overlay_screen.dart';
import 'screens/home_screen.dart';
import 'screens/result_screen.dart';

class CadenceApp extends StatelessWidget {
  const CadenceApp({super.key});

  @override
  Widget build(BuildContext context) {
    // 设置状态栏透明
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
    ));

    return MaterialApp(
      title: 'Cadence',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppColors.background,
        colorScheme: const ColorScheme.dark(
          primary: AppColors.primary,
          secondary: AppColors.secondary,
          surface: AppColors.surface,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: AppColors.background,
          elevation: 0,
          centerTitle: true,
        ),
        useMaterial3: true,
      ),
      initialRoute: '/',
      routes: {
        '/': (context) => const HomeScreen(),
        '/game': (context) => const GameOverlayScreen(),
        '/result': (context) => const ResultScreen(),
      },
    );
  }
}
