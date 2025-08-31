class Book {
  final int? id;
  final String title;
  final String? sortTitle;
  final int lastModified;
  final int publicationDate;
  final double seriesIndex;
  final String? authorSort;
  final String? isbn;
  final String? uuid;
  final bool hasCover;
  final String path;
  final int? seriesId;

  Book({
    this.id,
    required this.title,
    this.sortTitle,
    required this.lastModified,
    required this.publicationDate,
    this.seriesIndex = 1.0,
    this.authorSort,
    this.isbn,
    this.uuid,
    this.hasCover = false,
    required this.path,
    this.seriesId,
  });

  /// Converts this Book instance into a Map.
  /// The keys must correspond to the names of the columns in the database.
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'sort_title': sortTitle,
      'last_modified': lastModified,
      'publication_date': publicationDate,
      'series_index': seriesIndex,
      'author_sort': authorSort,
      'isbn': isbn,
      'uuid': uuid,
      'has_cover': hasCover ? 1 : 0,
      'path': path,
      'series_id': seriesId,
    };
  }

  /// Creates a Book instance from a Map.
  factory Book.fromMap(Map<String, dynamic> map) {
    return Book(
      id: map['id'],
      title: map['title'],
      sortTitle: map['sort_title'],
      lastModified: map['last_modified'],
      publicationDate: map['publication_date'],
      seriesIndex: map['series_index'],
      authorSort: map['author_sort'],
      isbn: map['isbn'],
      uuid: map['uuid'],
      hasCover: map['has_cover'] == 1,
      path: map['path'],
      seriesId: map['series_id'],
    );
  }
}
