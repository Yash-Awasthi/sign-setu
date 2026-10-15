package app.signsetu.ui.session

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.ui.components.AnswerButtons
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.PictureCard
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing
import java.io.ByteArrayOutputStream

/** The subject panel. Nothing here shows officer notes, the log, or unconfirmed input. */
@Composable
fun ShowPane(
    ui: SessionUi,
    content: ContentBundle,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDrawing: (ByteArray) -> Unit,
    onOfficer: () -> Unit,
) {
    val lang = ui.session.subjectLang
    var drawing by remember(ui.pending) { mutableStateOf(ui.pending == Pending.Drawing) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f }
                .safeDrawingPadding()
                .padding(Spacing.xl),
        ) {
            val pending = ui.pending
            when {
                drawing -> DrawingContent(content, lang, onDone = onDrawing)
                pending == Pending.LanguageFinder -> LanguageFinder(content, ui.chosenLanguage, onLanguage)
                pending is Pending.ShowBack -> ShowBackContent(pending, content, lang, ui.answeredYes, onYesNo)
                pending != null -> QuestionContent(pending, content, lang, ui, onCard, onYesNo, onDraw = { drawing = true })
            }
            OfficerHandle(onOfficer, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun OfficerHandle(onClick: () -> Unit, modifier: Modifier) {
    // Small and quiet: the subject should not be drawn to it; the officer knows where it is.
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Person,
                contentDescription = stringResource(R.string.show_officer),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun QuestionContent(
    pending: Pending,
    content: ContentBundle,
    lang: String,
    ui: SessionUi,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onDraw: () -> Unit,
) {
    val caption: String?
    val cardIds: List<String>
    when (pending) {
        is Pending.Ask -> { caption = pending.question.text.pick(lang); cardIds = pending.question.cards }
        is Pending.FreeText -> { caption = pending.text; cardIds = pending.cardIds }
        is Pending.Cards -> { caption = null; cardIds = pending.cardIds }
        else -> { caption = null; cardIds = emptyList() }
    }
    val drawable = "draw" in cardIds
    val pictures = cardIds.filter { it != "draw" }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Spacer(Modifier.height(40.dp))
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (pictures.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (pictures.size <= 2) 220.dp else 140.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f),
            ) {
                items(pictures, key = { it }) { id ->
                    PictureCard(
                        cardId = id,
                        label = content.cards[id]?.label?.pick(lang),
                        selected = ui.answeredCard == id,
                        onClick = { onCard(id) },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (drawable) {
            FilledTonalButton(onClick = onDraw, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Icon(Icons.Filled.Create, contentDescription = null)
                content.subjectText("draw", lang)?.let { Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Spacing.md)) }
            }
        }
        AnswerButtons(
            yesLabel = content.subjectText("yes", lang),
            noLabel = content.subjectText("no", lang),
            onYes = { onYesNo(true) },
            onNo = { onYesNo(false) },
            chosen = ui.answeredYes,
        )
    }
}

@Composable
private fun ShowBackContent(pending: Pending.ShowBack, content: ContentBundle, lang: String, answered: Boolean?, onYesNo: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Spacer(Modifier.height(40.dp))
        // Yes and No stay anchored at the bottom on every subject screen, so they are always in the same place.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f),
            ) {
                CardImage(pending.entry.card, Modifier.padding(Spacing.xl))
            }
        }
        content.subjectText("is_this_it", lang)?.let {
            Text(it, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
        AnswerButtons(
            yesLabel = content.subjectText("yes", lang),
            noLabel = content.subjectText("no", lang),
            onYes = { onYesNo(true) },
            onNo = { onYesNo(false) },
            chosen = answered,
        )
    }
}

@Composable
private fun LanguageFinder(content: ContentBundle, chosen: String?, onLanguage: (String) -> Unit) {
    val selection = LocalAnswerColors.current.selection
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
    ) {
        items(content.languages, key = { it.code }) { l ->
            val selected = chosen == l.code
            Surface(
                onClick = { onLanguage(l.code) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = if (selected) BorderStroke(4.dp, selection) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = 112.dp),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(l.name, style = MaterialTheme.typography.headlineSmall)
                    Text(l.english, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private class DrawStroke(val points: List<Offset>)

@Composable
private fun DrawingContent(content: ContentBundle, lang: String, onDone: (ByteArray) -> Unit) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Spacer(Modifier.height(40.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = paper,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> current = current + change.position },
                            onDragEnd = { strokes.add(DrawStroke(current)); current = emptyList() },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                (strokes + DrawStroke(current)).forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path,
                        color = ink,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedButton(onClick = { strokes.clear() }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                content.subjectText("clear", lang)?.let { Text(it, modifier = Modifier.padding(start = Spacing.sm)) }
            }
            FilledTonalButton(
                onClick = { onDone(renderPng(strokes, canvasSize, ink.toArgb(), paper.toArgb())) },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                content.subjectText("done", lang)?.let { Text(it, modifier = Modifier.padding(start = Spacing.sm)) }
            }
        }
    }
}

private fun renderPng(strokes: List<DrawStroke>, size: IntSize, ink: Int, paper: Int): ByteArray {
    val w = size.width.coerceAtLeast(1)
    val h = size.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(paper)
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 8 * density
            color = ink
        }
        val path = Path().apply {
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
