package org.calibre.i18n

import org.calibre.utils.Logger
import java.io.InputStream
import java.text.MessageFormat
import java.util.*

/**
 * Internationalization (I18n) support for Calibre Kotlin.
 * 
 * Features:
 * - Multiple language support
 * - Pluralization
 * - Date/time formatting
 * - Number formatting
 * - Message interpolation
 * - Fallback to default locale
 * 
 * Usage:
 * ```
 * val i18n = I18n.getInstance()
 * val message = i18n.t("library.book_count", mapOf("count" to 42))
 * ```
 */
class I18n private constructor() {
    
    private var currentLocale: Locale = Locale.getDefault()
    private val bundles = mutableMapOf<Locale, ResourceBundle>()
    private val loadedTranslations = mutableMapOf<Locale, Map<String, String>>()
    
    companion object {
        private var instance: I18n? = null
        
        @Synchronized
        fun getInstance(): I18n {
            if (instance == null) {
                instance = I18n()
            }
            return instance!!
        }
        
        // Convenience function for quick translations
        fun t(key: String, params: Map<String, Any>? = null): String {
            return getInstance().translate(key, params)
        }
        
        // Supported locales
        val SUPPORTED_LOCALES = listOf(
            Locale.ENGLISH,
            Locale.GERMAN,
            Locale.FRENCH,
            Locale("es"),      // Spanish
            Locale("pt"),      // Portuguese
            Locale.ITALIAN,
            Locale("ru"),      // Russian
            Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE,
            Locale.JAPANESE,
            Locale.KOREAN,
            Locale("ar"),      // Arabic
            Locale("nl"),      // Dutch
            Locale("pl"),      // Polish
            Locale("sv"),      // Swedish
            Locale("tr"),      // Turkish
            Locale("uk"),      // Ukrainian
            Locale("cs"),      // Czech
            Locale("da"),      // Danish
            Locale("fi"),      // Finnish
            Locale("hu"),      // Hungarian
            Locale("id"),      // Indonesian
            Locale("ro"),      // Romanian
            Locale("vi"),      // Vietnamese
            Locale("el"),      // Greek
            Locale("he"),      // Hebrew
            Locale("th")       // Thai
        )
    }
    
    init {
        loadTranslations()
    }
    
    /**
     * Sets the current locale.
     */
    fun setLocale(locale: Locale) {
        currentLocale = locale
        Logger.info("Locale set to: ${locale.displayName}")
    }
    
    /**
     * Gets the current locale.
     */
    fun getLocale(): Locale = currentLocale
    
    /**
     * Translates a key to the current locale.
     */
    fun translate(key: String, params: Map<String, Any>? = null): String {
        val translations = getTranslations(currentLocale)
        val template = translations[key] ?: getTranslations(Locale.ENGLISH)[key] ?: key
        
        return if (params != null) {
            interpolate(template, params)
        } else {
            template
        }
    }
    
    /**
     * Translates with pluralization support.
     */
    fun translatePlural(key: String, count: Int, params: Map<String, Any>? = null): String {
        val pluralKey = when {
            count == 0 -> "${key}.zero"
            count == 1 -> "${key}.one"
            count in 2..4 -> "${key}.few"
            else -> "${key}.other"
        }
        
        val allParams = (params ?: emptyMap()) + mapOf("count" to count)
        return translate(pluralKey, allParams).ifEmpty { translate("${key}.other", allParams) }
    }
    
    /**
     * Formats a date according to the current locale.
     */
    fun formatDate(date: Date, style: Int = java.text.DateFormat.MEDIUM): String {
        val formatter = java.text.DateFormat.getDateInstance(style, currentLocale)
        return formatter.format(date)
    }
    
    /**
     * Formats a date and time according to the current locale.
     */
    fun formatDateTime(date: Date, dateStyle: Int = java.text.DateFormat.MEDIUM, timeStyle: Int = java.text.DateFormat.SHORT): String {
        val formatter = java.text.DateFormat.getDateTimeInstance(dateStyle, timeStyle, currentLocale)
        return formatter.format(date)
    }
    
    /**
     * Formats a number according to the current locale.
     */
    fun formatNumber(number: Number): String {
        val formatter = java.text.NumberFormat.getInstance(currentLocale)
        return formatter.format(number)
    }
    
    /**
     * Formats a file size in human-readable form.
     */
    fun formatFileSize(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024
        
        return when {
            bytes >= gb -> translate("size.gb", mapOf("size" to String.format("%.2f", bytes.toDouble() / gb)))
            bytes >= mb -> translate("size.mb", mapOf("size" to String.format("%.2f", bytes.toDouble() / mb)))
            bytes >= kb -> translate("size.kb", mapOf("size" to String.format("%.2f", bytes.toDouble() / kb)))
            else -> translate("size.bytes", mapOf("size" to bytes))
        }
    }
    
    private fun getTranslations(locale: Locale): Map<String, String> {
        return loadedTranslations[locale] 
            ?: loadedTranslations[Locale(locale.language)]
            ?: loadedTranslations[Locale.ENGLISH]
            ?: emptyMap()
    }
    
    private fun loadTranslations() {
        // Load built-in translations
        loadedTranslations[Locale.ENGLISH] = getEnglishTranslations()
        loadedTranslations[Locale("es")] = getSpanishTranslations()
        loadedTranslations[Locale.GERMAN] = getGermanTranslations()
        loadedTranslations[Locale.FRENCH] = getFrenchTranslations()
        loadedTranslations[Locale.SIMPLIFIED_CHINESE] = getChineseTranslations()
        loadedTranslations[Locale.JAPANESE] = getJapaneseTranslations()
        loadedTranslations[Locale("ru")] = getRussianTranslations()
        loadedTranslations[Locale("pt")] = getPortugueseTranslations()
        loadedTranslations[Locale.ITALIAN] = getItalianTranslations()
        loadedTranslations[Locale("ar")] = getArabicTranslations()
        
        // Try to load from resource files
        for (locale in SUPPORTED_LOCALES) {
            try {
                val langCode = locale.language
                val stream = javaClass.getResourceAsStream("/messages_$langCode.properties")
                if (stream != null) {
                    val props = Properties()
                    props.load(stream)
                    loadedTranslations[locale] = props.entries
                        .associate { (k, v) -> k.toString() to v.toString() }
                }
            } catch (e: Exception) {
                // Ignore missing translation files
            }
        }
        
        Logger.info("Loaded translations for ${loadedTranslations.size} locales")
    }
    
    private fun interpolate(template: String, params: Map<String, Any>): String {
        var result = template
        for ((key, value) in params) {
            result = result.replace("{$key}", value.toString())
        }
        return result
    }
    
    // Built-in translation tables
    
    private fun getEnglishTranslations(): Map<String, String> = mapOf(
        // Library
        "library.title" to "Library",
        "library.empty" to "No books in library",
        "library.book_count.zero" to "No books",
        "library.book_count.one" to "1 book",
        "library.book_count.other" to "{count} books",
        "library.add_book" to "Add Book",
        "library.remove_book" to "Remove Book",
        "library.search" to "Search library...",
        
        // Book metadata
        "book.title" to "Title",
        "book.authors" to "Authors",
        "book.series" to "Series",
        "book.tags" to "Tags",
        "book.publisher" to "Publisher",
        "book.language" to "Language",
        "book.rating" to "Rating",
        "book.added" to "Date Added",
        "book.published" to "Published",
        "book.format" to "Format",
        "book.size" to "Size",
        "book.description" to "Description",
        
        // Actions
        "action.convert" to "Convert",
        "action.edit" to "Edit",
        "action.delete" to "Delete",
        "action.download" to "Download",
        "action.upload" to "Upload",
        "action.sync" to "Sync",
        "action.export" to "Export",
        "action.import" to "Import",
        "action.save" to "Save",
        "action.cancel" to "Cancel",
        "action.close" to "Close",
        "action.refresh" to "Refresh",
        
        // Conversion
        "convert.title" to "Convert Book",
        "convert.format" to "Output Format",
        "convert.progress" to "Converting...",
        "convert.success" to "Conversion complete",
        "convert.error" to "Conversion failed: {error}",
        
        // Server
        "server.start" to "Start Server",
        "server.stop" to "Stop Server",
        "server.status.running" to "Server running on port {port}",
        "server.status.stopped" to "Server stopped",
        
        // Devices
        "device.connected" to "Device connected: {name}",
        "device.disconnected" to "Device disconnected",
        "device.sync" to "Sync to Device",
        "device.space" to "{free} of {total} free",
        
        // Search
        "search.placeholder" to "Search...",
        "search.no_results" to "No results found",
        "search.results" to "{count} results",
        
        // Settings
        "settings.title" to "Settings",
        "settings.general" to "General",
        "settings.appearance" to "Appearance",
        "settings.conversion" to "Conversion",
        "settings.server" to "Server",
        "settings.language" to "Language",
        "settings.theme" to "Theme",
        "settings.theme.light" to "Light",
        "settings.theme.dark" to "Dark",
        "settings.theme.system" to "System",
        
        // File sizes
        "size.bytes" to "{size} bytes",
        "size.kb" to "{size} KB",
        "size.mb" to "{size} MB",
        "size.gb" to "{size} GB",
        
        // Errors
        "error.generic" to "An error occurred",
        "error.file_not_found" to "File not found",
        "error.permission_denied" to "Permission denied",
        "error.invalid_format" to "Invalid format",
        "error.network" to "Network error"
    )
    
    private fun getSpanishTranslations(): Map<String, String> = mapOf(
        "library.title" to "Biblioteca",
        "library.empty" to "No hay libros en la biblioteca",
        "library.book_count.zero" to "Sin libros",
        "library.book_count.one" to "1 libro",
        "library.book_count.other" to "{count} libros",
        "library.add_book" to "Añadir Libro",
        "library.remove_book" to "Eliminar Libro",
        "library.search" to "Buscar en biblioteca...",
        
        "book.title" to "Título",
        "book.authors" to "Autores",
        "book.series" to "Serie",
        "book.tags" to "Etiquetas",
        "book.publisher" to "Editorial",
        
        "action.convert" to "Convertir",
        "action.edit" to "Editar",
        "action.delete" to "Eliminar",
        "action.download" to "Descargar",
        "action.save" to "Guardar",
        "action.cancel" to "Cancelar",
        
        "settings.language" to "Idioma"
    )
    
    private fun getGermanTranslations(): Map<String, String> = mapOf(
        "library.title" to "Bibliothek",
        "library.empty" to "Keine Bücher in der Bibliothek",
        "library.book_count.zero" to "Keine Bücher",
        "library.book_count.one" to "1 Buch",
        "library.book_count.other" to "{count} Bücher",
        "library.add_book" to "Buch hinzufügen",
        "library.remove_book" to "Buch entfernen",
        "library.search" to "Bibliothek durchsuchen...",
        
        "book.title" to "Titel",
        "book.authors" to "Autoren",
        "book.series" to "Reihe",
        "book.tags" to "Schlagwörter",
        "book.publisher" to "Verlag",
        
        "action.convert" to "Konvertieren",
        "action.edit" to "Bearbeiten",
        "action.delete" to "Löschen",
        "action.download" to "Herunterladen",
        "action.save" to "Speichern",
        "action.cancel" to "Abbrechen",
        
        "settings.language" to "Sprache"
    )
    
    private fun getFrenchTranslations(): Map<String, String> = mapOf(
        "library.title" to "Bibliothèque",
        "library.empty" to "Aucun livre dans la bibliothèque",
        "library.book_count.zero" to "Aucun livre",
        "library.book_count.one" to "1 livre",
        "library.book_count.other" to "{count} livres",
        "library.add_book" to "Ajouter un livre",
        "library.remove_book" to "Supprimer le livre",
        "library.search" to "Rechercher...",
        
        "book.title" to "Titre",
        "book.authors" to "Auteurs",
        "book.series" to "Série",
        "book.tags" to "Tags",
        "book.publisher" to "Éditeur",
        
        "action.convert" to "Convertir",
        "action.edit" to "Modifier",
        "action.delete" to "Supprimer",
        "action.download" to "Télécharger",
        "action.save" to "Enregistrer",
        "action.cancel" to "Annuler",
        
        "settings.language" to "Langue"
    )
    
    private fun getChineseTranslations(): Map<String, String> = mapOf(
        "library.title" to "书库",
        "library.empty" to "书库为空",
        "library.book_count.zero" to "无书籍",
        "library.book_count.one" to "1 本书",
        "library.book_count.other" to "{count} 本书",
        "library.add_book" to "添加书籍",
        "library.remove_book" to "删除书籍",
        "library.search" to "搜索书库...",
        
        "book.title" to "标题",
        "book.authors" to "作者",
        "book.series" to "系列",
        "book.tags" to "标签",
        "book.publisher" to "出版社",
        
        "action.convert" to "转换",
        "action.edit" to "编辑",
        "action.delete" to "删除",
        "action.download" to "下载",
        "action.save" to "保存",
        "action.cancel" to "取消",
        
        "settings.language" to "语言"
    )
    
    private fun getJapaneseTranslations(): Map<String, String> = mapOf(
        "library.title" to "ライブラリ",
        "library.empty" to "ライブラリに本がありません",
        "library.book_count.zero" to "本なし",
        "library.book_count.one" to "1冊",
        "library.book_count.other" to "{count}冊",
        "library.add_book" to "本を追加",
        "library.remove_book" to "本を削除",
        "library.search" to "検索...",
        
        "book.title" to "タイトル",
        "book.authors" to "著者",
        "book.series" to "シリーズ",
        "book.tags" to "タグ",
        "book.publisher" to "出版社",
        
        "action.convert" to "変換",
        "action.edit" to "編集",
        "action.delete" to "削除",
        "action.download" to "ダウンロード",
        "action.save" to "保存",
        "action.cancel" to "キャンセル",
        
        "settings.language" to "言語"
    )
    
    private fun getRussianTranslations(): Map<String, String> = mapOf(
        "library.title" to "Библиотека",
        "library.empty" to "В библиотеке нет книг",
        "library.book_count.zero" to "Нет книг",
        "library.book_count.one" to "1 книга",
        "library.book_count.few" to "{count} книги",
        "library.book_count.other" to "{count} книг",
        "library.add_book" to "Добавить книгу",
        "library.remove_book" to "Удалить книгу",
        "library.search" to "Поиск...",
        
        "book.title" to "Название",
        "book.authors" to "Авторы",
        "book.series" to "Серия",
        "book.tags" to "Теги",
        "book.publisher" to "Издательство",
        
        "action.convert" to "Конвертировать",
        "action.edit" to "Редактировать",
        "action.delete" to "Удалить",
        "action.download" to "Скачать",
        "action.save" to "Сохранить",
        "action.cancel" to "Отмена",
        
        "settings.language" to "Язык"
    )
    
    private fun getPortugueseTranslations(): Map<String, String> = mapOf(
        "library.title" to "Biblioteca",
        "library.empty" to "Nenhum livro na biblioteca",
        "library.book_count.zero" to "Sem livros",
        "library.book_count.one" to "1 livro",
        "library.book_count.other" to "{count} livros",
        "library.add_book" to "Adicionar Livro",
        "library.remove_book" to "Remover Livro",
        "library.search" to "Pesquisar...",
        
        "book.title" to "Título",
        "book.authors" to "Autores",
        "book.series" to "Série",
        "book.tags" to "Tags",
        "book.publisher" to "Editora",
        
        "action.convert" to "Converter",
        "action.edit" to "Editar",
        "action.delete" to "Excluir",
        "action.download" to "Baixar",
        "action.save" to "Salvar",
        "action.cancel" to "Cancelar",
        
        "settings.language" to "Idioma"
    )
    
    private fun getItalianTranslations(): Map<String, String> = mapOf(
        "library.title" to "Libreria",
        "library.empty" to "Nessun libro nella libreria",
        "library.book_count.zero" to "Nessun libro",
        "library.book_count.one" to "1 libro",
        "library.book_count.other" to "{count} libri",
        "library.add_book" to "Aggiungi Libro",
        "library.remove_book" to "Rimuovi Libro",
        "library.search" to "Cerca...",
        
        "book.title" to "Titolo",
        "book.authors" to "Autori",
        "book.series" to "Serie",
        "book.tags" to "Tag",
        "book.publisher" to "Editore",
        
        "action.convert" to "Converti",
        "action.edit" to "Modifica",
        "action.delete" to "Elimina",
        "action.download" to "Scarica",
        "action.save" to "Salva",
        "action.cancel" to "Annulla",
        
        "settings.language" to "Lingua"
    )
    
    private fun getArabicTranslations(): Map<String, String> = mapOf(
        "library.title" to "المكتبة",
        "library.empty" to "لا توجد كتب في المكتبة",
        "library.book_count.zero" to "لا كتب",
        "library.book_count.one" to "كتاب واحد",
        "library.book_count.other" to "{count} كتب",
        "library.add_book" to "إضافة كتاب",
        "library.remove_book" to "حذف كتاب",
        "library.search" to "بحث...",
        
        "book.title" to "العنوان",
        "book.authors" to "المؤلفون",
        "book.series" to "السلسلة",
        "book.tags" to "الوسوم",
        "book.publisher" to "الناشر",
        
        "action.convert" to "تحويل",
        "action.edit" to "تعديل",
        "action.delete" to "حذف",
        "action.download" to "تحميل",
        "action.save" to "حفظ",
        "action.cancel" to "إلغاء",
        
        "settings.language" to "اللغة"
    )
}
