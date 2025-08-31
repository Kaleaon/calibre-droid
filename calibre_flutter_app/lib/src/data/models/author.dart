class Author {
  final int? id;
  final String name;
  final String? sortName;

  Author({
    this.id,
    required this.name,
    this.sortName,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'sort_name': sortName,
    };
  }

  factory Author.fromMap(Map<String, dynamic> map) {
    return Author(
      id: map['id'],
      name: map['name'],
      sortName: map['sort_name'],
    );
  }
}
