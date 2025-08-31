class Publisher {
  final int? id;
  final String name;

  Publisher({
    this.id,
    required this.name,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
    };
  }

  factory Publisher.fromMap(Map<String, dynamic> map) {
    return Publisher(
      id: map['id'],
      name: map['name'],
    );
  }
}
