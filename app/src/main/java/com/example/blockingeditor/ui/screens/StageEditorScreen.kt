package com.example.blockingeditor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.blockingeditor.model.Dancer
import com.example.blockingeditor.model.Project
import com.example.blockingeditor.model.Formation
import com.example.blockingeditor.ui.ProjectViewModel
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageEditorScreen(viewModel: ProjectViewModel) {
    val project by viewModel.project.collectAsState()
    val currentIndex by viewModel.currentFormationIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val animatedDancers by viewModel.currentAnimatedDancers.collectAsState()
    val videoExportProgress by viewModel.videoExportProgress.collectAsState()
    val musicDuration by viewModel.musicDuration.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    
    val formation = project.formations.getOrNull(currentIndex) ?: project.formations.first()

    var draggedDancerId by remember { mutableStateOf<Int?>(null) }
    var selectedDancerId by remember { mutableStateOf<Int?>(null) }
    var showEditPanel by remember { mutableStateOf(false) }
    var showProjectSettings by remember { mutableStateOf(false) }

    val primaryGreen = Color(viewModel.primaryColor)

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setMusic(uri) }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            selectedDancerId = null
            showEditPanel = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(formation.name, style = MaterialTheme.typography.bodySmall, color = primaryGreen)
                    }
                },
                actions = {
                    if (!isPlaying) {
                        IconButton(onClick = { showProjectSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = { viewModel.exportImage() }) { Icon(Icons.Default.Image, null) }
                        IconButton(onClick = { viewModel.exportVideo() }) { Icon(Icons.Default.Movie, null) }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isPlaying && videoExportProgress == null) {
                FloatingActionButton(
                    onClick = { viewModel.addDancer("Dancer ${formation.dancers.size + 1}", viewModel.primaryColor) },
                    containerColor = primaryGreen,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Dancer")
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val maxVal = if (musicDuration > 0) musicDuration.toFloat() else 300000f
                    Slider(
                        value = currentTimeMs.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toInt()) },
                        valueRange = 0f..maxVal,
                        colors = SliderDefaults.colors(thumbColor = primaryGreen, activeTrackColor = primaryGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentTimeMs), style = MaterialTheme.typography.labelSmall)
                        Text(if (musicDuration > 0) formatTime(musicDuration.toLong()) else "Manual (5m)", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { musicPickerLauncher.launch("audio/*") }) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = "Music", tint = if (musicDuration > 0) primaryGreen else Color.Gray)
                        }
                        IconButton(onClick = { if (musicDuration > 0) viewModel.skipBackward() else viewModel.prevStep() }) {
                            Icon(if (musicDuration > 0) Icons.Default.Replay else Icons.Default.ChevronLeft, contentDescription = "Back")
                        }
                        FloatingActionButton(
                            onClick = { if (isPlaying) viewModel.stopAnimation() else viewModel.playAnimation() },
                            shape = CircleShape, containerColor = primaryGreen, contentColor = Color.White, modifier = Modifier.size(56.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = { if (musicDuration > 0) viewModel.skipForward() else viewModel.nextStep() }) {
                            Icon(if (musicDuration > 0) Icons.Default.Forward10 else Icons.Default.ChevronRight, contentDescription = "Forward")
                        }
                        IconButton(onClick = { viewModel.addFormation() }) {
                            Icon(Icons.Default.AddLocation, contentDescription = "Pin", tint = primaryGreen)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.prevFormation() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
                        }
                        Text("${currentIndex + 1}/${project.formations.size}", modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { viewModel.nextFormation() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidth = maxWidth
                val screenHeight = maxHeight
                
                val (drawWidth, drawHeight) = remember(screenWidth, screenHeight, project.realWidth, project.realHeight) {
                    val stageRatio = project.realWidth / project.realHeight
                    val screenRatio = screenWidth.value / screenHeight.value
                    if (stageRatio > screenRatio) screenWidth to (screenWidth / stageRatio)
                    else (screenHeight * stageRatio) to screenHeight
                }

                StageArea(
                    modifier = Modifier.fillMaxSize(),
                    drawWidth = drawWidth, drawHeight = drawHeight,
                    project = project, animatedDancers = animatedDancers,
                    isPlaying = isPlaying, videoExportProgress = videoExportProgress,
                    selectedDancerId = selectedDancerId, draggedDancerId = draggedDancerId,
                    onSelectDancer = { 
                        selectedDancerId = it
                        if (it == null) showEditPanel = false 
                    },
                    onUpdateDancerPosition = { id, x, y -> viewModel.updateDancerPosition(id, x, y) },
                    onDragStart = { draggedDancerId = it },
                    onDragEnd = { draggedDancerId = null }
                )
                
                AnimatedVisibility(
                    visible = selectedDancerId != null && !isPlaying && !showEditPanel,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { showEditPanel = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = primaryGreen, contentColor = Color.White),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Details")
                    }
                }
            }

            AnimatedVisibility(
                visible = showEditPanel && !isPlaying,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                DetailPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    formation = formation,
                    selectedDancerId = selectedDancerId,
                    viewModel = viewModel,
                    onDancerSelected = { 
                        selectedDancerId = it
                        if (it == null) showEditPanel = false
                    },
                    primaryColor = primaryGreen
                )
            }

            if (showProjectSettings) {
                ProjectSettingsDialog(
                    projectName = project.name, realWidth = project.realWidth, realHeight = project.realHeight,
                    onDismiss = { showProjectSettings = false },
                    onSave = { name, w, h -> viewModel.updateProjectSettings(name, w, h); showProjectSettings = false }
                )
            }

            videoExportProgress?.let { progress ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(progress = { progress }, color = primaryGreen)
                        Text("Exporting... ${(progress * 100).toInt()}%", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun StageArea(
    modifier: Modifier,
    drawWidth: androidx.compose.ui.unit.Dp,
    drawHeight: androidx.compose.ui.unit.Dp,
    project: Project,
    animatedDancers: List<Dancer>,
    isPlaying: Boolean,
    videoExportProgress: Float?,
    selectedDancerId: Int?,
    draggedDancerId: Int?,
    onSelectDancer: (Int?) -> Unit,
    onUpdateDancerPosition: (Int, Float, Float) -> Unit,
    onDragStart: (Int?) -> Unit,
    onDragEnd: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("FRONT / PENONTON", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("L", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(drawWidth, drawHeight)
                    .background(Color(0xFF101010))
                    .border(2.dp, Color(0xFF2E7D32).copy(0.3f))
                    .pointerInput(project.stageWidth, project.stageHeight, animatedDancers) {
                        if (isPlaying || videoExportProgress != null) return@pointerInput
                        
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val hitRadius = 120f
                            val tappedDancer = animatedDancers.find { d ->
                                val canvasX = (d.x / project.stageWidth) * size.width
                                val canvasY = (d.y / project.stageHeight) * size.height
                                val dx = canvasX - down.position.x
                                val dy = canvasY - down.position.y
                                (dx * dx + dy * dy) < hitRadius * hitRadius
                            }
                            
                            if (tappedDancer != null) {
                                onSelectDancer(tappedDancer.id)
                                onDragStart(tappedDancer.id)
                                var currentDancer = tappedDancer
                                drag(down.id) { change ->
                                    change.consume()
                                    val moveFactorX = project.stageWidth / size.width
                                    val moveFactorY = project.stageHeight / size.height
                                    val newX = currentDancer.x + (change.positionChange().x * moveFactorX)
                                    val newY = currentDancer.y + (change.positionChange().y * moveFactorY)
                                    val boundedX = newX.coerceIn(0f, project.stageWidth)
                                    val boundedY = newY.coerceIn(0f, project.stageHeight)
                                    onUpdateDancerPosition(tappedDancer.id, boundedX, boundedY)
                                    currentDancer = currentDancer.copy(x = boundedX, y = boundedY)
                                }
                                onDragEnd()
                            } else {
                                onSelectDancer(null)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY.and(0x80FFFFFF.toInt()); textSize = 24f; textAlign = android.graphics.Paint.Align.LEFT
                    }
                    for (x in 0..project.realWidth.toInt()) {
                        val xPos = (x / project.realWidth) * size.width
                        drawLine(Color.Green.copy(0.1f), Offset(xPos, 0f), Offset(xPos, size.height))
                        drawContext.canvas.nativeCanvas.drawText("${x}m", xPos + 8f, 30f, labelPaint)
                    }
                    for (y in 0..project.realHeight.toInt()) {
                        val yPos = (y / project.realHeight) * size.height
                        drawLine(Color.Green.copy(0.1f), Offset(0f, yPos), Offset(size.width, yPos))
                        drawContext.canvas.nativeCanvas.drawText("${y}m", 8f, yPos - 8f, labelPaint)
                    }
                    animatedDancers.forEach { d ->
                        val isSelected = d.id == selectedDancerId
                        val canvasX = (d.x / project.stageWidth) * size.width
                        val canvasY = (d.y / project.stageHeight) * size.height
                        if (isSelected) drawCircle(Color.Green.copy(0.3f), 60f, Offset(canvasX, canvasY))
                        drawCircle(Color(d.color), 40f, Offset(canvasX, canvasY))
                        if (isSelected) drawCircle(Color.White, 45f, Offset(canvasX, canvasY), style = Stroke(4f))
                        drawContext.canvas.nativeCanvas.drawText(d.name, canvasX, canvasY + 75f, android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE; textSize = 30f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = isSelected
                        })
                    }
                }
            }
            Spacer(Modifier.width(12.dp)); Text("R", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        Text("BACK / BELAKANG", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
    }
}

@Composable
fun DetailPanel(
    modifier: Modifier, formation: Formation, selectedDancerId: Int?, viewModel: ProjectViewModel, onDancerSelected: (Int?) -> Unit, primaryColor: Color
) {
    Surface(
        modifier = modifier, color = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp, shadowElevation = 8.dp, shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EDIT DANCER", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDancerSelected(null) }) { Icon(Icons.Default.Close, null) }
            }
            val dancer = formation.dancers.find { it.id == selectedDancerId }
            if (dancer != null) {
                OutlinedTextField(
                    value = dancer.name, onValueChange = { viewModel.updateDancerName(dancer.id, it) }, 
                    label = { Text("Dancer Name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, focusedLabelColor = primaryColor)
                )
                Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync to:", style = MaterialTheme.typography.labelMedium, color = Color.Gray); Spacer(Modifier.width(8.dp))
                    LazyRow {
                        items(formation.dancers.filter { it.id != dancer.id }) { other ->
                            SuggestionChip(onClick = { viewModel.copyDancerPosition(dancer.id, other.id) }, label = { Text(other.name) }, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.mirrorFormationHorizontal() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Icon(Icons.Default.Flip, null); Spacer(Modifier.width(4.dp)); Text("Mirror")
                    }
                    Button(onClick = { viewModel.copyFromPrevious() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Dup")
                    }
                }
                TextButton(onClick = { viewModel.removeDancer(dancer.id); onDancerSelected(null) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete")
                }
            }
        }
    }
}

@Composable
fun ProjectSettingsDialog(projectName: String, realWidth: Float, realHeight: Float, onDismiss: () -> Unit, onSave: (String, Float, Float) -> Unit) {
    var name by remember { mutableStateOf(projectName) }
    var w by remember { mutableStateOf(realWidth.toString()) }
    var h by remember { mutableStateOf(realHeight.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Layout Settings") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = w, onValueChange = { w = it }, label = { Text("Width (m)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = h, onValueChange = { h = it }, label = { Text("Height (m)") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, w.toFloatOrNull() ?: 10f, h.toFloatOrNull() ?: 10f) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun formatTime(ms: Long): String {
    val mins = (ms / 1000) / 60
    val secs = (ms / 1000) % 60
    return "%02d:%02d".format(mins, secs)
}
