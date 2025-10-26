package com.example.flashcards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class CardAdapter(
    private var cards: List<Flashcard>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (Int) -> Unit
) : RecyclerView.Adapter<CardAdapter.ViewHolder>() {

    private lateinit var markwon: Markwon

    fun updateCards(newCards: List<Flashcard>) {
        cards = newCards
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textContent: TextView = view.findViewById(R.id.textCardContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flashcard, parent, false)

        if (!::markwon.isInitialized) {
            markwon = Markwon.create(parent.context)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]

        // Устанавливаем текст в зависимости от состояния переворота
        val text = if (card.isFlipped) card.back else card.front
        markwon.setMarkdown(holder.textContent, text)

        // Убеждаемся, что TextView не перехватывает клики
        holder.textContent.isClickable = false
        holder.textContent.isFocusable = false
        holder.textContent.isFocusableInTouchMode = false
        holder.textContent.movementMethod = null

        // Настраиваем обработчики кликов для всей карточки
        holder.itemView.setOnClickListener {
            onCardClick(position)
        }

        holder.itemView.setOnLongClickListener {
            onCardLongClick(position)
            true
        }
    }

    override fun getItemCount() = cards.size
}