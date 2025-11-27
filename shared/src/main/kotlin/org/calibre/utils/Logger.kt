package org.calibre.utils

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Simple logging system for Calibre Kotlin.
 * Supports multiple log levels and file output.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

object Logger {
    private var logFile: File? = null
    private var minLevel: LogLevel = LogLevel.INFO
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * Initialize logger with a log file.
     */
    fun initialize(logFile: File? = null, minLevel: LogLevel = LogLevel.INFO) {
        this.logFile = logFile
        this.minLevel = minLevel
        logFile?.parentFile?.mkdirs()
    }
    
    fun debug(message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, message, throwable)
    }
    
    fun info(message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, message, throwable)
    }
    
    fun warn(message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, message, throwable)
    }
    
    fun error(message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, message, throwable)
    }
    
    private fun log(level: LogLevel, message: String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        
        val timestamp = LocalDateTime.now().format(dateFormatter)
        val levelStr = level.name.padEnd(5)
        val logMessage = "[$timestamp] [$levelStr] $message"
        
        // Print to console
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
                // Silently fail if log file write fails
            }
        }
        
        // Also print stack trace to console for errors
        throwable?.let {
            if (level == LogLevel.ERROR) {
                it.printStackTrace()
            }
        }
    }
}
