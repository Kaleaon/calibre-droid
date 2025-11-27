package org.calibre.server

import org.calibre.utils.Logger
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple authentication system for the content server.
 */
data class User(
    val username: String,
    val passwordHash: String, // SHA-256 hash
    val roles: Set<String> = setOf("user")
)

class AuthenticationManager {
    private val users = ConcurrentHashMap<String, User>()
    private val sessions = ConcurrentHashMap<String, Session>() // token -> session
    
    data class Session(
        val username: String,
        val roles: Set<String>,
        val createdAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
    )
    
    init {
        // Add default admin user (password: admin)
        addUser("admin", "admin", setOf("admin", "user"))
    }
    
    /**
     * Add a new user.
     */
    fun addUser(username: String, password: String, roles: Set<String> = setOf("user")) {
        val hash = hashPassword(password)
        users[username] = User(username, hash, roles)
        Logger.info("Added user: $username")
    }
    
    /**
     * Authenticate a user and create a session.
     */
    fun authenticate(username: String, password: String): String? {
        val user = users[username] ?: return null
        
        val hash = hashPassword(password)
        if (hash != user.passwordHash) {
            Logger.warn("Failed authentication attempt for user: $username")
            return null
        }
        
        // Create session token
        val token = generateToken(username)
        sessions[token] = Session(username, user.roles)
        
        Logger.info("User authenticated: $username")
        return token
    }
    
    /**
     * Validate a session token.
     */
    fun validateSession(token: String): Session? {
        val session = sessions[token] ?: return null
        
        if (System.currentTimeMillis() > session.expiresAt) {
            sessions.remove(token)
            return null
        }
        
        return session
    }
    
    /**
     * Check if a session has a specific role.
     */
    fun hasRole(token: String, role: String): Boolean {
        val session = validateSession(token) ?: return false
        return session.roles.contains(role)
    }
    
    /**
     * Logout (invalidate session).
     */
    fun logout(token: String) {
        sessions.remove(token)
    }
    
    /**
     * Hash password using SHA-256.
     */
    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
    
    /**
     * Generate a session token.
     */
    private fun generateToken(username: String): String {
        val random = (System.currentTimeMillis().toString() + username + Math.random().toString())
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(random.toByteArray())
        return Base64.getEncoder().encodeToString(hash).take(32)
    }
    
    /**
     * Extract token from Authorization header.
     */
    fun extractToken(authHeader: String?): String? {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null
        }
        return authHeader.substring(7)
    }
}
