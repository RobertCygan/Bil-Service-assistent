package app.servicepassport.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.servicepassport.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class StorageProblem { READ, WRITE }
data class GarageState(
    val loading: Boolean = true,
    val snapshot: GarageSnapshot? = null,
    val saving: Boolean = false,
    val problem: StorageProblem? = null,
    val inputProblem: InputProblem? = null,
) { val profile: VehicleEntity? get() = snapshot?.vehicle }

class GarageViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(GarageState())
    val state: StateFlow<GarageState> = mutableState.asStateFlow()
    val today get() = repository.today
    private var observer: Job? = null
    init { reload() }
    fun clearProblem() { mutableState.update { it.copy(problem = it.problem?.takeIf { problem -> problem == StorageProblem.READ }, inputProblem = null) } }
    fun reload() {
        observer?.cancel()
        mutableState.update { it.copy(loading = true, problem = null, inputProblem = null) }
        observer = viewModelScope.launch {
            repository.observeGarage()
                .catch { mutableState.update { it.copy(loading = false, problem = StorageProblem.READ) } }
                .collect { snapshot -> mutableState.update { it.copy(loading = false, snapshot = snapshot) } }
        }
    }
    private fun mutate(onSaved: () -> Unit, work: suspend () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, problem = null, inputProblem = null) }
        viewModelScope.launch {
            try {
                work(); mutableState.update { it.copy(saving = false) }; onSaved()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (rejected: InputRejected) { mutableState.update { it.copy(saving = false, inputProblem = rejected.problem) }
            } catch (_: Exception) { mutableState.update { it.copy(saving = false, problem = StorageProblem.WRITE) } }
        }
    }
    fun save(input: ProfileInput, onSaved: () -> Unit) = mutate(onSaved) { repository.save(input) }
    fun saveService(input: ServiceInput, onSaved: () -> Unit) = mutate(onSaved) { repository.saveService(input) }
    fun saveReading(input: ReadingInput, onSaved: () -> Unit) = mutate(onSaved) { repository.saveReading(input) }
    fun deleteService(id: String, revision: Long, onSaved: () -> Unit) {
        val car = state.value.profile?.id ?: return
        mutate(onSaved) { repository.deleteService(car, id, revision) }
    }
    fun deleteReading(id: String, revision: Long, onSaved: () -> Unit) {
        val car = state.value.profile?.id ?: return
        mutate(onSaved) { repository.deleteReading(car, id, revision) }
    }
    fun selectReading(input: CurrentReadingInput, onSaved: () -> Unit) =
        mutate(onSaved) { repository.selectCurrent(input) }
    class Factory(private val repository: ProfileRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GarageViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return GarageViewModel(repository) as T
        }
    }
}
