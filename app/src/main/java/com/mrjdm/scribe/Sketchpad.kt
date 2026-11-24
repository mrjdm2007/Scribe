package com.mrjdm.scribe

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjdm.scribe.data.ScribeViewModel
import java.io.File

private data class SketchStroke(val points: List<Offset>, val color: Color, val width: Float)

@Composable
fun Sketchpad(vm: ScribeViewModel) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    var active by remember { mutableStateOf(emptyList<Offset>()) }
    var ink by remember { mutableStateOf(Color(0xFF252525)) }
    var brush by remember { mutableFloatStateOf(4f) }
    var title by remember { mutableStateOf("Engineering sketch") }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var message by remember { mutableStateOf("") }
    val palette = listOf(Color(0xFF252525), Color(0xFFD5B98C), Color(0xFF327EAB), Color(0xFFC75B55), Color(0xFF47865B))
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FREEHAND CANVAS", color = Color(0xFFD5B98C), fontSize = 10.sp, letterSpacing = 1.4.sp)
        Text("Sketch, annotate and think visually.", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Sketch title") })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            palette.forEach { color ->
                Box(Modifier.padding(end = 8.dp).size(27.dp).background(color, RoundedCornerShape(50)).border(if (ink == color) 2.dp else 0.dp, Color.White, RoundedCornerShape(50)).clickable { ink = color })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Icon(Icons.Rounded.Undo, "Undo") }
            IconButton(onClick = { strokes.clear(); active = emptyList() }) { Icon(Icons.Rounded.DeleteSweep, "Clear") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pen width", fontSize = 11.sp)
            Slider(brush, { brush = it }, valueRange = 1f..12f, modifier = Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F0E9))) {
            Canvas(Modifier.fillMaxSize().pointerInput(ink, brush) {
                detectDragGestures(onDragStart = { active = listOf(it) }, onDrag = { change, _ -> active = active + change.position }, onDragEnd = { if (active.isNotEmpty()) { strokes.add(SketchStroke(active, ink, brush)); active = emptyList() } })
            }.also { }) {
                canvasSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                val all = strokes.toList() + listOfNotNull(if (active.isNotEmpty()) SketchStroke(active, ink, brush) else null)
                all.forEach { stroke ->
                    stroke.points.zipWithNext().forEach { (a, b) -> drawLine(stroke.color, a, b, stroke.width.dp.toPx(), cap = StrokeCap.Round) }
                    if (stroke.points.size == 1) drawCircle(stroke.color, stroke.width.dp.toPx() / 2f, stroke.points.first())
                }
            }
        }
        Button(onClick = {
            val w = canvasSize.width.coerceAtLeast(1); val h = canvasSize.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = NativeCanvas(bitmap); canvas.drawColor(android.graphics.Color.rgb(243, 240, 233))
            strokes.forEach { stroke ->
                val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb((stroke.color.alpha * 255).toInt(), (stroke.color.red * 255).toInt(), (stroke.color.green * 255).toInt(), (stroke.color.blue * 255).toInt())
                    style = NativePaint.Style.STROKE; strokeCap = NativePaint.Cap.ROUND; strokeJoin = NativePaint.Join.ROUND; strokeWidth = stroke.width * context.resources.displayMetrics.density
                }
                val path = NativePath()
                stroke.points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
                canvas.drawPath(path, paint)
            }
            val folder = File(context.filesDir, "attachments").apply { mkdirs() }
            val file = File(folder, "sketch_" + System.currentTimeMillis() + ".png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            vm.add(null, "block", title.ifBlank { "Engineering sketch" }, body = "Freehand sketch", uri = file.absolutePath, tags = "type:image,source:sketchpad")
            message = "Saved to your library."
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD5B98C), contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save sketch to library")
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFD5B98C), fontSize = 11.sp)
        Text("Saved sketches are stored locally as PNG attachments.", color = Color(0xFF96938D), fontSize = 11.sp)
    }
}