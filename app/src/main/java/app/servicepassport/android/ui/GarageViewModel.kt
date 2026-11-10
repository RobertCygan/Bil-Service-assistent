package app.servicepassport.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.servicepassport.android.data.*
import app.servicepassport.android.dashboard.DashboardEvaluator
import app.servicepassport.android.dashboard.DashboardState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StorageProblem { READ, WRITE }
data class GarageState(
    val loading: Boolean = true,
    val snapshot: GarageSnapshot? = null,
    val saving: Boolean = false,
    val problem: StorageProblem? = null,
    val inputProblem: InputProblem? = null,
    val dashboard: DashboardState? = null,
) { val profile: VehicleEntity? get() = snapshot?.vehicle }

class GarageViewModel(private val repository: ProfileRepository, private val evaluator: DashboardEvaluator = DashboardEvaluator()) : ViewModel() {
    private val mutableState = MutableStateFlow(GarageState())
    val state: StateFlow<GarageState> = mutableState.asStateFlow()
    val today get() = repository.today
    private var observer: Job? = null
    private val day = MutableStateFlow(repository.today)
    init { reload() }
    fun clearProblem() { mutableState.update { it.copy(problem = it.problem?.takeIf { problem -> problem == StorageProblem.READ }, inputProblem = null) } }
    fun reload() {
        observer?.cancel()
        mutableState.update { it.copy(loading = true, problem = null, inputProblem = null) }
        observer = viewModelScope.launch {
            combine(repository.observeGarage(), day) { snapshot, date ->
                val dashboard = snapshot?.let {
                    try { withContext(Dispatchers.Default) { evaluator.evaluate(it, date) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: RuntimeException) { DashboardState(date, calculationUnavailable = true) }
                }
                snapshot to dashboard
            }
                .catch { mutableState.update { it.copy(loading = false, problem = StorageProblem.READ) } }
                .collect { (snapshot, dashboard) -> mutableState.update { it.copy(loading = false, snapshot = snapshot, dashboard = dashboard) } }
        }
    }
    fun refreshDate() { day.value = repository.today }
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
    fun saveInterval(input: UserIntervalInput, onSaved: () -> Unit) = mutate(onSaved) { repository.saveInterval(input) }
    fun deleteInterval(id: String, revision: Long, onSaved: () -> Unit) {
        val car = state.value.profile?.id ?: return
        mutate(onSaved) { repository.deleteInterval(car, id, revision) }
    }
    fun saveIndicator(input: IndicatorInput, onSaved: () -> Unit) = mutate(onSaved) { repository.saveIndicator(input) }
    fun deleteIndicator(id: String, onSaved: () -> Unit) {
        val car = state.value.profile?.id ?: return
        mutate(onSaved) { repository.deleteIndicator(car, id) }
    }
    class Factory(private val repository: ProfileRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GarageViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return GarageViewModel(repository) as T
        }
    }
}
