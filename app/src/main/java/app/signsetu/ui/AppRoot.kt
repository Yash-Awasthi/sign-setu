package app.signsetu.ui

import app.signsetu.ui.theme.systemAnimationsOn
import app.signsetu.ui.theme.LocalMotion
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.ExperimentalComposeUiApi
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import android.app.Activity
import android.widget.Toast
import app.signsetu.assistant.Notice
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import android.content.Context
import app.signsetu.ui.components.LocalOnlineAssistant
import app.signsetu.ui.components.LocalSentenceHelper
import app.signsetu.ui.components.OfficerAuth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.ui.practice.PracticeScreen
import app.signsetu.ui.settings.AppearanceScreen
import app.signsetu.ui.settings.SettingsScreen
import app.signsetu.ui.start.FirstRunScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import app.signsetu.ui.components.ScreenSkeleton
import app.signsetu.AppContainer
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.camera.LandmarkTracker
import app.signsetu.sign.SignReader
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.signsetu.ui.start.StartScreen
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.data.settings.ThemeMode
import app.signsetu.data.settings.ColorTheme
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.serialization.Serializable
import app.signsetu.ui.components.GuardedAlertDialog
import android.view.WindowManager
import androidx.compose.runtime.remember
import app.signsetu.pair.LinkRole
import app.signsetu.ui.pair.SecondScreenSheet
import app.signsetu.ui.pair.StageDeviceScreen
import app.signsetu.ui.pair.UseAsStageButton

@Serializable data object StartKey : NavKey
@Serializable data class SessionKey(val id: String) : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object PracticeKey : NavKey
@Serializable data object AppearanceKey : NavKey
@Serializable data object RecordSignsKey : NavKey
@Serializable data object SignToTypeKey : NavKey
@Serializable data class HelpKey(val topic: String? = null) : NavKey

private fun noticeText(context: Context, n: Notice) = context.getString(when (n) {
    Notice.FALLBACK_OFFLINE -> R.string.notice_offline
    Notice.FALLBACK_ERROR -> R.string.notice_gateway_error
    Notice.REVOKED -> R.string.notice_revoked
    Notice.BACK_ONLINE -> R.string.notice_back_online
    Notice.ENROLLED -> R.string.notice_enrolled
    Notice.ENROLL_FAILED -> R.string.notice_enroll_failed
    Notice.ENROLL_RATE_LIMITED -> R.string.notice_enroll_rate_limited
})

@Composable
fun AppRoot(container: AppContainer) {
    val repo by container.sessions.collectAsStateWithLifecycle()
    val content by produceState<ContentBundle?>(null) { value = container.content.load() }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val link by container.link.collectAsStateWithLifecycle()
    LaunchedEffect(settings?.avatarStyle) {
        app.signsetu.ui.avatar.AvatarRenderer.style.value = settings?.avatarStyle
            ?.let { runCatching { app.signsetu.ui.avatar.AvatarStyle.valueOf(it) }.getOrNull() } ?: app.signsetu.ui.avatar.AvatarStyle.AUTO
        if (app.signsetu.ui.avatar.AvatarRenderer.style.value != app.signsetu.ui.avatar.AvatarStyle.AUTO) app.signsetu.ui.avatar.AvatarViews.drop()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current
    LaunchedEffect(settings?.appLock) { OfficerAuth.required = settings?.appLock != false }
    LaunchedEffect(settings?.blockScreenshots) {
        val block = settings?.blockScreenshots ?: return@LaunchedEffect
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (block) activity?.window?.addFlags(flag) else activity?.window?.clearFlags(flag)
    }
    DisposableEffect(container) {
        container.onlineAssistant.onNotice = { n ->
            context.mainExecutor.execute { Toast.makeText(context, noticeText(context, n), Toast.LENGTH_LONG).show() }
        }
        onDispose { container.onlineAssistant.onNotice = {} }
    }

    val appContext = LocalContext.current
    val motionOn = systemAnimationsOn(appContext)
    val dark = settings?.colorTheme == ColorTheme.CONTRAST || when (settings?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    // Edge-to-edge picks bar icons from the phone's theme; a pinned app theme must set them itself.
    val view = LocalView.current
    DisposableEffect(view, dark) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {}
    }

    CompositionLocalProvider(LocalContentRepository provides container.content, LocalOnlineAssistant provides container.onlineAssistant, app.signsetu.ui.components.LocalBrain provides container.brain, LocalSentenceHelper provides container.sentenceModel, LocalMotion provides motionOn) { NoPersonalisedLearning {
        SignSetuTheme(dark = dark, theme = settings?.colorTheme ?: ColorTheme.EMERALD, amoled = settings?.amoled == true) {
            val r = repo
            val c = content
            val s = settings
            val stage = link?.takeIf { it.role == LinkRole.STAGE }
            var tour by rememberSaveable { mutableStateOf(false) }
            // Keeps the onboarding page while the tour covers it, so Back returns to the same step.
            val firstRunState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            androidx.activity.compose.BackHandler(enabled = tour && s?.firstRunDone == false) { tour = false }
            when {
                s == null -> ScreenSkeleton()
                // The tour holds no records, so it opens before the first unlock.
                !s.firstRunDone && tour -> app.signsetu.ui.help.HelpScreen(onBack = { tour = false }, startTopic = "tour")
                !s.firstRunDone -> firstRunState.SaveableStateProvider("firstRun") {
                    FirstRunScreen(c?.gestures.orEmpty(), onDone = { scope.launch { container.settings.setFirstRunDone() } }, onTour = { tour = true })
                }
                // A stage device never opens the session database or the navigation.
                stage != null && c != null -> StageDeviceScreen(stage, c, container)
                // The officer's screen lock opens the records; a device that has served as a stage waits for a tap.
                r == null -> LockScreen(
                    onUnlock = { onFailed -> OfficerAuth.confirm(activity, { container.unlockAsync(onFailed) }, onFailed) },
                    autoPrompt = !s.stageDevice,
                    stageButton = { UseAsStageButton(container) },
                )
                c == null -> ScreenSkeleton()
                else -> Navigation(r, c, s, container)
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
            val keyUnbound by container.keyUnbound.collectAsStateWithLifecycle()
            if (keyUnbound && r != null && !keyReset) {
                GuardedAlertDialog(
                    onDismissRequest = container::acknowledgeKeyUnbound,
                    title = { Text(stringResource(R.string.key_unbound_title)) },
                    text = { Text(stringResource(R.string.key_unbound_body)) },
                    confirmButton = { TextButton(onClick = container::acknowledgeKeyUnbound) { Text(stringResource(R.string.ok)) } },
                )
            }
            val downgraded by container.downgraded.collectAsStateWithLifecycle()
            if (downgraded && r != null) {
                GuardedAlertDialog(
                    onDismissRequest = container::acknowledgeDowngrade,
                    title = { Text(stringResource(R.string.downgrade_reset_title)) },
                    text = { Text(stringResource(R.string.downgrade_reset_body)) },
                    confirmButton = { TextButton(onClick = container::acknowledgeDowngrade) { Text(stringResource(R.string.ok)) } },
                )
            }
        }
    } }
}

/** every text field in the app asks the keyboard not to learn from what is typed, since it is about a subject. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoPersonalisedLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                    request.createInputConnection(outAttributes).also {
                        outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
            })
        },
        content = content,
    )
}

@Composable
private fun Navigation(repo: SessionRepository, content: ContentBundle, settings: AppSettings, container: AppContainer) {
    val backStack = rememberNavBackStack(StartKey)
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    var pairing by remember { mutableStateOf(false) }
    var sandesHandoffContact by rememberSaveable { mutableStateOf<String?>(null) }
    var whatsappConfirm by rememberSaveable { mutableStateOf<String?>(null) }
    // Only a tap on the call button gets here, and it only asks: nothing opens before the officer confirms.
    val interpreterCall: (() -> Unit)? = if (settings.interpreterConfigured) {
        {
            if (settings.interpreterApp == InterpreterApp.SANDES) sandesHandoffContact = settings.interpreterContact
            else whatsappConfirm = settings.interpreterContact
        }
    } else null
    val interpreterName = stringResource(if (settings.interpreterApp == InterpreterApp.SANDES) R.string.settings_sandes else R.string.settings_whatsapp)
    val onSecondScreen = { pairing = true }.takeIf { container.pairTransport != null }
    // Each reads and hashes model files, so none is built on the main thread; null until ready, as with no model.
    val tracker by produceState<LandmarkTracker?>(null) { value = withContext(Dispatchers.IO) { container.landmarkTracker } }
    val reader by produceState<SignReader?>(null) { value = withContext(Dispatchers.IO) { container.signReader } }
    // Loads the assistant's native library off the main thread, before Settings asks whether it runs here.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { container.models.state() } }
    if (pairing) SecondScreenSheet(container, onDismiss = { pairing = false })
    val station by container.brain.state.collectAsStateWithLifecycle()
    val motionOn = LocalMotion.current
    NavDisplay(
        backStack = backStack,
        transitionSpec = { if (motionOn) fadeIn(tween(220, 60)) + slideInHorizontally(tween(260)) { it / 12 } togetherWith fadeOut(tween(120)) else EnterTransition.None togetherWith ExitTransition.None },
        popTransitionSpec = { if (motionOn) fadeIn(tween(220, 60)) + slideInHorizontally(tween(260)) { -it / 12 } togetherWith fadeOut(tween(120)) else EnterTransition.None togetherWith ExitTransition.None },
        predictivePopTransitionSpec = { if (motionOn) fadeIn(tween(220, 60)) togetherWith fadeOut(tween(120)) else EnterTransition.None togetherWith ExitTransition.None },
        // Leaving a session by Back asks for the screen lock, as the Leave button does.
        onBack = { if (backStack.lastOrNull() is SessionKey) OfficerAuth.confirm(activity, { backStack.removeLastOrNull() }) else backStack.removeLastOrNull() },
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
                    onSecondScreen = onSecondScreen,
                    onCallInterpreter = interpreterCall,
                    interpreterSummary = "$interpreterName · ${settings.interpreterContact}",
                    station = station,
                    onTour = { backStack.add(HelpKey("tour")) },
                    savedActions = run {
                        val ctx = LocalContext.current
                        val lang = if (LocalConfiguration.current.locales[0].language == "hi") "hi" else "en"
                        app.signsetu.ui.start.SavedActions(
                            download = { s -> app.signsetu.ui.session.saveToPhone(ctx, repo, content, lang, container.report, s) },
                            upload = { s, progress ->
                                // The seal it is sent at: the "i" shows while the session still has that seal.
                                val root = s.rootHash ?: repo.head(s.id)
                                val zip = app.signsetu.export.StationExport.build(repo, s.id)
                                val path = zip?.let { container.brain.archive(app.signsetu.export.StationExport.name(s.title, s.id), it, progress) }
                                zip?.fill(0)
                                if (path != null && root != null) repo.markStationExport(s.id, path, root)
                                path
                            },
                        )
                    },
                )
            }
            entry<PracticeKey> {
                PracticeScreen(content, onBack = { backStack.removeLastOrNull() })
            }
            entry<SettingsKey> {
                SettingsScreen(container.settings, container.models, container.sentenceModel, onBack = { backStack.removeLastOrNull() }, onClearAll = { OfficerAuth.confirm(activity, container::clearAllData) }, onPractice = { backStack.add(PracticeKey) }, onSignToType = { backStack.add(SignToTypeKey); Unit }.takeIf { tracker != null }, onAppearance = { backStack.add(AppearanceKey) }, onCallInterpreter = interpreterCall, onRecordSigns = { backStack.add(RecordSignsKey); Unit }.takeIf { tracker != null && app.signsetu.BuildConfig.DEBUG }, repo = repo, onHelp = { backStack.add(HelpKey()) })
            }
            entry<SignToTypeKey> {
                app.signsetu.ui.practice.SignToTypeScreen(content, tracker, reader, onBack = { backStack.removeLastOrNull() })
            }
            entry<RecordSignsKey> {
                app.signsetu.ui.practice.RecordSignsScreen(tracker, container.contributions, onBack = { backStack.removeLastOrNull() })
            }
            entry<HelpKey> { key ->
                app.signsetu.ui.help.HelpScreen(onBack = { backStack.removeLastOrNull() }, startTopic = key.topic)
            }
            entry<AppearanceKey> {
                AppearanceScreen(container.settings, onBack = { backStack.removeLastOrNull() })
            }
            entry<SessionKey> { key ->
                SessionRoute(
                    key.id, repo, content, settings, container.report, tracker, reader,
                    onFavourite = { id -> scope.launch { container.settings.toggleFavourite(id) } },
                    assistant = container.assistant,
                    onClosed = { backStack.removeLastOrNull() },
                    container = container,
                    onSecondScreen = onSecondScreen,
                    onCallInterpreter = interpreterCall,
                )
            }
        },
    )
    sandesHandoffContact?.let { contact ->
        SandesHandoffDialog(
            contact = contact,
            onLaunch = {
                sandesHandoffContact = null
                activity?.let(::launchSandes)
            },
            onDismiss = { sandesHandoffContact = null },
        )
    }
    whatsappConfirm?.let { contact ->
        GuardedAlertDialog(
            onDismissRequest = { whatsappConfirm = null },
            icon = { Icon(Icons.Filled.Call, contentDescription = null) },
            title = { Text(stringResource(R.string.call_confirm_title)) },
            text = { Text(stringResource(R.string.call_confirm_body, stringResource(R.string.settings_whatsapp), contact)) },
            confirmButton = { TextButton(onClick = { whatsappConfirm = null; activity?.let { callInterpreter(it, settings) } }) { Text(stringResource(R.string.call_confirm_go)) } },
            dismissButton = { TextButton(onClick = { whatsappConfirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
