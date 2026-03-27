package com.example.blockingeditor.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blockingeditor.animation.AnimationEngine
import com.example.blockingeditor.model.Dancer
import com.example.blockingeditor.model.Formation
import com.example.blockingeditor.model.Project
import com.example.blockingeditor.storage.ProjectStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.net.Uri
import com.example.blockingeditor.audio.AudioPlayer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val _project = MutableStateFlow(
        ProjectStorage.loadProject(application) ?: Project(
            name = "New Project",
            formations = mutableListOf(
                Formation(
                    name = "Formation 1",
                    timeMs = 0L,
                    dancers = mutableListOf()
                )
            )
        )
    )
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _currentFormationIndex = MutableStateFlow(0)
    val currentFormationIndex: StateFlow<Int> = _currentFormationIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val undoStack = mutableListOf<Project>()
    private val redoStack = mutableListOf<Project>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var currentProjectFilename = "project.json"

    private val _projects = MutableStateFlow<List<String>>(emptyList())
    val projects: StateFlow<List<String>> = _projects.asStateFlow()

    private val audioPlayer = AudioPlayer(application)

    private val _musicDuration = MutableStateFlow(0)
    val musicDuration: StateFlow<Int> = _musicDuration.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _currentAnimatedDancers = MutableStateFlow<List<Dancer>>(emptyList())
    val currentAnimatedDancers: StateFlow<List<Dancer>> = _currentAnimatedDancers.asStateFlow()

    // GREEN THEME CONSTANT
    val primaryColor = 0xFF2E7D32 // Material Green 700

    private fun pushToUndo(project: Project) {
        undoStack.add(project.copy(formations = project.formations.map { f -> f.copy(dancers = f.dancers.map { it.copy() }.toMutableList()) }.toMutableList()))
        if (undoStack.size > 50) undoStack.removeAt(0)
        _canUndo.value = undoStack.isNotEmpty()
        redoStack.clear()
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _project.value
        redoStack.add(current)
        _canRedo.value = true
        
        val previous = undoStack.removeAt(undoStack.size - 1)
        _project.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        updateAnimatedDancersAtTime(_currentTimeMs.value)
        saveProject()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _project.value
        undoStack.add(current)
        _canUndo.value = true
        
        val next = redoStack.removeAt(redoStack.size - 1)
        _project.value = next
        _canRedo.value = redoStack.isNotEmpty()
        updateAnimatedDancersAtTime(_currentTimeMs.value)
        saveProject()
    }

    private fun updateProject(action: (Project) -> Project) {
        val oldProject = _project.value
        pushToUndo(oldProject)
        val newProject = action(oldProject)
        _project.value = newProject
        saveProject()
    }

    init {
        refreshProjectList()
        // Init music if exists
        _project.value.musicUri?.let {
            audioPlayer.load(it)
            _musicDuration.value = audioPlayer.getDuration()
        }

        // Sync animated dancers with current time
        viewModelScope.launch {
            _currentTimeMs.collect { timeMs ->
                updateAnimatedDancersAtTime(timeMs)
            }
        }
        
        // Handle new formations sorting
        viewModelScope.launch {
            _project.collect { project ->
                project.formations.sortBy { it.timeMs }
            }
        }
        
        // Ensure there's at least one formation at 0ms
        if (_project.value.formations.none { it.timeMs == 0L }) {
            _project.update { current ->
                val updatedFormations = current.formations.toMutableList()
                if (updatedFormations.isEmpty()) {
                    updatedFormations.add(Formation("Start", 0L, mutableListOf()))
                } else {
                    val first = updatedFormations.first()
                    updatedFormations.add(0, Formation("Start", 0L, first.dancers.map { it.copy() }.toMutableList()))
                }
                current.copy(formations = updatedFormations)
            }
        }
    }

    fun setMusic(uri: Uri) {
        val uriString = uri.toString()
        _project.update { it.copy(musicUri = uriString) }
        audioPlayer.load(uriString)
        _musicDuration.value = audioPlayer.getDuration()
        saveProject()
    }

    fun saveProject() {
        ProjectStorage.saveProject(getApplication(), _project.value, currentProjectFilename)
    }

    fun refreshProjectList() {
        _projects.value = ProjectStorage.listProjects(getApplication())
    }

    fun switchProject(filename: String) {
        val loaded = ProjectStorage.loadProject(getApplication(), filename)
        if (loaded != null) {
            currentProjectFilename = filename
            _project.value = loaded
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _musicDuration.value = 0
            _currentTimeMs.value = 0L
            loaded.musicUri?.let {
                audioPlayer.load(it)
                _musicDuration.value = audioPlayer.getDuration()
            }
            updateAnimatedDancersAtTime(0)
        }
    }

    fun createNewProject(name: String) {
        val newProject = Project(
            name = name,
            formations = mutableListOf(Formation("Start", 0L, mutableListOf()))
        )
        val filename = "${name.replace(" ", "_")}_${System.currentTimeMillis()}.json"
        _project.value = newProject
        currentProjectFilename = filename
        saveProject()
        refreshProjectList()
    }

    fun deleteProject(filename: String) {
        ProjectStorage.deleteProject(getApplication(), filename)
        refreshProjectList()
    }

    fun playAnimation() {
        if (_project.value.formations.isEmpty()) return
        
        viewModelScope.launch {
            _isPlaying.value = true
            
            // If manual mode, assume 5 min duration if no music
            val duration = if (audioPlayer.getDuration() > 0) audioPlayer.getDuration() else 300000 
            
            if (audioPlayer.getDuration() > 0) audioPlayer.play()
            
            while (_isPlaying.value) {
                if (audioPlayer.getDuration() > 0) {
                    val currentPosMs = audioPlayer.getCurrentPosition().toLong()
                    _currentTimeMs.value = currentPosMs
                    if (!audioPlayer.isPlaying()) break
                } else {
                    // Manual mode auto-playback increment
                    _currentTimeMs.value = (_currentTimeMs.value + 16).coerceAtMost(duration.toLong())
                    if (_currentTimeMs.value >= duration) break
                }
                
                // Find current formation index for UI
                val foundIndex = _project.value.formations.indexOfLast { it.timeMs <= _currentTimeMs.value }
                if (foundIndex != -1) _currentFormationIndex.value = foundIndex
                
                delay(16) // ~60fps
            }
            
            _isPlaying.value = false
            if (audioPlayer.getDuration() > 0) audioPlayer.pause()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }

    fun stopAnimation() {
        _isPlaying.value = false
        audioPlayer.pause()
    }

    fun seekTo(msec: Int) {
        if (audioPlayer.getDuration() > 0) audioPlayer.seekTo(msec)
        _currentTimeMs.value = msec.toLong()
        
        val foundIndex = _project.value.formations.indexOfLast { it.timeMs <= msec.toLong() }
        if (foundIndex != -1) _currentFormationIndex.value = foundIndex
    }

    fun skipBackward() {
        val nextPos = (_currentTimeMs.value.toInt() - 5000).coerceAtLeast(0)
        seekTo(nextPos)
    }

    fun skipForward() {
        val duration = if (audioPlayer.getDuration() > 0) audioPlayer.getDuration() else 300000
        val nextPos = (_currentTimeMs.value.toInt() + 5000).coerceAtMost(duration)
        seekTo(nextPos)
    }

    // New: Step by step navigation for manual mode
    fun nextStep() {
        val duration = if (audioPlayer.getDuration() > 0) audioPlayer.getDuration() else 300000
        val nextPos = (_currentTimeMs.value.toInt() + 500).coerceAtMost(duration)
        seekTo(nextPos)
    }

    fun prevStep() {
        val nextPos = (_currentTimeMs.value.toInt() - 500).coerceAtLeast(0)
        seekTo(nextPos)
    }

    private fun updateAnimatedDancersAtTime(timeMs: Long) {
        val formations = _project.value.formations
        if (formations.isEmpty()) return

        val prev = formations.findLast { it.timeMs <= timeMs } ?: formations.first()
        val next = formations.find { it.timeMs > timeMs }

        if (next == null) {
            _currentAnimatedDancers.value = prev.dancers.map { it.copy() }
        } else {
            val duration = next.timeMs - prev.timeMs
            val t = if (duration == 0L) 1f else (timeMs - prev.timeMs).toFloat() / duration
            _currentAnimatedDancers.value = AnimationEngine.interpolateDancers(
                prev.dancers,
                next.dancers,
                t
            )
        }
    }

    fun updateProjectSettings(name: String, width: Float, height: Float) {
        updateProject { it.copy(name = name, realWidth = width, realHeight = height) }
    }

    fun addDancer(name: String, color: Long) {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val centerX = currentProject.stageWidth / 2f
            val centerY = currentProject.stageHeight / 2f
            
            val newId = (formation.dancers.maxOfOrNull { it.id } ?: 0) + 1
            val newDancer = Dancer(
                id = newId,
                name = name,
                color = color,
                x = centerX,
                y = centerY
            )
            val updatedDancers = formation.dancers.toMutableList().apply { add(newDancer) }
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun updateDancerPosition(id: Int, x: Float, y: Float) {
        val stageWidth = _project.value.stageWidth
        
        // Immediate UI update for smooth dragging (outside undo/redo for performance)
        _currentAnimatedDancers.value = _currentAnimatedDancers.value.map { d ->
            when {
                d.id == id -> d.copy(x = x, y = y)
                d.mirrorOfId == id -> d.copy(x = stageWidth - x, y = y)
                else -> d
            }
        }

        // Persistent update with history
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.map { d ->
                when {
                    d.id == id -> d.copy(x = x, y = y)
                    d.mirrorOfId == id -> d.copy(x = currentProject.stageWidth - x, y = y)
                    else -> d
                }
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun removeDancer(id: Int) {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            // Filter out the dancer AND clear any mirror links pointing to it
            val updatedDancers = formation.dancers.filter { it.id != id }.map { d ->
                if (d.mirrorOfId == id) d.copy(mirrorOfId = null) else d
            }.toMutableList()
            
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun addFormation() {
        updateProject { currentProject ->
            var targetTime = _currentTimeMs.value
            val dancers = _currentAnimatedDancers.value.map { it.copy() }.toMutableList()
            
            while (currentProject.formations.any { it.timeMs == targetTime }) {
                targetTime += 1000L
            }
            
            val newFormation = Formation(
                name = "Formation ${currentProject.formations.size + 1}",
                timeMs = targetTime,
                dancers = dancers
            )
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations.add(newFormation)
            updatedFormations.sortBy { it.timeMs }
            currentProject.copy(formations = updatedFormations)
        }
        _currentFormationIndex.value = _project.value.formations.indexOfLast { it.timeMs <= _currentTimeMs.value + 1000L }.coerceAtLeast(0)
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun removeFormation() {
        if (_project.value.formations.size <= 1) return
        updateProject { currentProject ->
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations.removeAt(_currentFormationIndex.value)
            currentProject.copy(formations = updatedFormations)
        }
        _currentFormationIndex.value = (_currentFormationIndex.value - 1).coerceAtLeast(0)
        seekTo(_project.value.formations[_currentFormationIndex.value].timeMs.toInt())
    }

    fun updateDancerName(id: Int, newName: String) {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.map {
                if (it.id == id) it.copy(name = newName) else it
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun mirrorFormationHorizontal() {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val stageWidth = currentProject.stageWidth
            val updatedDancers = formation.dancers.map { seeker ->
                seeker.copy(x = stageWidth - seeker.x)
            }.toMutableList()
            
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun mirrorFormationVertical() {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val stageHeight = currentProject.stageHeight
            val updatedDancers = formation.dancers.map { seeker ->
                seeker.copy(y = stageHeight - seeker.y)
            }.toMutableList()
            
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }


    fun clearMirrorLink(dancerId: Int) {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.map { d ->
                if (d.id == dancerId) d.copy(mirrorOfId = null)
                else if (d.mirrorOfId == dancerId) d.copy(mirrorOfId = null)
                else d
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun addMirrorPartner(dancerId: Int) {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val dancer = formation.dancers.find { it.id == dancerId } ?: return@updateProject currentProject
            
            val mirrorX = currentProject.stageWidth - dancer.x
            val mirrorY = dancer.y
            
            // Find existing dancer nearby mirrored pos
            val existingMirror = formation.dancers.find { 
                it.id != dancerId && abs(it.x - mirrorX) < 50f && abs(it.y - mirrorY) < 50f 
            }
            
            val updatedFormations = currentProject.formations.toMutableList()
            val updatedDancers = formation.dancers.toMutableList()
            
            if (existingMirror != null) {
                // Link them
                updatedDancers.replaceAll { d ->
                    when (d.id) {
                        dancerId -> d.copy(mirrorOfId = existingMirror.id, x = mirrorX, y = mirrorY)
                        existingMirror.id -> d.copy(mirrorOfId = dancerId, x = currentProject.stageWidth - mirrorX, y = mirrorY)
                        else -> d
                    }
                }
            } else {
                // Create new mirrored dancer
                val newId = (updatedDancers.maxOfOrNull { it.id } ?: 0) + 1
                val mirroredDancer = Dancer(
                    id = newId,
                    name = "${dancer.name} (M)",
                    color = dancer.color,
                    x = mirrorX,
                    y = mirrorY,
                    mirrorOfId = dancerId
                )
                updatedDancers.add(mirroredDancer)
                updatedDancers.replaceAll { if (it.id == dancerId) it.copy(mirrorOfId = newId) else it }
            }
            
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun nextFormation() {
        if (_currentFormationIndex.value < _project.value.formations.size - 1) {
            _currentFormationIndex.value++
            seekTo(_project.value.formations[_currentFormationIndex.value].timeMs.toInt())
        }
    }

    fun prevFormation() {
        if (_currentFormationIndex.value > 0) {
            _currentFormationIndex.value--
            seekTo(_project.value.formations[_currentFormationIndex.value].timeMs.toInt())
        }
    }

    fun copyFromPrevious() {
        if (_currentFormationIndex.value > 0) {
            updateProject { currentProject ->
                val prevFormation = currentProject.formations[_currentFormationIndex.value - 1]
                val currentFormation = currentProject.formations[_currentFormationIndex.value]
                val updatedFormations = currentProject.formations.toMutableList()
                updatedFormations[_currentFormationIndex.value] = currentFormation.copy(
                    dancers = prevFormation.dancers.map { it.copy() }.toMutableList()
                )
                currentProject.copy(formations = updatedFormations)
            }
            updateAnimatedDancersAtTime(_currentTimeMs.value)
        }
    }

    fun applyCirclePreset() {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            if (formation.dancers.isEmpty()) return@updateProject currentProject
            
            val centerX = currentProject.stageWidth / 2f
            val centerY = currentProject.stageHeight / 2f
            val radius = 300f
            val updatedDancers = formation.dancers.mapIndexed { index, dancer ->
                val angle = (2 * PI * index / formation.dancers.size).toFloat()
                dancer.copy(
                    x = centerX + radius * cos(angle.toDouble()).toFloat(),
                    y = centerY + radius * sin(angle.toDouble()).toFloat()
                )
            }.toMutableList()
            
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun applyLinePreset() {
        updateProject { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            if (formation.dancers.isEmpty()) return@updateProject currentProject
            
            val centerY = currentProject.stageHeight / 2f
            val startX = 200f
            val endX = currentProject.stageWidth - 200f
            val stepX = if (formation.dancers.size > 1) (endX - startX) / (formation.dancers.size - 1) else 0f
            
            val updatedDancers = formation.dancers.mapIndexed { index, dancer ->
                dancer.copy(x = startX + index * stepX, y = centerY)
            }.toMutableList()
            
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    fun exportImage() {
        val formation = _project.value.formations[_currentFormationIndex.value]
        val width = 1080
        val height = 1920
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        canvas.drawColor(android.graphics.Color.BLACK)
        paint.color = android.graphics.Color.DKGRAY
        paint.strokeWidth = 1f
        for (x in 0..width step 100) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        for (y in 0..height step 100) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        // Fix scaling for image export
        val scaleX = width.toFloat() / _project.value.stageWidth.coerceAtLeast(1f)
        val scaleY = height.toFloat() / _project.value.stageHeight.coerceAtLeast(1f)

        formation.dancers.forEach { dancer ->
            val drawX = dancer.x * scaleX
            val drawY = dancer.y * scaleY
            
            paint.color = dancer.color.toInt()
            canvas.drawCircle(drawX, drawY, 45f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(drawX, drawY, 45f, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 35f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(dancer.name, drawX, drawY + 80f, paint)
        }
        try {
            val file = File(getApplication<Application>().cacheDir, "formation.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(Intent.createChooser(intent, "Share Formation").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private val _videoExportProgress = MutableStateFlow<Float?>(null)
    val videoExportProgress: StateFlow<Float?> = _videoExportProgress.asStateFlow()

    fun exportVideo() {
        viewModelScope.launch {
            _videoExportProgress.value = 0f
            com.example.blockingeditor.export.VideoExporter.exportProjectToVideo(
                context = getApplication(),
                project = _project.value,
                onProgress = { progress -> _videoExportProgress.value = progress },
                onComplete = { file ->
                    _videoExportProgress.value = null
                    shareVideo(file)
                }
            )
        }
    }


    fun cloneMovement(sourceId: Int, targetId: Int, mirrored: Boolean) {
        updateProject { currentProject ->
            val stageWidth = currentProject.stageWidth
            val updatedFormations = currentProject.formations.map { formation ->
                val sourceDancer = formation.dancers.find { it.id == sourceId }
                if (sourceDancer != null) {
                    val updatedDancers = formation.dancers.map { target ->
                        if (target.id == targetId) {
                            if (mirrored) {
                                target.copy(x = stageWidth - sourceDancer.x, y = sourceDancer.y)
                            } else {
                                target.copy(x = sourceDancer.x, y = sourceDancer.y)
                            }
                        } else {
                            target
                        }
                    }.toMutableList()
                    formation.copy(dancers = updatedDancers)
                } else {
                    formation
                }
            }.toMutableList()
            currentProject.copy(formations = updatedFormations)
        }
        updateAnimatedDancersAtTime(_currentTimeMs.value)
    }

    private fun shareVideo(file: File) {
        val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(Intent.createChooser(intent, "Share Animation").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
