package com.bubble.qwenchat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var setupScreen: LinearLayout
    private lateinit var chatScreen: LinearLayout
    private lateinit var installButton: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var downloadStatus: TextView
    private lateinit var modelStatus: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private lateinit var messagesView: RecyclerView

    private lateinit var engine: InferenceEngine
    private lateinit var store: ConversationStore
    private lateinit var adapter: MessageAdapter
    private var generationJob: Job? = null
    private var modelReady = false
    private var historyInjected = false

    private val messages = mutableListOf<Message>()

    private val modelsDir by lazy { File(filesDir, "models").apply { mkdirs() } }
    private val modelFile by lazy { File(modelsDir, MODEL_FILENAME) }
    private val partialFile by lazy { File(modelsDir, "$MODEL_FILENAME.part") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        store = ConversationStore(this)
        messages.addAll(store.load())
        adapter = MessageAdapter(messages)
        messagesView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesView.adapter = adapter
        if (messages.isNotEmpty()) messagesView.scrollToPosition(messages.lastIndex)

        installButton.setOnClickListener { downloadAndInstall() }
        sendButton.setOnClickListener { sendMessage() }

        if (partialFile.exists() && !modelFile.exists()) {
            installButton.text = "CONTINUAR DOWNLOAD"
            val mb = partialFile.length() / (1024.0 * 1024.0)
            downloadStatus.text = "Download parcial encontrado: %.0f MB".format(mb)
        }

        if (modelFile.exists()) {
            showChat()
            initEngineAndLoad()
        } else {
            showSetup()
        }
    }

    private fun bindViews() {
        setupScreen = findViewById(R.id.setup_screen)
        chatScreen = findViewById(R.id.chat_screen)
        installButton = findViewById(R.id.install_button)
        progress = findViewById(R.id.download_progress)
        downloadStatus = findViewById(R.id.download_status)
        modelStatus = findViewById(R.id.model_status)
        input = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        messagesView = findViewById(R.id.messages)
    }

    private fun showSetup() {
        setupScreen.visibility = View.VISIBLE
        chatScreen.visibility = View.GONE
    }

    private fun showChat() {
        setupScreen.visibility = View.GONE
        chatScreen.visibility = View.VISIBLE
        input.isEnabled = false
        sendButton.isEnabled = false
    }

    private fun initEngineAndLoad() {
        modelStatus.text = "carregando modelo local..."
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                engine.loadModel(modelFile.absolutePath)
                withContext(Dispatchers.Main) {
                    modelReady = true
                    input.isEnabled = true
                    sendButton.isEnabled = true
                    modelStatus.text = "online • local • sem nuvem"
                    input.requestFocus()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    modelStatus.text = "falha ao carregar"
                    Toast.makeText(
                        this@MainActivity,
                        "O modelo salvo parece inválido. Baixe novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                    modelFile.delete()
                    showSetup()
                    installButton.isEnabled = true
                    installButton.text = "BAIXAR MODELO NOVAMENTE"
                }
            }
        }
    }

    private fun downloadAndInstall() {
        installButton.isEnabled = false
        progress.progress = 0
        downloadStatus.text = "Conectando ao Hugging Face..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val existing = partialFile.takeIf { it.exists() }?.length() ?: 0L
                var connection = openConnection(existing)
                var response = connection.responseCode

                var append = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL
                if (existing > 0L && !append) {
                    connection.disconnect()
                    partialFile.delete()
                    connection = openConnection(0L)
                    response = connection.responseCode
                }

                if (response !in 200..299) {
                    throw IllegalStateException("HTTP $response")
                }

                val base = if (append) existing else 0L
                val bodyLength = connection.contentLengthLong.coerceAtLeast(0L)
                val total = if (bodyLength > 0L) base + bodyLength else EXPECTED_MODEL_BYTES

                BufferedInputStream(connection.inputStream, 1024 * 1024).use { inputStream ->
                    FileOutputStream(partialFile, append).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var downloaded = base
                        var lastUi = 0L
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (downloaded - lastUi >= 4L * 1024L * 1024L || downloaded == total) {
                                lastUi = downloaded
                                val percent = ((downloaded.toDouble() / total) * 100.0)
                                    .coerceIn(0.0, 100.0)
                                    .roundToInt()
                                val doneMb = downloaded / (1024.0 * 1024.0)
                                val totalMb = total / (1024.0 * 1024.0)
                                withContext(Dispatchers.Main) {
                                    progress.progress = percent
                                    downloadStatus.text = "%d%% • %.0f / %.0f MB".format(percent, doneMb, totalMb)
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                connection.disconnect()

                if (partialFile.length() < MIN_VALID_MODEL_BYTES) {
                    throw IllegalStateException("Arquivo baixado menor que o esperado")
                }

                if (modelFile.exists()) modelFile.delete()
                if (!partialFile.renameTo(modelFile)) {
                    partialFile.copyTo(modelFile, overwrite = true)
                    partialFile.delete()
                }

                withContext(Dispatchers.Main) {
                    progress.progress = 100
                    downloadStatus.text = "Instalado. Preparando conversa..."
                    showChat()
                    initEngineAndLoad()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    installButton.isEnabled = true
                    installButton.text = "TENTAR NOVAMENTE"
                    downloadStatus.text = "Falha: ${t.message ?: "erro de download"}"
                }
            }
        }
    }

    private fun openConnection(resumeFrom: Long): HttpURLConnection {
        return (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "QwenLocalChat/0.1")
            if (resumeFrom > 0L) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            connect()
        }
    }

    private fun sendMessage() {
        if (!modelReady || generationJob?.isActive == true) return
        val userText = input.text.toString().trim()
        if (userText.isEmpty()) return

        val priorHistory = messages.toList()
        input.text?.clear()
        messages.add(Message(userText, true))
        messages.add(Message("", false))
        adapter.notifyItemRangeInserted(messages.size - 2, 2)
        messagesView.scrollToPosition(messages.lastIndex)
        input.isEnabled = false
        sendButton.isEnabled = false
        store.save(messages.filterNot { !it.isUser && it.text.isBlank() })

        val promptForEngine = if (!historyInjected && priorHistory.isNotEmpty()) {
            historyInjected = true
            buildWarmStartPrompt(priorHistory, userText)
        } else {
            userText
        }

        val assistantIndex = messages.lastIndex
        val builder = StringBuilder()
        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine.sendUserPrompt(promptForEngine)
                    .onCompletion {
                        withContext(Dispatchers.Main) {
                            input.isEnabled = true
                            sendButton.isEnabled = true
                            store.save(messages)
                        }
                    }
                    .collect { token ->
                        builder.append(token)
                        withContext(Dispatchers.Main) {
                            if (assistantIndex < messages.size) {
                                messages[assistantIndex] = Message(builder.toString(), false)
                                adapter.notifyItemChanged(assistantIndex)
                                messagesView.scrollToPosition(messages.lastIndex)
                            }
                        }
                    }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    messages[assistantIndex] = Message("Erro local: ${t.message ?: "falha na geração"}", false)
                    adapter.notifyItemChanged(assistantIndex)
                    input.isEnabled = true
                    sendButton.isEnabled = true
                    store.save(messages)
                }
            }
        }
    }

    private fun buildWarmStartPrompt(history: List<Message>, newMessage: String): String {
        val recent = history.takeLast(12)
        val transcript = buildString {
            append("Continue esta conversa anterior mantendo o contexto. Não comente que recebeu um resumo.\n\n")
            recent.forEach { msg ->
                append(if (msg.isUser) "Usuário: " else "Assistente: ")
                append(msg.text.take(1800))
                append('\n')
            }
            append("\nUsuário: ")
            append(newMessage)
        }
        return transcript.takeLast(7000)
    }

    override fun onDestroy() {
        generationJob?.cancel()
        if (::engine.isInitialized) {
            runCatching { engine.destroy() }
        }
        super.onDestroy()
    }

    companion object {
        private const val MODEL_FILENAME = "qwen2.5-3b-instruct-q4_k_m.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf?download=true"

        private const val EXPECTED_MODEL_BYTES = 2_100_000_000L
        private const val MIN_VALID_MODEL_BYTES = 1_900_000_000L
    }
}
