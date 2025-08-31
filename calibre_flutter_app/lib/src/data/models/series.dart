class Series {
  final int? id;
  final String name;

  Series({
    this.id,
    required this.name,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
    };
  }

  factory Series.fromMap(Map<String, dynamic> map) {
    return Series(
      id: map['id'],
      name: map['name'],
    );
  }
}
