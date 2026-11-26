package app.signsetu.ui.session

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Space
import java.io.ByteArrayOutputStream
import androidx.compose.ui.res.painterResource
import app.signsetu.pair.TEXT_CAP
import androidx.compose.foundation.layout.RowScope
import app.signsetu.ui.theme.TypeSize
import app.signsetu.ui.theme.Radius
import app.signsetu.ui.theme.StageTones
import app.signsetu.ui.theme.StageLight

internal class DrawStroke(val points: List<Offset>, val color: Color = Ink)

/** Dark brown text and ink on white cards and paper. */
internal val Ink = StageLight.onBubble

/** Dark ink, and red, blue, green and brown, so a person can mark where it hurts or what colour something was. */
private val InkColors = listOf(Ink, StageTones.bodyMark, StageTones.crayonBlue, StageTones.crayonGreen, StageTones.crayonBrown)

private fun Offset.fraction(size: IntSize) = Offset(x / size.width.coerceAtLeast(1), y / size.height.coerceAtLeast(1))
private fun Offset.scaled(size: IntSize) = Offset(x * size.width, y * size.height)

/** The subject's own words, typed large on the stage; the keyboard opens at once and Done sends them. */
@Composable
internal fun WritingPanel(
    content: ContentBundle,
    lang: String,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    pauseRow: @Composable () -> Unit,
    modifier: Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val send = { if (text.isNotBlank()) onDone(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Surface(shape = RoundedCornerShape(Radius.r20), color = Color.White, border = TileEdge, shadowElevation = Space.xxs, modifier = Modifier.weight(1f).fillMaxWidth()) {
            BasicTextField(
                value = text,
                // Input past the cap is ignored, never cut later: the receiver logs what was sent.
                onValueChange = { if (it.length <= TEXT_CAP) text = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ink),
                // nothing the subject writes is learned or suggested by the keyboard.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxSize().padding(Space.s20).focusRequester(focus)
                    // Escape closes the writing panel, the same as its Back button.
                    .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onBack(); true } else false },
            )
        }
        if (text.length > TEXT_CAP - 200) {
            Text(stringResource(R.string.write_count, text.length, TEXT_CAP), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.End))
        }
        pauseRow()
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            BackButton(content, lang, onBack)
            DoneButton(content, lang, enabled = text.isNotBlank(), onClick = send)
        }
    }
}

/** Done in a writing or drawing panel; it greys out until there is something to send. */
@Composable
private fun RowScope.DoneButton(content: ContentBundle, lang: String, enabled: Boolean, onClick: () -> Unit) {
    val answer = LocalAnswerColors.current
    val word = content.subjectText("done", lang)
    PressableSurface(
        onClick = onClick,
        shape = Radius.pill,
        color = if (enabled) answer.yes else Off,
        modifier = Modifier.weight(1f).height(Space.s56).named(R.string.stage_done, word == null),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYes)
            word?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = answer.onYes, modifier = Modifier.padding(start = Space.sm)) }
        }
    }
}

/** Back to the question's answers, for a subject who opened writing or drawing by mistake. */
@Composable
private fun BackButton(content: ContentBundle, lang: String, onBack: () -> Unit) {
    val name = stringResource(R.string.stage_back)
    val stage = LocalStageColors.current
    PressableSurface(
        onClick = onBack,
        shape = CircleShape,
        color = stage.tool,
        border = stage.toolEdge,
        modifier = Modifier.size(Space.s56).semantics {
            role = Role.Button
            contentDescription = content.subjectText("back", lang) ?: name
        },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = stage.onTool) }
    }
}

/** A name for a control whose word is missing in the subject's language, so a screen reader still has one. */
@Composable
private fun Modifier.named(@StringRes id: Int, needed: Boolean): Modifier {
    val name = stringResource(id)
    return semantics {
        role = Role.Button
        if (needed) contentDescription = name
    }
}

@Composable
internal fun DrawingPanel(content: ContentBundle, lang: String, strokes: SnapshotStateList<DrawStroke>, onDone: (ByteArray) -> Unit, onBack: (() -> Unit)?, modifier: Modifier) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableStateOf(Ink) }
    var tool by remember { mutableStateOf(DrawTool.PEN) }
    // A drag reads the colour and tool chosen after it began: keyed on Unit, the gesture block would keep the first.
    val pen by rememberUpdatedState(ink)
    val shape by rememberUpdatedState(tool)
    val paper = Color.White
    val board = stringResource(R.string.stage_draw_board)
    Column(
        modifier.onPreviewKeyEvent { e ->
            // Escape backs out of drawing, the same as its Back button.
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && onBack != null) { onBack(); true } else false
        },
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.r20),
            color = paper,
            border = TileEdge,
            shadowElevation = Space.xxs,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .semantics { contentDescription = board }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> current = current + change.position },
                            // Kept in canvas fractions, so a drawing survives the canvas changing size.
                            onDragEnd = {
                                val trail = current.map { it.fraction(canvasSize) }
                                drawnLines(shape, trail).forEach { strokes.add(DrawStroke(it, pen)) }
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                val lines = strokes.map { s -> DrawStroke(s.points.map { it.scaled(canvasSize) }, s.color) } +
                    drawnLines(shape, current).map { DrawStroke(it, pen) }
                lines.forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, s.color, style = Stroke(width = Space.sm.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        val stage = LocalStageColors.current
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            DrawTool.entries.forEach { t ->
                val name = stringResource(t.nameId())
                val chosen = t == tool
                Box(
                    Modifier.size(Space.s48).clip(CircleShape)
                        .background(if (chosen) stage.tool else Color.Transparent)
                        .border(stage.toolEdge, CircleShape)
                        .clickable(role = Role.RadioButton) { tool = t }
                        .semantics { selected = chosen; contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(Space.s26)) { drawToolGlyph(t, stage.onTool) }
                }
            }
            Spacer(Modifier.weight(1f))
            // Undo takes back one line or shape; a slip of the finger should not cost the whole picture.
            PressableSurface(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, shape = CircleShape, color = stage.tool, border = stage.toolEdge,
                modifier = Modifier.size(Space.s48).semantics { contentDescription = content.subjectText("undo", lang) ?: "Undo" },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_undo), contentDescription = null, tint = stage.onTool) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            InkColors.forEachIndexed { i, c ->
                val name = stringResource(R.string.stage_ink_colour, i + 1, InkColors.size)
                Box(
                    Modifier.size(Space.s48).clip(CircleShape).clickable(role = Role.RadioButton) { ink = c }.semantics { selected = ink == c; contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(if (ink == c) Space.s40 else Space.xxl).background(c, CircleShape).border(if (ink == c) Space.s3 else Space.none, Color.White, CircleShape))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            onBack?.let { BackButton(content, lang, it) }
            PressableSurface(
                onClick = { strokes.clear() }, shape = Radius.pill, color = stage.tool, border = stage.toolEdge,
                modifier = Modifier.weight(1f).height(Space.s56).named(R.string.stage_clear, content.subjectText("clear", lang) == null),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = stage.onTool)
                    content.subjectText("clear", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = stage.onTool, modifier = Modifier.padding(start = Space.sm)) }
                }
            }
            DoneButton(content, lang, enabled = strokes.isNotEmpty()) {
                if (strokes.isNotEmpty()) onDone(renderPng(strokes.map { s -> DrawStroke(s.points.map { it.scaled(canvasSize) }, s.color) }, canvasSize, paper.toArgb()))
            }
        }
    }
}

@StringRes
private fun DrawTool.nameId(): Int = when (this) {
    DrawTool.PEN -> R.string.stage_tool_pen
    DrawTool.LINE -> R.string.stage_tool_line
    DrawTool.RECT -> R.string.stage_tool_rect
    DrawTool.OVAL -> R.string.stage_tool_oval
    DrawTool.ARROW -> R.string.stage_tool_arrow
}

/** The tool's mark on its chip: the shape it draws, at the size of the chip's ink. */
private fun DrawScope.drawToolGlyph(tool: DrawTool, color: Color) {
    val w = size.width
    val h = size.height
    val ink = Stroke(width = Space.xxs.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val a = Offset(w * 0.15f, h * 0.85f)
    val b = Offset(w * 0.85f, h * 0.15f)
    when (tool) {
        DrawTool.PEN -> drawLine(color, a, b, strokeWidth = Space.s3.toPx(), cap = StrokeCap.Round)
        DrawTool.LINE -> drawLine(color, a, b, strokeWidth = Space.xxs.toPx(), cap = StrokeCap.Round)
        DrawTool.RECT -> drawRect(color, Offset(w * 0.14f, h * 0.22f), Size(w * 0.72f, h * 0.56f), style = ink)
        DrawTool.OVAL -> drawOval(color, Offset(w * 0.14f, h * 0.22f), Size(w * 0.72f, h * 0.56f), style = ink)
        DrawTool.ARROW -> {
            drawLine(color, a, b, strokeWidth = Space.xxs.toPx(), cap = StrokeCap.Round)
            drawLine(color, b, Offset(w * 0.55f, h * 0.17f), strokeWidth = Space.xxs.toPx(), cap = StrokeCap.Round)
            drawLine(color, b, Offset(w * 0.83f, h * 0.45f), strokeWidth = Space.xxs.toPx(), cap = StrokeCap.Round)
        }
    }
}

private fun renderPng(strokes: List<DrawStroke>, size: IntSize, paper: Int): ByteArray {
    val w = size.width.coerceAtLeast(1)
    val h = size.height.coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(paper)
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 8 * density
    }
    strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        paint.color = s.color.toArgb()
        val path = android.graphics.Path().apply {
            moveTo(s.points[0].x, s.points[0].y)
            s.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

/** After a requested drawing: thanks, the way back to the officer, and a way to add to it. */
@Composable
internal fun HandBackPanel(content: ContentBundle, lang: String, onDrawMore: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterVertically)) {
        Surface(shape = CircleShape, color = LocalAnswerColors.current.yes, modifier = Modifier.size(Space.s96)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, contentDescription = null, tint = LocalAnswerColors.current.onYes, modifier = Modifier.size(Space.s56)) }
        }
        content.subjectText("give_back", lang)?.let {
            Text(it, fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = TypeSize.s22, textAlign = TextAlign.Center, color = stage.onBubble)
        }
        PressableSurface(
            onClick = onDrawMore, shape = Radius.pill, color = stage.tool, border = stage.toolEdge,
            modifier = Modifier.height(Space.s56).widthIn(min = Space.s160).named(R.string.stage_draw, content.subjectText("draw_more", lang) == null),
        ) {
            Row(Modifier.padding(horizontal = Space.lg), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Create, contentDescription = null, tint = stage.onTool)
                content.subjectText("draw_more", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = stage.onTool, modifier = Modifier.padding(start = Space.sm)) }
            }
        }
    }
}
