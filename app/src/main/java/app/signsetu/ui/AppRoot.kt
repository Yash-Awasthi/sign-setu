package app.signsetu.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.settings.SettingsScreen
import app.signsetu.ui.start.FirstRunScreen
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import app.signsetu.export.SessionReport
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.signsetu.AppContainer
import app.signsetu.DeviceGuard
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.OfficerPane
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.Stage
import app.signsetu.camera.HandTracker
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.signsetu.ui.start.StartScreen
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.serialization.Serializable
import app.signsetu.ui.components.GuardedAlertDialog

@Serializable data object StartKey : NavKey
@Serializable data class SessionKey(val id: String) : NavKey
@Serializable data object SettingsKey : NavKey

private const val SANDES_PACKAGE = "in.nic.gimkerala"

@Composable
fun AppRoot(container: AppContainer, guard: DeviceGuard) {
    val repo by container.sessions.collectAsStateWithLifecycle()
    val content by produceState<ContentBundle?>(null) { value = container.content.load() }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    CompositionLocalProvider(LocalContentRepository provides container.content) {
        SignSetuTheme {
            val r = repo
            val c = content
            val s = settings
            when {
                s == null -> Box(Modifier.fillMaxSize())
                !s.firstRunDone -> FirstRunScreen(c?.gestures.orEmpty(), onDone = { scope.launch { container.settings.setFirstRunDone() } })
                r == null -> LockScreen(onUnlock = { onFailed ->
                    guard.unlock(title, subtitle, { container.unlockAsync(onFailed) }, onFailed)
                })
                c == null -> Box(Modifier.fillMaxSize())
                else -> Navigation(r, c, s, guard, container)
            }
            val keyReset by container.keyReset.collectAsStateWithLifecycle()
            if (keyReset && r != null) {
                GuardedAlertDialog(
                    onDismissRequest = container::acknowledgeKeyReset,
                    title = { Text(stringResource(R.string.key_reset_title)) },
                    text = { Text(stringResource(R.string.key_reset_body)) },
                    confirmButton = { TextButton(onClick = container::acknowledgeKeyReset) { Text(stringResource(R.string.ok)) } },
                )
            }
        }
    }
}

@Composable
private fun Navigation(repo: SessionRepository, content: ContentBundle, settings: AppSettings, guard: DeviceGuard, container: AppContainer) {
    val backStack = rememberNavBackStack(StartKey)
    val scope = rememberCoroutineScope()
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<StartKey> {
                StartScreen(
                    repo = repo,
                    content = content,
                    expired = container.buildExpired,
                    rooted = container.deviceLooksRooted,
                    cameraWanted = settings.cameraEnabled,
                    onOpenSession = { id -> backStack.add(SessionKey(id)) },
                    onSettings = { backStack.add(SettingsKey) },
                )
            }
            entry<SettingsKey> {
                SettingsScreen(container.settings, onBack = { backStack.removeLastOrNull() }, onClearAll = container::clearAllData)
            }
            entry<SessionKey> { key ->
                SessionRoute(
                    key.id, repo, content, settings, guard, container.report, container.handTracker,
                    onFavourite = { id -> scope.launch { container.settings.toggleFavourite(id) } },
                    onClosed = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}

@Composable
private fun SessionRoute(
    sessionId: String,
    repo: SessionRepository,
    content: ContentBundle,
    settings: AppSettings,
    guard: DeviceGuard,
    report: SessionReport,
    tracker: HandTracker?,
    onFavourite: (String) -> Unit,
    onClosed: () -> Unit,
) {
    val officerLang = if (LocalConfiguration.current.locales[0].language == "hi") "hi" else "en"
    val vm: SessionViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory { initializer { SessionViewModel(sessionId, repo, content, officerLang, report) } },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val title = stringResource(R.string.return_prompt_title)
    val subtitle = stringResource(R.string.return_prompt_subtitle)
    val cancel = stringResource(R.string.cancel)
    // The officer can turn the mirror off for one person, for example a child upset by seeing themself.
    var cameraOn by rememberSaveable { mutableStateOf(settings.cameraEnabled) }
    val state = ui ?: return
    val backToOfficer = { guard.confirmOfficer(title, subtitle, cancel) { vm.returnToOfficer() } }

    // From the first Show until the session ends, the app stays pinned so a subject cannot reach other apps (FR-61).
    LaunchedEffect(state.mode) {
        // After the stage's first frame: pinning brings up system UI that would otherwise delay it (NFR-02).
        if (state.mode == Mode.SHOW) {
            withFrameNanos { }
            guard.pin()
        }
    }
    ImmersiveWhile(activity, state.mode == Mode.SHOW)

    when (state.mode) {
        Mode.OFFICER -> OfficerPane(
            ui = state,
            vm = vm,
            officerLang = officerLang,
            cameraOn = cameraOn,
            onCameraOn = { cameraOn = it },
            favourites = settings.favourites,
            onFavourite = onFavourite,
            onCallInterpreter = if (settings.interpreterConfigured) {
                {
                    guard.unpin()
                    activity?.let { callInterpreter(it, settings) }
                }
            } else {
                null
            },
            onShare = { file ->
                // Other apps cannot open while the app is pinned.
                guard.unpin()
                activity?.let { shareReport(it, file) }
            },
            onEndSession = {
                vm.endSession {
                    guard.unpin()
                    onClosed()
                }
            },
        )
        Mode.SHOW -> {
            BackHandler(onBack = backToOfficer)
            SubjectTheme {
                Stage(
                    ui = state,
                    content = content,
                    tracker = tracker,
                    cameraOn = cameraOn,
                    largeCaptions = settings.largeCaptions,
                    gestureSpeed = if (settings.slowGestures) 0.75f else 1f,
                    onCard = vm::answerCard,
                    onYesNo = vm::answerYesNo,
                    onLanguage = vm::chooseLanguage,
                    onDrawing = vm::submitDrawing,
                    onOfficer = backToOfficer,
                )
            }
        }
    }
}

/** Opens the messaging app at the interpreter's contact; no session data goes with it (FR-64). */
private fun callInterpreter(activity: Activity, settings: AppSettings) {
    val sandes = settings.interpreterApp == InterpreterApp.SANDES
    val name = activity.getString(if (sandes) R.string.settings_sandes else R.string.settings_whatsapp)
    val intent = if (sandes) {
        activity.packageManager.getLaunchIntentForPackage(SANDES_PACKAGE)
    } else {
        whatsappNumber(settings.interpreterContact)?.let { Intent(Intent.ACTION_VIEW, "https://wa.me/$it".toUri()) }
    }
    val opened = intent != null && runCatching { activity.startActivity(intent) }.isSuccess
    val message = when {
        !opened -> activity.getString(R.string.call_missing, name)
        // Sandes has no public link to a contact, so the officer is told whom to call.
        sandes -> activity.getString(R.string.call_sandes_hint, settings.interpreterContact)
        else -> null
    }
    message?.let { Toast.makeText(activity, it, Toast.LENGTH_LONG).show() }
}

private fun shareReport(activity: Activity, file: File) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    activity.startActivity(Intent.createChooser(send, null))
}

@Composable
private fun ImmersiveWhile(activity: Activity?, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
