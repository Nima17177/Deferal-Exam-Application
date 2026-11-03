package com.example.duckduckdefer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FeedbackUiState(
    val category: String = "General",
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null
)

class FeedbackViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun onCategoryChanged(category: String) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun onRatingChanged(rating: Int) {
        _uiState.value = _uiState.value.copy(rating = rating)
    }

    fun onTitleChanged(title: String) {
        if (title.length <= 100) {
            _uiState.value = _uiState.value.copy(title = title)
        }
    }

    fun onBodyChanged(body: String) {
        if (body.length <= 1000) {
            _uiState.value = _uiState.value.copy(body = body)
        }
    }

    fun submitFeedback(userId: String, userName: String) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter a title for your feedback.")
            return
        }
        if (state.body.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please describe your feedback.")
            return
        }
        if (state.rating == 0) {
            _uiState.value = state.copy(errorMessage = "Please select a rating.")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val feedbackData = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "category" to state.category,
                    "rating" to state.rating,
                    "title" to state.title,
                    "body" to state.body,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to "new"
                )
                db.collection("Feedback").add(feedbackData).await()
                _uiState.value = _uiState.value.copy(isSubmitting = false, isSubmitted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Failed to submit feedback. Please try again."
                )
            }
        }
    }

    fun resetForm() {
        _uiState.value = FeedbackUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

