package com.example.flashcards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardAdapter(
    private var cards: List<Flashcard>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (Int) -> Unit
) : RecyclerView.Adapter<CardAdapter.ViewHolder>() {

    fun updateCards(newCards: List<Flashcard>) {
        cards = newCards
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frontText: TextView = view.findViewById(R.id.textFront)
        val backText: TextView = view.findViewById(R.id.textBack)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flashcard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]

        if (card.isFlipped) {
            holder.frontText.visibility = View.GONE
            holder.backText.visibility = View.VISIBLE
            holder.backText.text = card.back
        } else {
            holder.frontText.visibility = View.VISIBLE
            holder.backText.visibility = View.GONE
            holder.frontText.text = card.front
        }

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