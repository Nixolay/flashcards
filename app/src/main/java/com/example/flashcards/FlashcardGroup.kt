package com.example.flashcards

data class FlashcardGroup(
    var name: String,
    val cards: MutableList<Flashcard> = mutableListOf()
)