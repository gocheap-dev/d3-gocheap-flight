package com.gocheap.flights.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.flights.data.flight.FlightRepository
import com.gocheap.flights.data.model.Flight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val flights: List<Flight> = emptyList(),
    val isLoading: Boolean = true,
)

class FavoritesViewModel(
    private val repository: FlightRepository = FlightRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        // Re-reads the list whenever a flight is favourited or un-favourited anywhere in the app.
        viewModelScope.launch {
            repository.favoriteFlightIds().collect {
                val flights = repository.favoriteFlights()
                _state.update { current -> current.copy(flights = flights, isLoading = false) }
            }
        }
    }

    fun onRemoveFavorite(flightId: String) {
        repository.toggleFavorite(flightId)
    }
}
