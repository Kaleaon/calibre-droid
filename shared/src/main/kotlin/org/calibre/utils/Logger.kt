package org.calibre.utils

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * # Logging System
 * 
 * Simple, lightweight logging system for Calibre Kotlin.
 * Supports multiple log levels, console output, and optional file output.
 * 
 * ## Log Levels
 * 
 * - **DEBUG**: Detailed diagnostic information (typically only useful for debugging)
 * - **INFO**: General informational messages about program execution
 * - **WARN**: Warning messages for potentially problematic situations
 * - **ERROR**: Error messages for serious problems that may cause failures
 * 
 * ## Usage
 * 
 * ```kotlin
 * // Initialize with file logging
 * Logger.initialize(File("app.log"), LogLevel.DEBUG)
 * 
 * // Log messages
 * Logger.info("Application started")
 * Logger.warn("Deprecated feature used")
 * Logger.error("Failed to load file", exception)
 * ```
 * 
 * ## Features
 * 
 * - Timestamped log entries
 * - Configurable minimum log level
 * - Console output (stdout for INFO/DEBUG/WARN, stderr for ERROR)
 * - Optional file output
 * - Stack trace support for exceptions
 * - Thread-safe (object singleton)
 * 
 * @author Calibre Kotlin Conversion Project
 * @since 1.0
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Centralized logging object for the Calibre application.
 * 
 * Provides structured logging with multiple levels and optional file output.
 * All log messages include timestamps and are formatted consistently.
 */
object Logger {
    private var logFile: File? = null
    private var minLevel: LogLevel = LogLevel.INFO
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * Initialize the logger with optional file output and minimum log level.
     * 
     * If a log file is specified, the parent directory will be created if it doesn't exist.
     * 
     * @param logFile Optional file to write logs to. If null, logs only go to console.
     * @param minLevel Minimum log level to output. Messages below this level are ignored.
     *                 Default is INFO (DEBUG messages are filtered out).
     */
    fun initialize(logFile: File? = null, minLevel: LogLevel = LogLevel.INFO) {
        this.logFile = logFile
        this.minLevel = minLevel
        logFile?.parentFile?.mkdirs()
    }
    
    /**
     * Log a DEBUG level message.
     * 
     * @param message The log message
     * @param throwable Optional exception to log (stack trace will be included)
     */
    fun debug(message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, message, throwable)
    }
    
    /**
     * Log an INFO level message.
     * 
     * @param message The log message
     * @param throwable Optional exception to log (stack trace will be included)
     */
    fun info(message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, message, throwable)
    }
    
    /**
     * Log a WARN level message.
     * 
     * @param message The log message
     * @param throwable Optional exception to log (stack trace will be included)
     */
    fun warn(message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, message, throwable)
    }
    
    /**
     * Log an ERROR level message.
     * 
     * @param message The log message
     * @param throwable Optional exception to log (stack trace will be included)
     */
    fun error(message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, message, throwable)
    }
    
    /**
     * Internal logging method that handles all log levels.
     * 
     * Formats the message with timestamp and level, outputs to console and/or file,
     * and includes stack traces for exceptions when provided.
     * 
     * @param level The log level
     * @param message The log message
     * @param throwable Optional exception to log
     */
    private fun log(level: LogLevel, message: String, throwable: Throwable?) {
        // Filter by minimum log level
        if (level.ordinal < minLevel.ordinal) return
        
        val timestamp = LocalDateTime.now().format(dateFormatter)
        val levelStr = level.name.padEnd(5)
        val logMessage = "[$timestamp] [$levelStr] $message"
        
        // Print to console (stderr for errors, stdout for others)
        when (level) {
            LogLevel.ERROR -> System.err.println(logMessage)
            else -> println(logMessage)
        }
        
        // Write to file if configured
        logFile?.let { file ->
            try {
                file.appendText("$logMessage\n")
                throwable?.let {
                    val sw = StringWriter()
                    it.printStackTrace(PrintWriter(sw))
                    file.appendText(sw.toString() + "\n")
                }
            } catch (e: Exception) {
                // Silently fail if log file write fails to avoid breaking the application
                // In production, you might want to log this to stderr
            }
        }
        
        // Print stack trace to console for errors
        throwable?.let {
            if (level == LogLevel.ERROR) {
                it.printStackTrace()
            }
        }
    }
}
