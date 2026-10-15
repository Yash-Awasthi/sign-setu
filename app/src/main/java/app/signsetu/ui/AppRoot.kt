package app.signsetu.ui

import android.app.Activity
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
import app.signsetu.ui.session.ShowPane
import app.signsetu.ui.start.StartScreen
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.serialization.Serializable

@Serializable data object StartKey : NavKey
@Serializable data class SessionKey(val id: String) : NavKey

@Composable
fun AppRoot(container: AppContainer, guard: DeviceGuard) {
    val repo by container.sessions.collectAsStateWithLifecycle()
    val content by produceState<ContentBundle?>(null) { value = container.content.load() }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    CompositionLocalProvider(LocalContentRepository provides container.content) {
        SignSetuTheme {
            val r = repo
            val c = content
            when {
                r == null -> LockScreen(onUnlock = { onFailed ->
                    guard.unlock(title, subtitle, { container.unlockAsync(onFailed) }, onFailed)
                })
                c == null -> Box(Modifier.fillMaxSize())
                else -> Navigation(r, c, guard)
            }
        }
    }
}

@Composable
private fun Navigation(repo: SessionRepository, content: ContentBundle, guard: DeviceGuard) {
    val backStack = rememberNavBackStack(StartKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<StartKey> {
                StartScreen(repo, content, onOpenSession = { id -> backStack.add(SessionKey(id)) })
            }
            entry<SessionKey> { key ->
                SessionRoute(key.id, repo, content, guard, onClosed = { backStack.removeLastOrNull() })
            }
        },
    )
}

@Composable
private fun SessionRoute(
    sessionId: String,
    repo: SessionRepository,
    content: ContentBundle,
    guard: DeviceGuard,
    onClosed: () -> Unit,
) {
    val officerLang = if (LocalConfiguration.current.locales[0].language == "hi") "hi" else "en"
    val vm: SessionViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory { initializer { SessionViewModel(sessionId, repo, content, officerLang) } },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val title = stringResource(R.string.return_prompt_title)
    val subtitle = stringResource(R.string.return_prompt_subtitle)
    val cancel = stringResource(R.string.cancel)
    val state = ui ?: return
    val backToOfficer = { guard.confirmOfficer(title, subtitle, cancel) { vm.returnToOfficer() } }

    // From the first Show until the session ends, the app stays pinned so a subject cannot reach other apps (FR-61).
    LaunchedEffect(state.mode) {
        if (state.mode == Mode.SHOW) guard.pin()
    }
    ImmersiveWhile(activity, state.mode == Mode.SHOW)

    when (state.mode) {
        Mode.OFFICER -> OfficerPane(
            ui = state,
            vm = vm,
            officerLang = officerLang,
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
                ShowPane(
                    ui = state,
                    content = content,
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
