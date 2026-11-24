package com.mrjdm.scribe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mrjdm.scribe.data.ScribeViewModel

private val WorkGold = Color(0xFFD9BD91)
private val WorkMuted = Color(0xFFAAA69E)
private val WorkPanel = Color(0xFF1B1D21)

@Composable
fun FormulaLibrary(vm: ScribeViewModel) {
    val saved by vm.kind("formula").collectAsState(initial = emptyList())
    val presets = listOf(
        "Ohm's law" to "V = IR", "Electrical power" to "P = VI = I²R = V²/R",
        "Capacitive reactance" to "Xc = 1/(2πfC)", "Inductive reactance" to "XL = 2πfL",
        "Resonant frequency" to "f₀ = 1/(2π√LC)", "RC time constant" to "τ = RC",
        "Kirchhoff's current law" to "ΣI = 0", "Kirchhoff's voltage law" to "ΣV = 0",
        "Voltage divider" to "Vout = Vin · R₂/(R₁ + R₂)", "Non-inverting op-amp" to "Av = 1 + Rf/Rin"
    )
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FORMULA SHELF", color = WorkMuted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Find a formula…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
        Text("ENGINEERING ESSENTIALS", color = WorkMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        presets.filter { (name, expr) -> query.isBlank() || name.contains(query, true) || expr.contains(query, true) }.forEach { (name, expr) ->
            Card(colors = CardDefaults.cardColors(containerColor = WorkPanel), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Medium)
                        Text(expr, color = WorkGold, fontSize = 17.sp)
                    }
                    IconButton(onClick = { vm.add(null, "formula", name, body = expr, tags = "subject:engineering") }) {
                        Icon(Icons.Rounded.AddCircleOutline, "Save formula", tint = WorkGold)
                    }
                }
            }
        }
        Text("YOUR SAVED FORMULAS · ${saved.size}", color = WorkMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        saved.forEach { formula ->
            Card(colors = CardDefaults.cardColors(containerColor = WorkPanel), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formula.title, fontWeight = FontWeight.Medium)
                        Text(formula.body, color = WorkGold)
                    }
                    IconButton(onClick = { vm.removeTree(formula) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = WorkMuted) }
                }
            }
        }
        if (saved.isEmpty()) Text("Save frequently used equations here. Your collection is included in Scribe backups.", color = WorkMuted, fontSize = 12.sp)
    }
}

@Composable
fun CircuitWorkbench(vm: ScribeViewModel) {
    val parts = listOf("Resistor", "Capacitor", "Inductor", "Diode", "LED", "Battery")
    var selected by remember { mutableStateOf("Resistor") }
    var reference by remember { mutableStateOf("R1") }
    var value by remember { mutableStateOf("1 kΩ") }
    var notes by remember { mutableStateOf("Series circuit") }
    var quantity by remember { mutableIntStateOf(1) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CIRCUIT SKETCHPAD", color = WorkMuted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        Text("Choose a component, record its value and save a schematic note.", color = WorkMuted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { part -> FilterChip(selected == part, { selected = part }, label = { Text(part, fontSize = 11.sp) }) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = WorkPanel), shape = RoundedCornerShape(18.dp)) {
            Canvas(Modifier.fillMaxWidth().height(150.dp).padding(16.dp)) {
                val cy = size.height / 2
                drawLine(WorkGold, Offset(12f,cy), Offset(size.width*.34f,cy), 2.dp.toPx())
                drawLine(WorkGold, Offset(size.width*.66f,cy), Offset(size.width-12f,cy), 2.dp.toPx())
                when (selected) {
                    "Resistor" -> {
                        val p = Path().apply { moveTo(size.width*.34f,cy); lineTo(size.width*.39f,cy-15); lineTo(size.width*.44f,cy+15); lineTo(size.width*.49f,cy-15); lineTo(size.width*.54f,cy+15); lineTo(size.width*.59f,cy-15); lineTo(size.width*.66f,cy) }
                        drawPath(p, WorkGold, style = Stroke(2.dp.toPx()))
                    }
                    "Capacitor" -> {
                        drawLine(WorkGold, Offset(size.width*.48f,cy-25), Offset(size.width*.48f,cy+25), 3.dp.toPx())
                        drawLine(WorkGold, Offset(size.width*.53f,cy-25), Offset(size.width*.53f,cy+25), 3.dp.toPx())
                    }
                    "Diode", "LED" -> {
                        val p = Path().apply { moveTo(size.width*.44f,cy-22); lineTo(size.width*.56f,cy); lineTo(size.width*.44f,cy+22); close() }
                        drawPath(p, WorkGold, style = Stroke(2.dp.toPx()))
                        drawLine(WorkGold, Offset(size.width*.58f,cy-22), Offset(size.width*.58f,cy+22), 2.dp.toPx())
                    }
                    "Battery" -> {
                        drawLine(WorkGold, Offset(size.width*.48f,cy-26), Offset(size.width*.48f,cy+26), 3.dp.toPx())
                        drawLine(WorkGold, Offset(size.width*.55f,cy-15), Offset(size.width*.55f,cy+15), 3.dp.toPx())
                    }
                    else -> for (i in 0..3) drawArc(WorkGold, 180f, 180f, false, Offset(size.width*.37f+i*size.width*.07f,cy-13), androidx.compose.ui.geometry.Size(size.width*.08f,26f), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(reference, { reference = it }, modifier = Modifier.weight(1f), label = { Text("Reference") }, singleLine = true)
            OutlinedTextField(value, { value = it }, modifier = Modifier.weight(1f), label = { Text("Value") }, singleLine = true)
        }
        OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Circuit notes") }, minLines = 2)
        OutlinedButton(onClick = { quantity++ }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add component · $quantity in sketch")
        }
        Button(onClick = {
            val body = "Component: $selected\nReference: $reference\nValue: $value\nQuantity in sketch: $quantity\nNotes: $notes"
            vm.add(null, "diagram", "Circuit · $reference", body = body, tags = "type:circuit;subject:electronics")
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WorkGold, contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save circuit note")
        }
        Text("This is a schematic starter, not a full drag-and-drop circuit CAD editor or SPICE simulator.", color = WorkMuted, fontSize = 11.sp)
    }
}

@Composable
fun ScientificTable(vm: ScribeViewModel) {
    var title by remember { mutableStateOf("Measurement table") }
    var cells by remember { mutableStateOf(List(4) { List(3) { "" } }) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SCIENTIFIC TABLE", color = WorkMuted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Table title") }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { if (cells.size < 12) cells = cells + List(cells.first().size) { "" } }) { Icon(Icons.Rounded.Add, null); Text("Add row") }
            OutlinedButton(onClick = { if (cells.first().size < 8) cells = cells.map { it + "" } }) { Icon(Icons.Rounded.ViewColumn, null); Text("Add column") }
            OutlinedButton(onClick = { cells = List(4) { List(3) { "" } } }) { Icon(Icons.Rounded.Refresh, null); Text("Reset") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = WorkPanel), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                cells.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        row.forEachIndexed { c, cell ->
                            OutlinedTextField(cell, { v -> cells = cells.mapIndexed { ri, old -> if (ri == r) old.mapIndexed { ci, x -> if (ci == c) v else x } else old } },
                                modifier = Modifier.width(112.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall,
                                label = if (r == 0) ({ Text("Col ${c+1}", fontSize = 9.sp) }) else null)
                        }
                    }
                }
            }
        }
        Button(onClick = {
            val tsv = cells.joinToString("\n") { row -> row.joinToString("\t") }
            vm.add(null, "block", title, body = tsv, tags = "type:table;format:tsv")
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WorkGold, contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save table to library")
        }
        Text("Editable cells are stored as tab-separated data and included in backups.", color = WorkMuted, fontSize = 11.sp)
    }
}
