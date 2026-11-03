package app.signsetu.ui

import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.signsetu.ui.theme.motion
import androidx.compose.animation.Crossfade
import app.signsetu.assistant.Assistant
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import app.signsetu.ui.components.OfficerAuth
import androidx.compose.ui.platform.LocalContext
import app.signsetu.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.SideEffect
import androidx.core.content.FileProvider
import app.signsetu.export.SessionReport
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.animation.core.tween
import app.signsetu.AppContainer
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.forSignLanguage
import app.signsetu.ui.session.signLanguage
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Conv
import app.signsetu.ui.session.planLadder
import androidx.lifecycle.viewModelScope
import app.signsetu.ui.session.OfficerPane
import app.signsetu.ui.session.titleToKeep
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.Stage
import app.signsetu.ui.session.defaultModes
import app.signsetu.ui.session.typedModes
import app.signsetu.camera.LandmarkTracker
import app.signsetu.sign.SignReader
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.signsetu.ui.theme.SubjectTheme
import android.view.WindowManager
import androidx.compose.runtime.remember
import app.signsetu.pair.ByeReason
import app.signsetu.pair.LinkRole
import app.signsetu.pair.LinkState
import app.signsetu.ui.pair.rememberPairedStage

@Composable
internal fun SessionRoute(
    sessionId: String,
    repo: SessionRepository,
    content: ContentBundle,
    settings: AppSettings,
    report: SessionReport,
    tracker: LandmarkTracker?,
    reader: SignReader?,
    assistant: Assistant?,
    onFavourite: (String) -> Unit,
    onClosed: () -> Unit,
    container: AppContainer,
    onSecondScreen: (() -> Unit)?,
    onCallInterpreter: (() -> Unit)?,
) {
    val officerLang = if (LocalConfiguration.current.locales[0].language == "hi") "hi" else "en"
    val vm: SessionViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory { initializer { SessionViewModel(sessionId, repo, content, officerLang, report, reader, checkOnResume = true) } },
    )
    LaunchedEffect(tracker) { tracker?.let { withContext(Dispatchers.Default) { it.warm() } } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val signLanguage = ui?.signLanguage ?: SignLanguage.ISL
    val langReader by produceState(reader.takeIf { signLanguage == SignLanguage.ISL }, signLanguage, reader) {
        value = if (signLanguage == SignLanguage.ISL) reader else withContext(Dispatchers.IO) { container.signReader(signLanguage) }
    }
    SideEffect { vm.reader = langReader }
    // The station reads ISL only: its reader is the department's ISL model.
    SideEffect { vm.station = container.brain.takeIf { signLanguage == SignLanguage.ISL } }
    val planner = remember(vm) { planLadder(content, container.brain, container.models, container.onlineAssistant, vm.viewModelScope) { vm.auto.cloudAllowed } }
    SideEffect { vm.auto.planner = planner }
    val signContent = remember(content, signLanguage) { content.forSignLanguage(signLanguage) }
    val conv by vm.auto.conv.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val cameraOn = rememberSaveable { settings.cameraEnabled }
    // with a second screen the subject's stage is there, and this phone stays the officer's.
    val link by container.link.collectAsStateWithLifecycle()
    val officerLink = link?.takeIf { it.role == LinkRole.OFFICER }
    val linkState = officerLink?.state?.collectAsStateWithLifecycle()?.value
    val paired = linkState == LinkState.Live || linkState == LinkState.Lost || linkState == LinkState.Hold || linkState is LinkState.Ended
    DisposableEffect(Unit) {
        container.inSession = true
        onDispose { container.inSession = false }
    }
    // A recreation for dark mode, font scale or locale keeps the session; only really leaving it ends the link.
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true && container.link.value?.role == LinkRole.OFFICER) container.endLink() } }
    KeepScreenOnWhile(activity, linkState == LinkState.Live)
    val state = ui ?: return
    val pane = officerLink?.takeIf { paired }?.let {
        rememberPairedStage(vm, it, settings, cameraOn, officerLang, onFallback = {
            vm.fallbackToLocal()
            container.endLink(ByeReason.FALLBACK)
        })
    }
    val backToOfficer: () -> Unit = vm::returnToOfficer
    val nextIn by vm.auto.countdown.collectAsStateWithLifecycle()
    // Auto mode names its next item seconds ahead; decoding that item's pictures now keeps its arrival instant.
    val proposedNext by vm.auto.conv.collectAsStateWithLifecycle()
    LaunchedEffect((proposedNext as? Conv.Proposed)?.p?.q?.id) {
        (proposedNext as? Conv.Proposed)?.p?.q?.cards?.forEach { runCatching { container.content.cardImage(it) } }
    }
    // The camera service and the sleeping gateway take longest on first use; waking them now keeps the first item quick.
    val appContext = LocalContext.current
    val cloud = container.onlineAssistant
    LaunchedEffect(Unit) {
        if (cameraOn && ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching { ProcessCameraProvider.awaitInstance(appContext) }
        }
        if (cloud.sendsSessionData()) cloud.warmUp()
    }

    val localStage = state.mode == Mode.SHOW && !paired
    ImmersiveWhile(activity, localStage)
    // Pinned once for the whole session, so Back, Home and Recents stay out of reach and nothing re-pins between items.
    PinWhile(activity, !paired && settings.pinApp)
    // The assistant's buffers would sit beside the camera and the landmark graph on a small phone.
    LaunchedEffect(localStage || state.signWindow != null) { if (localStage || state.signWindow != null) container.models.release() }
    // The app locking takes this screen away: landmarks go with it, though not on a mere configuration change.
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) vm.wipeSigns() } }

    Crossfade(targetState = if (paired) Mode.OFFICER else state.mode, animationSpec = motion(tween(220)), label = "mode") { shown ->
    when (shown) {
        Mode.OFFICER -> OfficerPane(
            ui = state,
            vm = vm,
            officerLang = officerLang,
            favourites = settings.favourites,
            onFavourite = onFavourite,
            assistant = assistant,
            canReadSigns = tracker != null && !paired,
            paired = pane,
            onSecondScreen = onSecondScreen,
            islReorder = settings.islReorder,
            onCallInterpreter = onCallInterpreter,
            onShare = { file ->
                activity?.let { shareReport(it, file) }
            },
            historyOff = container.deviceLooksRooted,
            onLeave = { OfficerAuth.confirm(activity, onClosed) },
            onEndSession = { asked ->
                OfficerAuth.confirm(activity, {
                    val title = titleToKeep(asked, container.deviceLooksRooted)
                    val reopened = vm.ui.value?.session?.savedThrough != null
                    vm.endSession(title) {
                        officerLink?.let { container.endLink() }
                        val said = when { title != null -> R.string.session_saved; reopened -> R.string.session_discarded; else -> R.string.session_deleted }
                        activity?.let { Toast.makeText(it, said, Toast.LENGTH_LONG).show() }
                        onClosed()
                    }
                })
            },
        )
        Mode.SHOW -> {
            // Back on the stage does nothing: the officer holds the handle to take the phone back.
            BackHandler { }
            SubjectTheme {
                Stage(
                    ui = state,
                    content = signContent,
                    tracker = tracker,
                    cameraOn = cameraOn,
                    gestureSpeed = settings.gestureSpeed,
                    askModes = typedModes(vm.modesFor(state.session.profile, defaultModes(state.session.profile)), settings.islReorder),
                    onCard = vm::answerCard,
                    onYesNo = vm::answerYesNo,
                    onCue = vm::noteCue,
                    onCameraRead = vm::cameraRead,
                    onLanguage = vm::chooseLanguage,
                    onDrawing = vm::submitDrawing,
                    onWritten = vm::answerText,
                    onSignFrame = vm::onSignFrame,
                    onStartSigning = vm::startReading,
                    onDoneSigning = vm::finishReading,
                    onOfficer = backToOfficer,
                    onSignChoice = vm::chooseSign,
                    onNextRule = vm::nextRule,
                    nextIn = nextIn,
                    onPauseEnd = vm::endPause,
                    onGiven = vm::give,
                    onNeed = vm::askNeed,
                )
            }
        }
    }
    }
}

private fun shareReport(activity: Activity, file: File) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    unpinFor(activity)
    activity.startActivity(Intent.createChooser(send, null))
}

/** Pins the app while [enabled]: friction, not a lock, since unpinning keeps its system gesture. A refusal (managed
 * profile, OEM quirk) leaves the stage usable. Pinning lifted for another app ([unpinFor]) is set again on return. */
@Composable
internal fun PinWhile(activity: Activity?, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        if (!enabled || activity == null || app.signsetu.BuildConfig.TEST_UNLOCKED) return@DisposableEffect onDispose { }
        var pinned = runCatching { activity.startLockTask() }.isSuccess
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val repin = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && PinLifted.value) {
                PinLifted.value = false
                pinned = runCatching { activity.startLockTask() }.isSuccess
            }
        }
        lifecycle?.addObserver(repin)
        onDispose {
            lifecycle?.removeObserver(repin)
            if (pinned) runCatching { activity.stopLockTask() }
        }
    }
}

private object PinLifted { @Volatile var value = false }

/** A pinned task cannot open another app, so the interpreter call and the share sheet lift pinning first. */
internal fun unpinFor(activity: Activity) {
    val pinned = activity.getSystemService(android.app.ActivityManager::class.java)?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    if (pinned && runCatching { activity.stopLockTask() }.isSuccess) PinLifted.value = true
}

/** Pins again when the app [unpinFor] was meant to open did not start, since no resume will do it. */
internal fun repinAfterFailedOpen(activity: Activity) {
    if (PinLifted.value) {
        PinLifted.value = false
        runCatching { activity.startLockTask() }
    }
}

/** The officer phone must not sleep into the background mid-answer while a second screen is live. */
@Composable
private fun KeepScreenOnWhile(activity: Activity?, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
internal fun ImmersiveWhile(activity: Activity?, enabled: Boolean) {
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
