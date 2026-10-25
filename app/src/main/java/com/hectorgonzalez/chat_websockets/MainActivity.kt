package com.hectorgonzalez.chat_websockets

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var sendButton: Button
    private lateinit var messageInput: EditText
    private lateinit var serverUriInput: EditText
    private lateinit var chatMessages: TextView

    private var wsClient: MyWebSocketClient? = null

    companion object {
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val REQUEST_LOCAL_NETWORK = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Referències a les vistes
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        sendButton = findViewById(R.id.sendButton)
        messageInput = findViewById(R.id.messageInput)
        serverUriInput = findViewById(R.id.serverUriInput)
        chatMessages = findViewById(R.id.chatMessages)

        // Connectar / Desconnectar
        connectButton.setOnClickListener {
            if (wsClient == null || !wsClient!!.isOpen) {
                connectWs()
            } else {
                disconnectWs()
            }
        }

        // Enviar missatge
        sendButton.setOnClickListener {
            sendMessage()
        }

        // Enviar al prémer Enter al camp de text
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        requestLocalNetworkPermissionIfNeeded()
    }

    private fun hasLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return true
        return checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocalNetworkPermissionIfNeeded() {
        if (!hasLocalNetworkPermission()) {
            requestPermissions(arrayOf(LOCAL_NETWORK_PERMISSION), REQUEST_LOCAL_NETWORK)
        }
    }

    private fun connectWs() {
        val uriString = serverUriInput.text.toString().trim()
        if (uriString.isEmpty()) {
            appendMessage("Error: introdueix una URI del servidor.")
            return
        }
        if (!hasLocalNetworkPermission()) {
            appendMessage("Cal concedir el permís de xarxa local per connectar a $uriString")
            requestLocalNetworkPermissionIfNeeded()
            return
        }
        try {
            val uri = URI(uriString)
            wsClient = MyWebSocketClient(uri)
            wsClient!!.connect()
            appendMessage("Connectant a $uriString...")
        } catch (e: URISyntaxException) {
            appendMessage("Error: URI no vàlida: $uriString")
        }
    }

    private fun disconnectWs() {
        wsClient?.close()
        wsClient = null
        updateConnectionState(false)
        appendMessage("Desconnectat del servidor.")
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        if (wsClient == null || !wsClient!!.isOpen) {
            appendMessage("No conectat. No es pot enviar.")
            return
        }
        wsClient!!.send(text)
        messageInput.setText("")
    }

    private fun updateConnectionState(connected: Boolean) {
        runOnUiThread {
            if (connected) {
                statusText.text = getString(R.string.status_connected)
                connectButton.text = getString(R.string.disconnect)
                serverUriInput.isEnabled = false
            } else {
                statusText.text = getString(R.string.status_disconnected)
                connectButton.text = getString(R.string.connect)
                serverUriInput.isEnabled = true
            }
        }
    }

    private fun appendMessage(message: String) {
        runOnUiThread {
            val current = chatMessages.text.toString()
            chatMessages.text = if (current.isEmpty()) message else "$current\n$message"
        }
    }

    inner class MyWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake?) {
            updateConnectionState(true)
            appendMessage("Connectat al servidor WebSocket")
        }

        override fun onMessage(message: String?) {
            if (message != null) {
                appendMessage("$message")
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            updateConnectionState(false)
            appendMessage("Connexió tancada (codi $code): $reason")
        }

        override fun onError(ex: Exception?) {
            appendMessage("Error: ${ex?.message ?: "desconegut"}")
            ex?.printStackTrace()
            updateConnectionState(false)
        }
    }
}
