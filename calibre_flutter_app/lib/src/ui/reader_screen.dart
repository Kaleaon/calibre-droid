import 'package:flutter/material.dart';

class ReaderScreen extends StatefulWidget {
  final String bookPath;

  const ReaderScreen({super.key, required this.bookPath});

  @override
  State<ReaderScreen> createState() => _ReaderScreenState();
}

class _ReaderScreenState extends State<ReaderScreen> {
  @override
  void initState() {
    super.initState();
    // In a real implementation, we would initialize the reader here.
    // For example: VocsyEpub.open(widget.bookPath);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('E-Reader'),
      ),
      body: Center(
        child: Text('Loading book from: ${widget.bookPath}'),
      ),
    );
  }
}
