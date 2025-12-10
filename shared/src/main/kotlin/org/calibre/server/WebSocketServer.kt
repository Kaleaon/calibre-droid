package org.calibre.server

import org.calibre.metadata.Library
import org.calibre.metadata.Metadata
import org.calibre.utils.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.experimental.xor

/**
 * WebSocket server for real-time library updates.
 * 
 * Features:
 * - Real-time book addition/removal notifications
 * - Reading progress synchronization
 * - Library change notifications
 * - Client state management
 * 
 * Protocol:
 * - Uses JSON messages
 * - Supports multiple clients
 * - Heartbeat for connection monitoring
 * 
 * Message types:
 * - book_added: New book added to library
 * - book_removed: Book removed from library
 * - book_updated: Book metadata changed
 * - reading_progress: Reading progress update
 * - library_sync: Full library sync request/response
 */
class WebSocketServer(
    private val library: Library,
    private val port: Int = 8081
) {
    
    private val clients = ConcurrentHashMap<String, WebSocketClient>()
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    
    /**
     * Starts the WebSocket server.
     */
    fun start() {
        if (isRunning) return
        
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Logger.info("WebSocket server started on port $port")
            
            executor.submit {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleConnection(clientSocket)
                    } catch (e: IOException) {
                        if (isRunning) {
                            Logger.error("WebSocket accept error: ${e.message}")
                        }
                    }
                }
            }
            
            // Start heartbeat thread
            executor.submit { heartbeatLoop() }
            
        } catch (e: Exception) {
            Logger.error("Failed to start WebSocket server: ${e.message}")
        }
    }
    
    /**
     * Stops the WebSocket server.
     */
    fun stop() {
        isRunning = false
        
        for (client in clients.values) {
            try {
                client.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        clients.clear()
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        
        executor.shutdownNow()
        Logger.info("WebSocket server stopped")
    }
    
    /**
     * Broadcasts a message to all connected clients.
     */
    fun broadcast(message: String) {
        val frame = createTextFrame(message)
        for (client in clients.values) {
            try {
                client.send(frame)
            } catch (e: Exception) {
                Logger.debug("Failed to send to client ${client.id}: ${e.message}")
            }
        }
    }
    
    /**
     * Notifies clients about a new book.
     */
    fun notifyBookAdded(book: Metadata) {
        val message = """{"type":"book_added","data":${bookToJson(book)}}"""
        broadcast(message)
    }
    
    /**
     * Notifies clients about a removed book.
     */
    fun notifyBookRemoved(bookId: Int) {
        val message = """{"type":"book_removed","data":{"id":$bookId}}"""
        broadcast(message)
    }
    
    /**
     * Notifies clients about book metadata update.
     */
    fun notifyBookUpdated(book: Metadata) {
        val message = """{"type":"book_updated","data":${bookToJson(book)}}"""
        broadcast(message)
    }
    
    /**
     * Notifies clients about reading progress update.
     */
    fun notifyReadingProgress(bookId: Int, progress: Double, position: String?) {
        val posJson = position?.let { "\"$it\"" } ?: "null"
        val message = """{"type":"reading_progress","data":{"bookId":$bookId,"progress":$progress,"position":$posJson}}"""
        broadcast(message)
    }
    
    private fun handleConnection(socket: Socket) {
        executor.submit {
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                
                // Perform WebSocket handshake
                if (performHandshake(input, output)) {
                    val clientId = UUID.randomUUID().toString()
                    val client = WebSocketClient(clientId, socket, input, output)
                    clients[clientId] = client
                    
                    Logger.info("WebSocket client connected: $clientId")
                    
                    // Send welcome message
                    val welcome = """{"type":"connected","data":{"clientId":"$clientId"}}"""
                    client.send(createTextFrame(welcome))
                    
                    // Read messages from client
                    handleClientMessages(client)
                    
                    clients.remove(clientId)
                    Logger.info("WebSocket client disconnected: $clientId")
                }
            } catch (e: Exception) {
                Logger.debug("WebSocket connection error: ${e.message}")
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    private fun performHandshake(input: InputStream, output: OutputStream): Boolean {
        val reader = input.bufferedReader()
        val headers = mutableMapOf<String, String>()
        
        // Read HTTP headers
        var line = reader.readLine()
        if (line == null || !line.startsWith("GET")) {
            return false
        }
        
        while (true) {
            line = reader.readLine()
            if (line.isNullOrEmpty()) break
            
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key.lowercase()] = value
            }
        }
        
        // Verify WebSocket upgrade request
        val upgrade = headers["upgrade"]?.lowercase()
        val connection = headers["connection"]?.lowercase()
        val key = headers["sec-websocket-key"]
        
        if (upgrade != "websocket" || connection?.contains("upgrade") != true || key == null) {
            return false
        }
        
        // Generate accept key
        val acceptKey = generateAcceptKey(key)
        
        // Send handshake response
        val response = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: $acceptKey\r\n")
            append("\r\n")
        }
        
        output.write(response.toByteArray())
        output.flush()
        
        return true
    }
    
    private fun generateAcceptKey(key: String): String {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest((key + magic).toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
    
    private fun handleClientMessages(client: WebSocketClient) {
        while (isRunning && !client.isClosed) {
            try {
                val frame = readFrame(client.input)
                if (frame == null) {
                    break
                }
                
                when (frame.opcode) {
                    OPCODE_TEXT -> handleTextMessage(client, frame.payload)
                    OPCODE_PING -> {
                        // Respond with pong
                        client.send(createFrame(OPCODE_PONG, frame.payload))
                    }
                    OPCODE_PONG -> {
                        // Ignore pong
                    }
                    OPCODE_CLOSE -> {
                        // Close connection
                        client.send(createFrame(OPCODE_CLOSE, ByteArray(0)))
                        break
                    }
                }
            } catch (e: Exception) {
                Logger.debug("Error reading WebSocket frame: ${e.message}")
                break
            }
        }
    }
    
    private fun handleTextMessage(client: WebSocketClient, payload: ByteArray) {
        val message = String(payload)
        Logger.debug("WebSocket message from ${client.id}: $message")
        
        try {
            // Parse JSON message (simplified parsing)
            when {
                message.contains("\"type\":\"sync\"") -> {
                    // Send full library
                    val books = library.getAllBooks()
                    val booksJson = books.joinToString(",") { bookToJson(it) }
                    val response = """{"type":"library_sync","data":{"books":[$booksJson]}}"""
                    client.send(createTextFrame(response))
                }
                message.contains("\"type\":\"reading_progress\"") -> {
                    // Handle reading progress update from client
                    // Extract book ID and progress, update library
                    Logger.debug("Received reading progress update")
                }
                message.contains("\"type\":\"ping\"") -> {
                    val response = """{"type":"pong","data":{}}"""
                    client.send(createTextFrame(response))
                }
            }
        } catch (e: Exception) {
            Logger.warn("Error handling WebSocket message: ${e.message}")
        }
    }
    
    private fun heartbeatLoop() {
        while (isRunning) {
            try {
                Thread.sleep(30000) // 30 seconds
                
                val ping = createFrame(OPCODE_PING, "ping".toByteArray())
                for (client in clients.values) {
                    try {
                        client.send(ping)
                    } catch (e: Exception) {
                        // Client disconnected
                        client.close()
                        clients.remove(client.id)
                    }
                }
            } catch (e: InterruptedException) {
                break
            }
        }
    }
    
    private fun readFrame(input: InputStream): WebSocketFrame? {
        val header = ByteArray(2)
        if (input.read(header) != 2) return null
        
        val fin = (header[0].toInt() and 0x80) != 0
        val opcode = header[0].toInt() and 0x0F
        val masked = (header[1].toInt() and 0x80) != 0
        var payloadLength = header[1].toInt() and 0x7F
        
        // Extended payload length
        when (payloadLength) {
            126 -> {
                val extended = ByteArray(2)
                input.read(extended)
                payloadLength = ((extended[0].toInt() and 0xFF) shl 8) or (extended[1].toInt() and 0xFF)
            }
            127 -> {
                val extended = ByteArray(8)
                input.read(extended)
                // Simplified: only handle lengths that fit in Int
                payloadLength = ((extended[6].toInt() and 0xFF) shl 8) or (extended[7].toInt() and 0xFF)
            }
        }
        
        // Masking key (if masked)
        val maskingKey = if (masked) {
            val key = ByteArray(4)
            input.read(key)
            key
        } else null
        
        // Payload
        val payload = ByteArray(payloadLength)
        var bytesRead = 0
        while (bytesRead < payloadLength) {
            val count = input.read(payload, bytesRead, payloadLength - bytesRead)
            if (count < 0) return null
            bytesRead += count
        }
        
        // Unmask if needed
        if (maskingKey != null) {
            for (i in payload.indices) {
                payload[i] = payload[i] xor maskingKey[i % 4]
            }
        }
        
        return WebSocketFrame(opcode, payload)
    }
    
    private fun createTextFrame(message: String): ByteArray {
        return createFrame(OPCODE_TEXT, message.toByteArray())
    }
    
    private fun createFrame(opcode: Int, payload: ByteArray): ByteArray {
        val payloadLength = payload.size
        
        val header = when {
            payloadLength <= 125 -> {
                byteArrayOf(
                    (0x80 or opcode).toByte(),
                    payloadLength.toByte()
                )
            }
            payloadLength <= 65535 -> {
                byteArrayOf(
                    (0x80 or opcode).toByte(),
                    126.toByte(),
                    (payloadLength shr 8).toByte(),
                    payloadLength.toByte()
                )
            }
            else -> {
                byteArrayOf(
                    (0x80 or opcode).toByte(),
                    127.toByte(),
                    0, 0, 0, 0,
                    (payloadLength shr 24).toByte(),
                    (payloadLength shr 16).toByte(),
                    (payloadLength shr 8).toByte(),
                    payloadLength.toByte()
                )
            }
        }
        
        return header + payload
    }
    
    private fun bookToJson(book: Metadata): String {
        val authors = book.authors.joinToString("\",\"") { escapeJson(it) }
        val tags = book.tags.joinToString("\",\"") { escapeJson(it) }
        
        return buildString {
            append("{")
            append("\"id\":${book.id ?: 0},")
            append("\"title\":\"${escapeJson(book.title)}\",")
            append("\"authors\":[\"$authors\"],")
            append("\"tags\":[\"$tags\"]")
            book.series?.let { append(",\"series\":\"${escapeJson(it)}\"") }
            book.seriesIndex?.let { append(",\"seriesIndex\":$it") }
            book.publisher?.let { append(",\"publisher\":\"${escapeJson(it)}\"") }
            book.rating?.let { append(",\"rating\":$it") }
            append("}")
        }
    }
    
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    companion object {
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA
    }
}

/**
 * Represents a WebSocket client connection.
 */
private class WebSocketClient(
    val id: String,
    private val socket: Socket,
    val input: InputStream,
    private val output: OutputStream
) {
    var isClosed = false
        private set
    
    @Synchronized
    fun send(data: ByteArray) {
        if (!isClosed) {
            output.write(data)
            output.flush()
        }
    }
    
    fun close() {
        isClosed = true
        try {
            socket.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

/**
 * Represents a WebSocket frame.
 */
private data class WebSocketFrame(
    val opcode: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebSocketFrame) return false
        return opcode == other.opcode && payload.contentEquals(other.payload)
    }
    
    override fun hashCode(): Int {
        return 31 * opcode + payload.contentHashCode()
    }
}
