package app.servicepassport.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.servicepassport.android.data.ProfileInput
import app.servicepassport.android.data.ProfileRepository
import app.servicepassport.android.data.VehicleEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class StorageProblem { READ, WRITE }

data class GarageState(
    val loading: Boolean = true,
    val profile: VehicleEntity? = null,
    val saving: Boolean = false,
    val problem: StorageProblem? = null,
)

class GarageViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(GarageState())
    val state: StateFlow<GarageState> = mutableState.asStateFlow()
    private var observer: Job? = null

    init { reload() }

    fun reload() {
        observer?.cancel()
        mutableState.update { it.copy(loading = true, problem = null) }
        observer = viewModelScope.launch {
            repository.observeProfile()
                .catch { mutableState.update { it.copy(loading = false, problem = StorageProblem.READ) } }
                .collect { profile -> mutableState.update { it.copy(loading = false, profile = profile) } }
        }
    }

    fun save(input: ProfileInput, onSaved: () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, problem = null) }
        viewModelScope.launch {
            try {
                repository.save(input)
                mutableState.update { it.copy(saving = false) }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not expose SQL details, identifiers or raw exceptions in UI/logs.
                mutableState.update { it.copy(saving = false, problem = StorageProblem.WRITE) }
            }
        }
    }

    class Factory(private val repository: ProfileRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GarageViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return GarageViewModel(repository) as T
        }
    }
}
