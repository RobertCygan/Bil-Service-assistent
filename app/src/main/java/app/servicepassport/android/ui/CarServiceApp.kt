package app.servicepassport.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import app.servicepassport.android.R
import app.servicepassport.android.data.*
import java.time.Year

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarServiceApp(repository: ProfileRepository) {
    val model: GarageViewModel = viewModel(factory = GarageViewModel.Factory(repository))
    val state by model.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "overview"
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(
        primary = Color(0xFF285A4B), secondary = Color(0xFF506857), background = Color(0xFFF8F9F5),
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
            bottomBar = {
                NavigationBar {
                    listOf(
                        Triple("overview", R.string.overview, Icons.Default.Home),
                        Triple("history", R.string.history, Icons.Default.List),
                        Triple("settings", R.string.settings, Icons.Default.Settings),
                    ).forEach { (target, title, icon) ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav_$target"),
                            selected = route == target,
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(title)) },
                            onClick = {
                                nav.navigate(target) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "overview", modifier = Modifier.padding(padding)) {
                composable("overview") { Overview(state, model::reload) { nav.navigate("vehicle") } }
                composable("history") { History() }
                composable("settings") { Settings() }
                composable("vehicle") {
                    ProfileEditor(state) { input -> model.save(input) { nav.popBackStack() } }
                }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

@Composable
private fun Overview(state: GarageState, retry: () -> Unit, editProfile: () -> Unit) = Page {
    when {
        state.loading -> CircularProgressIndicator(Modifier.testTag("loading"))
        state.problem == StorageProblem.READ -> {
            Text(stringResource(R.string.read_error))
            Button(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
        state.profile == null -> {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.welcome_body))
            LanguageSelector()
            Button(onClick = editProfile, modifier = Modifier.fillMaxWidth().testTag("add_car")) {
                Text(stringResource(R.string.add_car))
            }
            Text(stringResource(R.string.local_storage_note), style = MaterialTheme.typography.bodySmall)
        }
        else -> {
            val profile = requireNotNull(state.profile)
            Text(profile.displayName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("profile_name"))
            profile.modelYear?.let { Text(stringResource(R.string.model_year_value, it.toString())) }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.no_verified_schedule), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.no_schedule_body))
                }
            }
            OutlinedButton(onClick = editProfile, modifier = Modifier.fillMaxWidth().testTag("edit_car")) {
                Text(stringResource(R.string.edit_car))
            }
        }
    }
}

@Composable
private fun History() = Page {
    Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.history_empty_body))
}

@Composable
private fun Settings() = Page {
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
    LanguageSelector()
    HorizontalDivider()
    Text(stringResource(R.string.your_data), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.local_storage_note))
}

@Composable
private fun LanguageSelector() {
    val locale = LocalConfiguration.current.locales[0]
    val norwegian = locale.language == "nb" || locale.language == "no"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.language_help), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                modifier = Modifier.testTag("language_nb"),
                selected = norwegian,
                onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("nb-NO")) },
                label = { Text(stringResource(R.string.language_norwegian)) },
            )
            FilterChip(
                modifier = Modifier.testTag("language_en"),
                selected = !norwegian,
                onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) },
                label = { Text(stringResource(R.string.language_english)) },
            )
        }
    }
}

@Composable
private fun ProfileEditor(state: GarageState, save: (ProfileInput) -> Unit) = Page {
    var name by rememberSaveable(state.profile?.id) { mutableStateOf(state.profile?.displayName.orEmpty()) }
    var year by rememberSaveable(state.profile?.id) { mutableStateOf(state.profile?.modelYear?.toString().orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val problem = if (submitted) ProfileValidation.problem(name, year, Year.now().value) else null
    Text(stringResource(R.string.car_details), style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.car_name)) },
        singleLine = true,
        isError = problem in setOf(ProfileProblem.NAME_REQUIRED, ProfileProblem.NAME_TOO_LONG),
        modifier = Modifier.fillMaxWidth().testTag("car_name"),
    )
    OutlinedTextField(
        value = year,
        onValueChange = { year = it },
        label = { Text(stringResource(R.string.model_year_optional)) },
        supportingText = { Text(stringResource(R.string.model_year_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = problem == ProfileProblem.INVALID_YEAR,
        modifier = Modifier.fillMaxWidth().testTag("model_year"),
    )
    if (problem != null) {
        Text(
            stringResource(when (problem) {
                ProfileProblem.NAME_REQUIRED -> R.string.name_required
                ProfileProblem.NAME_TOO_LONG -> R.string.name_too_long
                ProfileProblem.INVALID_YEAR -> R.string.invalid_year
            }),
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.problem == StorageProblem.WRITE) Text(stringResource(R.string.write_error), color = MaterialTheme.colorScheme.error)
    Button(
        enabled = !state.saving,
        onClick = {
            submitted = true
            if (ProfileValidation.problem(name, year, Year.now().value) == null) save(ProfileInput(name.trim(), year.toIntOrNull()))
        },
        modifier = Modifier.fillMaxWidth().testTag("save_car"),
    ) { Text(stringResource(if (state.saving) R.string.saving else R.string.save_car)) }
}
