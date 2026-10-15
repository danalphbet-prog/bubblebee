package com.bubble.qwenchat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    private val items: MutableList<Message>
) : RecyclerView.Adapter<MessageAdapter.Holder>() {

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text.rootView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val root = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false) as FrameLayout
        return Holder(root.findViewById(R.id.message_text))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        holder.text.setBackgroundResource(
            if (item.isUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai
        )
        val lp = holder.text.layoutParams as FrameLayout.LayoutParams
        lp.gravity = if (item.isUser) Gravity.END else Gravity.START
        holder.text.layoutParams = lp
    }

    override fun getItemCount(): Int = items.size
}
