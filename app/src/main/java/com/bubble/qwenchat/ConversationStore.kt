package com.bubble.qwenchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ConversationStore(context: Context) {
    private val file = File(context.filesDir, "conversation.json")

    fun load(): MutableList<Message> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(file.readText())
            MutableList(arr.length()) { index ->
                val obj = arr.getJSONObject(index)
                Message(
                    text = obj.getString("text"),
                    isUser = obj.getBoolean("isUser")
                )
            }
        }.getOrElse { mutableListOf() }
    }

    fun save(messages: List<Message>) {
        val arr = JSONArray()
        messages.takeLast(100).forEach { message ->
            arr.put(
                JSONObject()
                    .put("text", message.text)
                    .put("isUser", message.isUser)
            )
        }
        file.writeText(arr.toString())
    }
}
