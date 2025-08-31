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
  final String? publisher;
  final String? comments;

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
    this.publisher,
    this.comments,
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
      'publisher': publisher,
      'comments': comments,
    };
  }

  /// Creates a Book instance from a Map.
  Book copyWith({
    int? id,
    String? title,
    String? sortTitle,
    int? lastModified,
    int? publicationDate,
    double? seriesIndex,
    String? authorSort,
    String? isbn,
    String? uuid,
    bool? hasCover,
    String? path,
    int? seriesId,
    String? publisher,
    String? comments,
  }) {
    return Book(
      id: id ?? this.id,
      title: title ?? this.title,
      sortTitle: sortTitle ?? this.sortTitle,
      lastModified: lastModified ?? this.lastModified,
      publicationDate: publicationDate ?? this.publicationDate,
      seriesIndex: seriesIndex ?? this.seriesIndex,
      authorSort: authorSort ?? this.authorSort,
      isbn: isbn ?? this.isbn,
      uuid: uuid ?? this.uuid,
      hasCover: hasCover ?? this.hasCover,
      path: path ?? this.path,
      seriesId: seriesId ?? this.seriesId,
      publisher: publisher ?? this.publisher,
      comments: comments ?? this.comments,
    );
  }

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
      publisher: map['publisher'],
      comments: map['comments'],
    );
  }
}
