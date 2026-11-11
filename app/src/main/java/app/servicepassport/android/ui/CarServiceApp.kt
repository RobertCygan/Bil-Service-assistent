package app.servicepassport.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import app.servicepassport.android.AppLanguage
import app.servicepassport.android.R
import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.documents.DocumentAnalyzer
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.data.ProfileRepository
import app.servicepassport.android.reminders.ReminderStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarServiceApp(repository: ProfileRepository, reminderStore: ReminderStore? = null, onDataChanged: () -> Unit = {}, dataFiles: DataFiles? = null, documents: DocumentAnalyzer? = null, documentStore: DocumentStore? = null) {
    val model: GarageViewModel = viewModel(factory = GarageViewModel.Factory(repository, documentStore))
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { model.refreshDate(); delay(60_000) }
        }
    }
    LaunchedEffect(state.dashboard) { if (state.dashboard != null) onDataChanged() }
    val imports = remember { ImportSession(documentStore) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "overview"
    var dirty by remember { mutableStateOf(false) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    var pendingBack by remember { mutableStateOf(false) }
    val tabs = setOf("overview", "history", "settings")
    fun navigate(target: String) {
        model.clearProblem()
        // A prefill from a document belongs to one visit of the service form only.
        if (target != "service/new" && route == "service/new") imports.clear()
        nav.navigate(target) {
            if (target in tabs) popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
    fun go(target: String) { if (dirty) pendingRoute = target else navigate(target) }
    fun back() { if (dirty) pendingBack = true else nav.popBackStack() }
    fun saved(target: String) { dirty = false; navigate(target) }
    fun intervalSaved() { dirty = false; model.clearProblem(); if (!nav.popBackStack("intervals", false)) navigate("intervals") }
    BackHandler(enabled = dirty) { pendingBack = true }
    LaunchedEffect(route) { model.clearProblem() }
    AppTheme {
        Scaffold(
            topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                if (route !in tabs) IconButton(onClick = { back() }, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.back)) }
            }) },
            bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf(Triple("overview", R.string.overview, Icons.Default.Home), Triple("history", R.string.history, Icons.AutoMirrored.Default.List), Triple("settings", R.string.settings, Icons.Default.Settings)).forEach { (target, label, icon) ->
                    NavigationBarItem(modifier = Modifier.testTag("nav_$target"), selected = route == target, icon = { Icon(icon, contentDescription = null) }, label = { Text(stringResource(label)) }, onClick = { go(target) })
                }
            } },
        ) { padding ->
            NavHost(nav, startDestination = "overview", modifier = Modifier.padding(padding)) {
                composable("overview") { Overview(state, model.today, model::reload, ::go) }
                composable("history") { History(state, ::go) }
                composable("settings") { Settings(reminderStore, onDataChanged, dataFiles, state.snapshot != null) }
                composable("dashboard") { DashboardPage(state, ::go, model::reload) }
                composable("forecast/{id}") { node -> ForecastDetails(state, node.arguments?.getString("id").orEmpty(), ::go) }
                composable("intervals") { IntervalsPage(state, ::go) { id, revision -> model.deleteInterval(id, revision) {} } }
                composable("indicator") { IndicatorPage(state, model.today, ::go, { input -> model.saveIndicator(input) {} }) { id -> model.deleteIndicator(id) {} } }
                composable("interval/new") { IntervalEditor(state, null, { dirty = it }) { input -> model.saveInterval(input, ::intervalSaved) } }
                composable("interval/edit/{id}") { node ->
                    val interval = state.snapshot?.intervals?.firstOrNull { it.id == node.arguments?.getString("id") }
                    if (interval == null) Page { Text(stringResource(R.string.entry_not_found)) }
                    else IntervalEditor(state, interval, { dirty = it }) { input -> model.saveInterval(input, ::intervalSaved) }
                }
                composable("vehicle") { ProfileEditor(state, model.today, { dirty = it }) { input -> model.save(input) { saved("overview") } } }
                composable("service/new") {
                    ServiceEditor(state, null, model.today, { dirty = it }, imports.prefill, imports.attachment != null) { input -> model.saveService(input, imports.attachment) { imports.clear(); saved("history") } }
                }
                composable("import") { ImportPage(state, imports, documents, documentStore, model.today, ::go) }
                composable("service/edit/{id}") { node ->
                    val record = state.snapshot?.events?.firstOrNull { it.event.id == node.arguments?.getString("id") }
                    if (record == null) Page { Text(stringResource(R.string.entry_not_found)) } else ServiceEditor(state, record, model.today, { dirty = it }) { input -> model.saveService(input) { saved("history") } }
                }
                composable("service/{id}") { node -> ServiceDetails(state, node.arguments?.getString("id").orEmpty(), ::go, documentStore, { id -> model.removeDocument(id) {} }) { id, revision -> model.deleteService(id, revision) { saved("history") } } }
                composable("readings") { Readings(state, ::go, model::selectReading) { id, revision -> model.deleteReading(id, revision) {} } }
                composable("reading/new") { ReadingEditor(state, null, model.today, { dirty = it }) { input -> model.saveReading(input) { saved("overview") } } }
                composable("reading/{id}") { node ->
                    val reading = state.snapshot?.readings?.firstOrNull { it.id == node.arguments?.getString("id") && it.eventId == null }
                    if (reading == null) Page { Text(stringResource(R.string.entry_not_found)) } else ReadingEditor(state, reading, model.today, { dirty = it }) { input -> model.saveReading(input) { saved("readings") } }
                }
            }
        }
        if (pendingRoute != null || pendingBack) AlertDialog(onDismissRequest = { pendingRoute = null; pendingBack = false }, title = { Text(stringResource(R.string.unsaved_title)) }, text = { Text(stringResource(R.string.unsaved_body)) }, confirmButton = { TextButton(onClick = {
            val destination = pendingRoute; val wasBack = pendingBack
            pendingRoute = null; pendingBack = false; dirty = false
            if (wasBack) nav.popBackStack() else if (destination != null) navigate(destination)
        }, modifier = Modifier.testTag("confirm_discard")) { Text(stringResource(R.string.discard_changes)) } }, dismissButton = { TextButton(onClick = { pendingRoute = null; pendingBack = false }) { Text(stringResource(R.string.keep_editing)) } })
    }
}

@Composable internal fun LanguageSelector() {
    val locale = LocalConfiguration.current.locales[0]; val norwegian = locale.language in setOf("nb", "no")
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.language_help), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(modifier = Modifier.testTag("language_nb"), selected = norwegian, onClick = { AppLanguage.choose(context, "nb-NO") }, label = { Text(stringResource(R.string.language_norwegian)) })
            FilterChip(modifier = Modifier.testTag("language_en"), selected = !norwegian, onClick = { AppLanguage.choose(context, "en") }, label = { Text(stringResource(R.string.language_english)) })
        }
    }
}
