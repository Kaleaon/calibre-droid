import 'package:http/http.dart' as http;
import 'package:xml/xml.dart';
import 'models/book.dart';

class MetadataService {
  final String _baseUrl = 'https://books.google.com/books/feeds/volumes';

  // A helper to safely extract text from an XML element.
  String? _getText(XmlElement element, String tagName) {
    try {
      return element.findAllElements(tagName, namespace: 'http://purl.org/dc/terms').first.innerText;
    } catch (e) {
      return null;
    }
  }

  Future<Book?> fetchMetadata({String? title, String? author, String? isbn}) async {
    if (title == null && author == null && isbn == null) {
      throw ArgumentError('At least one search parameter must be provided.');
    }

    // Construct the query based on Calibre's logic.
    List<String> queryParts = [];
    if (isbn != null) {
      queryParts.add('isbn:$isbn');
    } else {
      if (title != null) {
        queryParts.add('intitle:$title');
      }
      if (author != null) {
        queryParts.add('inauthor:$author');
      }
    }
    final String query = queryParts.join('+');
    final url = Uri.parse('$_baseUrl?q=$query&max-results=1');

    try {
      final response = await http.get(url);
      if (response.statusCode == 200) {
        final document = XmlDocument.parse(response.body);
        final entries = document.findAllElements('entry', namespace: 'http://www.w3.org/2005/Atom');

        if (entries.isNotEmpty) {
          final entry = entries.first;

          // Parse the data using the logic discovered from the Python source.
          final fetchedTitle = _getText(entry, 'title') ?? 'Unknown Title';
          final fetchedAuthors = entry.findAllElements('creator', namespace: 'http://purl.org/dc/terms').map((e) => e.innerText).toList();
          final fetchedPublisher = _getText(entry, 'publisher');
          final dateStr = _getText(entry, 'date');
          final description = _getText(entry, 'description');

          // For simplicity, we create a new Book object.
          // A real implementation might return a more specific metadata object.
          return Book(
            title: fetchedTitle,
            authorSort: fetchedAuthors.isNotEmpty ? fetchedAuthors.first : null, // Simplified
            publisher: fetchedPublisher, // Requires adding 'publisher' to Book model
            comments: description, // Requires adding 'comments' to Book model
            publicationDate: dateStr != null ? DateTime.tryParse(dateStr)?.millisecondsSinceEpoch ?? 0 : 0,
            // Other fields would be populated here...
            // These are placeholders as they don't come from this specific API call.
            lastModified: DateTime.now().millisecondsSinceEpoch,
            path: '',
          );
        }
      }
    } catch (e) {
      // Handle exceptions, e.g., network errors
      print('Error fetching metadata: $e');
    }
    return null;
  }
}
