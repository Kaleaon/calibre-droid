import 'package:shared_preferences/shared_preferences.dart';

class PreferenceService {
  static const String _libraryPathKey = 'calibre_library_path';

  Future<void> saveLibraryPath(String path) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_libraryPathKey, path);
  }

  Future<String?> getLibraryPath() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_libraryPathKey);
  }
}
