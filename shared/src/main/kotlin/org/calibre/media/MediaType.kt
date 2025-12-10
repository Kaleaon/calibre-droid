package org.calibre.media

/**
 * Enumeration of all supported media types in the unified media library.
 * Modeled after Plex, Jellyfin, and other media management systems.
 */
enum class MediaType(
    val displayName: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>
) {
    // Ebook formats
    EBOOK(
        displayName = "eBook",
        extensions = setOf("epub", "mobi", "azw", "azw3", "pdf", "txt", "rtf", "html", "htm",
            "lit", "lrf", "pdb", "fb2", "djvu", "djv", "chm", "cbz", "cbr"),
        mimeTypes = setOf("application/epub+zip", "application/x-mobipocket-ebook",
            "application/pdf", "text/plain", "text/html")
    ),
    
    // Comic book formats
    COMIC(
        displayName = "Comic",
        extensions = setOf("cbz", "cbr", "cb7", "cbt", "cba", "pdf"),
        mimeTypes = setOf("application/x-cbz", "application/x-cbr", "application/x-cb7")
    ),
    
    // Magazine/Periodical formats
    MAGAZINE(
        displayName = "Magazine",
        extensions = setOf("pdf", "epub", "mobi"),
        mimeTypes = setOf("application/pdf", "application/epub+zip")
    ),
    
    // Movie formats
    MOVIE(
        displayName = "Movie",
        extensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            "mpeg", "mpg", "3gp", "3g2", "ogv", "ts", "mts", "m2ts", "vob", "divx"),
        mimeTypes = setOf("video/mp4", "video/x-matroska", "video/avi", "video/quicktime",
            "video/x-ms-wmv", "video/webm", "video/mpeg")
    ),
    
    // TV Show formats (same as movies, differentiated by metadata)
    TV_SHOW(
        displayName = "TV Show",
        extensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            "mpeg", "mpg", "ts", "mts", "m2ts"),
        mimeTypes = setOf("video/mp4", "video/x-matroska", "video/avi")
    ),
    
    // Music formats
    MUSIC(
        displayName = "Music",
        extensions = setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma",
            "aiff", "alac", "ape", "dsf", "dff", "wv", "mka"),
        mimeTypes = setOf("audio/mpeg", "audio/flac", "audio/aac", "audio/mp4",
            "audio/ogg", "audio/wav", "audio/x-ms-wma")
    ),
    
    // Audiobook formats
    AUDIOBOOK(
        displayName = "Audiobook",
        extensions = setOf("mp3", "m4b", "m4a", "aax", "aa", "flac", "ogg", "opus"),
        mimeTypes = setOf("audio/mpeg", "audio/mp4", "audio/x-m4b", "audio/flac")
    ),
    
    // Podcast formats
    PODCAST(
        displayName = "Podcast",
        extensions = setOf("mp3", "m4a", "ogg", "opus"),
        mimeTypes = setOf("audio/mpeg", "audio/mp4", "audio/ogg")
    ),
    
    // Photo/Image formats
    PHOTO(
        displayName = "Photo",
        extensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif",
            "heic", "heif", "raw", "cr2", "nef", "arw", "dng", "svg"),
        mimeTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp",
            "image/tiff", "image/heic", "image/svg+xml")
    ),
    
    // Document formats
    DOCUMENT(
        displayName = "Document",
        extensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "odt", "ods", "odp", "rtf", "txt", "md", "csv", "tsv"),
        mimeTypes = setOf("application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ),
    
    // Unknown/Other
    UNKNOWN(
        displayName = "Unknown",
        extensions = emptySet(),
        mimeTypes = emptySet()
    );
    
    companion object {
        /**
         * Detect media type from file extension.
         */
        fun fromExtension(extension: String): MediaType {
            val ext = extension.lowercase().trimStart('.')
            
            // Check each type's extensions
            return values().firstOrNull { type ->
                ext in type.extensions
            } ?: UNKNOWN
        }
        
        /**
         * Detect media type from MIME type.
         */
        fun fromMimeType(mimeType: String): MediaType {
            val mime = mimeType.lowercase()
            
            return values().firstOrNull { type ->
                mime in type.mimeTypes
            } ?: when {
                mime.startsWith("video/") -> MOVIE
                mime.startsWith("audio/") -> MUSIC
                mime.startsWith("image/") -> PHOTO
                mime.startsWith("text/") -> DOCUMENT
                else -> UNKNOWN
            }
        }
        
        /**
         * Get all video-related types.
         */
        fun videoTypes() = listOf(MOVIE, TV_SHOW)
        
        /**
         * Get all audio-related types.
         */
        fun audioTypes() = listOf(MUSIC, AUDIOBOOK, PODCAST)
        
        /**
         * Get all reading-related types.
         */
        fun readingTypes() = listOf(EBOOK, COMIC, MAGAZINE, DOCUMENT)
    }
}
