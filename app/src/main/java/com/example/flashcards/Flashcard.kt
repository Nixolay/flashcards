package com.example.flashcards

data class Flashcard(
    var front: String,
    var back: String,
    var isFlipped: Boolean = false
)