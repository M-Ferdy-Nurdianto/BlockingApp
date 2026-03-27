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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.ui.text.style.TextAlign

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
    
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val projectList by viewModel.projects.collectAsState()
    
    val formation = project.formations.getOrNull(currentIndex) ?: project.formations.first()

    var showProjectGallery by remember { mutableStateOf(false) }
    var showCreateProject by remember { mutableStateOf(false) }

    var draggedDancerId by remember { mutableStateOf<Int?>(null) }
    var selectedDancerId by remember { mutableStateOf<Int?>(null) }
    var showEditPanel by remember { mutableStateOf(false) }
    var showProjectSettings by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneTargetId by remember { mutableStateOf<Int?>(null) }

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
                        IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (canUndo) primaryGreen else Color.Gray)
                        }
                        IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (canRedo) primaryGreen else Color.Gray)
                        }
                        IconButton(onClick = { viewModel.refreshProjectList(); showProjectGallery = true }) {
                            Icon(Icons.Default.Folder, contentDescription = "Projects")
                        }
                        IconButton(onClick = { showProjectSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = { viewModel.exportImage() }) { Icon(Icons.Default.Image, null) }
                        IconButton(onClick = { viewModel.exportVideo() }) { Icon(Icons.Default.Movie, null) }
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = primaryGreen)
                        }
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
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.prevFormation() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
                            }
                            Text("${currentIndex + 1}/${project.formations.size}", modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { viewModel.nextFormation() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                            }
                            if (project.formations.size > 1 && !isPlaying) {
                                IconButton(onClick = { viewModel.removeFormation() }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Frame", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (selectedDancerId != null && !isPlaying) {
                            FilledTonalIconButton(
                                onClick = { showEditPanel = !showEditPanel },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (showEditPanel) primaryGreen.copy(alpha = 0.3f) else primaryGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Dancer")
                            }
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
                        .heightIn(max = 350.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    formation = formation,
                    selectedDancerId = selectedDancerId,
                    viewModel = viewModel,
                    onDancerSelected = { 
                        selectedDancerId = it
                        if (it == null) showEditPanel = false
                    },
                    primaryColor = primaryGreen,
                    onShowCloneDialog = { showCloneDialog = true }
                )
            }

            if (showProjectSettings) {
                ProjectSettingsDialog(
                    projectName = project.name, realWidth = project.realWidth, realHeight = project.realHeight,
                    onDismiss = { showProjectSettings = false },
                    onSave = { name, w, h -> viewModel.updateProjectSettings(name, w, h); showProjectSettings = false }
                )
            }

            if (showProjectGallery) {
                ProjectGalleryDialog(
                    projects = projectList,
                    currentProject = project.name,
                    onDismiss = { showProjectGallery = false },
                    onSelect = { viewModel.switchProject(it); showProjectGallery = false },
                    onCreateNew = { showCreateProject = true; showProjectGallery = false },
                    onDelete = { viewModel.deleteProject(it) }
                )
            }

            if (showCreateProject) {
                var newName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateProject = false },
                    title = { Text("New Project") },
                    text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Project Name") }) },
                    confirmButton = { 
                        Button(onClick = { viewModel.createNewProject(newName); showCreateProject = false }, colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)) {
                            Text("Create")
                        }
                    },
                    dismissButton = { TextButton(onClick = { showCreateProject = false }) { Text("Cancel") } }
                )
            }

            if (showHelpDialog) {
                HelpDialog(onDismiss = { showHelpDialog = false })
            }

            if (showCloneDialog && selectedDancerId != null) {
                val sourceDancer = formation.dancers.find { it.id == selectedDancerId }
                if (sourceDancer != null) {
                    CloneMotionDialog(
                        dancers = formation.dancers.filter { it.id != selectedDancerId },
                        onDismiss = { showCloneDialog = false },
                        onConfirm = { targetId, mirrored ->
                            viewModel.cloneMovement(selectedDancerId!!, targetId, mirrored)
                            showCloneDialog = false
                        }
                    )
                }
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
    val density = LocalDensity.current
    val currentDancers by rememberUpdatedState(animatedDancers)
    val currentDraggedId by rememberUpdatedState(draggedDancerId)
    
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
                    .pointerInput(project.stageWidth, project.stageHeight) {
                        if (isPlaying || videoExportProgress != null) return@pointerInput
                        detectTapGestures(onTap = { offset ->
                            val hitRadius = 100f
                            val tapped = currentDancers.find { d ->
                                val canvasX = (d.x / project.stageWidth) * size.width
                                val canvasY = (d.y / project.stageHeight) * size.height
                                val dx = canvasX - offset.x
                                val dy = canvasY - offset.y
                                (dx * dx + dy * dy) < hitRadius * hitRadius
                            }
                            onSelectDancer(tapped?.id)
                        })
                    }
                    .pointerInput(project.stageWidth, project.stageHeight) {
                        if (isPlaying || videoExportProgress != null) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hitRadius = 100f
                                val id = currentDancers.find { d ->
                                    val canvasX = (d.x / project.stageWidth) * size.width
                                    val canvasY = (d.y / project.stageHeight) * size.height
                                    val dx = canvasX - offset.x
                                    val dy = canvasY - offset.y
                                    (dx * dx + dy * dy) < hitRadius * hitRadius
                                }?.id
                                onDragStart(id)
                                if (id != null) onSelectDancer(id)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentDraggedId?.let { id ->
                                    val dancer = currentDancers.find { it.id == id }
                                    dancer?.let {
                                        val moveFactorX = project.stageWidth / size.width
                                        val moveFactorY = project.stageHeight / size.height
                                        val nextX = (it.x + (dragAmount.x * moveFactorX)).coerceIn(0f, project.stageWidth)
                                        val nextY = (it.y + (dragAmount.y * moveFactorY)).coerceIn(0f, project.stageHeight)
                                        onUpdateDancerPosition(id, nextX, nextY)
                                    }
                                }
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            ) {
                // BACKGROUND GRID (Rarely changes)
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
                }
                
                // MIRROR LINKS
                Canvas(modifier = Modifier.fillMaxSize()) {
                    animatedDancers.forEach { dancer ->
                        if (dancer.mirrorOfId != null && (dancer.id < (dancer.mirrorOfId ?: 0))) {
                            val partner = animatedDancers.find { it.id == dancer.mirrorOfId }
                            if (partner != null) {
                                val startX = (dancer.x / project.stageWidth) * size.width
                                val startY = (dancer.y / project.stageHeight) * size.height
                                val endX = (partner.x / project.stageWidth) * size.width
                                val endY = (partner.y / project.stageHeight) * size.height
                                drawLine(
                                    color = Color.White.copy(alpha = 0.3f),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY),
                                    strokeWidth = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            }
                        }
                    }
                }

                // DANCERS (Optimized with graphicsLayer)
                animatedDancers.forEach { dancer ->
                    key(dancer.id) {
                        DancerIcon(
                            dancer = dancer,
                            isSelected = dancer.id == selectedDancerId,
                            stageWidth = project.stageWidth,
                            stageHeight = project.stageHeight,
                            drawWidth = with(density) { drawWidth.toPx() },
                            drawHeight = with(density) { drawHeight.toPx() }
                        )
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
fun DancerIcon(
    dancer: Dancer,
    isSelected: Boolean,
    stageWidth: Float,
    stageHeight: Float,
    drawWidth: Float,
    drawHeight: Float
) {
    val canvasX = (dancer.x / stageWidth) * drawWidth
    val canvasY = (dancer.y / stageHeight) * drawHeight
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .graphicsLayer {
                translationX = canvasX - with(density) { 40.dp.toPx() }
                translationY = canvasY - with(density) { 40.dp.toPx() }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isSelected) drawCircle(Color.Green.copy(0.3f), 60f)
            drawCircle(Color(dancer.color), 40f)
            if (isSelected) drawCircle(Color.White, 45f, style = Stroke(4f))
            if (dancer.mirrorOfId != null) drawCircle(Color.Cyan.copy(0.7f), 48f, style = Stroke(3f))
            
            drawContext.canvas.nativeCanvas.drawText(
                dancer.name, 40f, 115f, android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE; textSize = 30f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = isSelected
                }
            )
        }
    }
}

@Composable
fun DetailPanel(
    modifier: Modifier, formation: Formation, selectedDancerId: Int?, viewModel: ProjectViewModel, onDancerSelected: (Int?) -> Unit, primaryColor: Color, onShowCloneDialog: () -> Unit
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
                if (dancer.mirrorOfId != null) {
                    Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Linked to:", style = MaterialTheme.typography.labelMedium, color = Color.Gray); Spacer(Modifier.width(8.dp))
                        val sourceName = formation.dancers.find { it.id == dancer.mirrorOfId }?.name ?: "?"
                        AssistChip(
                            onClick = { viewModel.clearMirrorLink(dancer.id) },
                            label = { Text("🔗 $sourceName") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = primaryColor.copy(alpha = 0.2f)),
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isLinked = dancer.mirrorOfId != null
                    Button(
                        onClick = { if (isLinked) viewModel.clearMirrorLink(dancer.id) else viewModel.addMirrorPartner(dancer.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLinked) MaterialTheme.colorScheme.secondary else primaryColor)
                    ) {
                        Icon(if (isLinked) Icons.Default.LinkOff else Icons.Default.Link, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLinked) "Break Link" else "Mirror Link")
                    }
                }
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.mirrorFormationHorizontal() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Icon(Icons.Default.Flip, null); Spacer(Modifier.width(4.dp)); Text("H-Flip")
                    }
                    Button(onClick = { viewModel.mirrorFormationVertical() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Icon(Icons.Default.Flip, null, modifier = Modifier.graphicsLayer { rotationZ = 90f }); Spacer(Modifier.width(4.dp)); Text("V-Flip")
                    }
                }
                Button(
                    onClick = onShowCloneDialog,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clone Motion to Another Dancer")
                }
                Text("PRESETS", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { viewModel.applyCirclePreset() }, label = { Text("Circle") }, leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = { viewModel.applyLinePreset() }, label = { Text("Line") }, leadingIcon = { Icon(Icons.Default.LinearScale, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = { viewModel.copyFromPrevious() }, label = { Text("Dup Prev") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) })
                }
                TextButton(onClick = { viewModel.removeDancer(dancer.id); onDancerSelected(null) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete Dancer")
                }
            }
        }
    }
}

@Composable
fun ProjectGalleryDialog(projects: List<String>, currentProject: String, onDismiss: () -> Unit, onSelect: (String) -> Unit, onCreateNew: () -> Unit, onDelete: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Gallery") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Button(onClick = onCreateNew, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create New Project")
                }
                Divider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(projects) { filename ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSelect(filename) }, modifier = Modifier.weight(1f)) {
                                Text(filename.replace(".json", ""), textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                            IconButton(onClick = { onDelete(filename) }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
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

@Composable
fun CloneMotionDialog(dancers: List<Dancer>, onDismiss: () -> Unit, onConfirm: (Int, Boolean) -> Unit) {
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var mirrored by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone Motion") },
        text = {
            Column {
                Text("Pilih penari target yang akan mengikuti gerakan penari ini di SEMUA frame:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(dancers) { dancer ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedId = dancer.id }.padding(8.dp).background(if (selectedId == dancer.id) Color.LightGray.copy(0.3f) else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedId == dancer.id, onClick = { selectedId = dancer.id })
                            Text(dancer.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mirrored, onCheckedChange = { mirrored = it })
                    Text("Mirror Position (Symmetry)")
                }
            }
        },
        confirmButton = {
            Button(onClick = { selectedId?.let { onConfirm(it, mirrored) } }, enabled = selectedId != null) { Text("Clone Now") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Panduan Fitur") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpItem("🔄 Dup Prev", "Menyalin posisi semua penari dari frame sebelumnya ke frame saat ini. Cocok untuk membuat transisi bertahap.")
                HelpItem("🔗 Mirror Link", "Menghubungkan dua penari secara simetris. Jika satu digeser, pasangannya akan bergerak otomatis di sisi berlawanan.")
                HelpItem("👯 Clone Motion", "Menyalin seluruh pola gerakan seorang penari ke penari lain di SEMUA frame. Sangat berguna untuk formasi grup yang simetris.")
                HelpItem("⏱️ Manual Mode", "Gunakan Slider atau tombol skip untuk berpindah antar detik. Tekan 'Pin' untuk mengunci formasi baru.")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Mengerti") } }
    )
}

@Composable
fun HelpItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color(0xFF2E7D32))
        Text(desc, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatTime(ms: Long): String {
    val mins = (ms / 1000) / 60
    val secs = (ms / 1000) % 60
    return "%02d:%02d".format(mins, secs)
}
