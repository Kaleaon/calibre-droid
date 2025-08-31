import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'src/data/database_repository.dart';
import 'src/importer/importer.dart';
import 'src/exporter/exporter.dart';
import 'src/ui/library_screen.dart';

void main() {
  // Ensure that the Flutter binding is initialized before calling runApp.
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CalibreApp());
}

class CalibreApp extends StatelessWidget {
  const CalibreApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Calibre Flutter',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Calibre Control Center'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool _isLoading = false;

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  Future<void> _handleImport() async {
    setState(() => _isLoading = true);
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        dialogTitle: 'Select metadata.db',
        type: FileType.any, // Cannot easily filter for .db, allow any.
      );

      if (result != null && result.files.single.path != null) {
        final importer = Importer(
          legacyDbPath: result.files.single.path!,
          appDbRepository: DatabaseRepository.instance,
        );
        await importer.import();
        _showSnackBar('Import completed successfully!');
      } else {
        _showSnackBar('Import cancelled.');
      }
    } catch (e) {
      _showSnackBar('Import failed: $e');
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _handleExport() async {
    setState(() => _isLoading = true);
    try {
      String? outputDirectory = await FilePicker.platform.getDirectoryPath(
        dialogTitle: 'Select directory to export library',
      );

      if (outputDirectory != null) {
        final exporter = Exporter(
          outputPath: '$outputDirectory/metadata_new.db',
          appDbRepository: DatabaseRepository.instance,
        );
        await exporter.export();
        _showSnackBar('Export completed successfully!');
      } else {
        _showSnackBar('Export cancelled.');
      }
    } catch (e) {
      _showSnackBar('Export failed: $e');
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: _isLoading
            ? const CircularProgressIndicator()
            : Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  ElevatedButton(
                    onPressed: _handleImport,
                    child: const Text('Import Library'),
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton(
                    onPressed: _handleExport,
                    child: const Text('Export Library'),
                  ),
                  const SizedBox(height: 40),
                  ElevatedButton(
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (context) => const LibraryScreen()),
                      );
                    },
                    child: const Text('View Library'),
                  ),
                ],
              ),
      ),
    );
  }
}
