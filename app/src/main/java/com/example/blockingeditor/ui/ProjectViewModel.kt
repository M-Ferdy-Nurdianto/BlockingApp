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

    private val audioPlayer = AudioPlayer(application)

    private val _musicDuration = MutableStateFlow(0)
    val musicDuration: StateFlow<Int> = _musicDuration.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _currentAnimatedDancers = MutableStateFlow<List<Dancer>>(emptyList())
    val currentAnimatedDancers: StateFlow<List<Dancer>> = _currentAnimatedDancers.asStateFlow()

    // GREEN THEME CONSTANT
    val primaryColor = 0xFF2E7D32 // Material Green 700

    init {
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
        ProjectStorage.saveProject(getApplication(), _project.value)
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
        _project.update { it.copy(name = name, realWidth = width, realHeight = height) }
        saveProject()
    }

    fun addDancer(name: String, color: Long) {
        _project.update { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val centerX = currentProject.stageWidth / 2f
            val centerY = currentProject.stageHeight / 2f
            
            val newDancer = Dancer(
                id = if (formation.dancers.isEmpty()) 0 else formation.dancers.maxOf { it.id } + 1,
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
        // Immediate UI update
        _currentAnimatedDancers.value = _currentAnimatedDancers.value.map {
            if (it.id == id) it.copy(x = x, y = y) else it
        }

        // Persistent update
        _project.update { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.map {
                if (it.id == id) it.copy(x = x, y = y) else it
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun removeDancer(id: Int) {
        _project.update { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.filter { it.id != id }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun addFormation() {
        _project.update { currentProject ->
            val currentTime = _currentTimeMs.value
            val dancers = _currentAnimatedDancers.value.map { it.copy() }.toMutableList()
            
            val newFormation = Formation(
                name = "Formation ${currentProject.formations.size + 1}",
                timeMs = currentTime,
                dancers = dancers
            )
            val updatedFormations = currentProject.formations.toMutableList()
            val existingIndex = updatedFormations.indexOfFirst { it.timeMs == currentTime }
            if (existingIndex != -1) {
                updatedFormations[existingIndex] = newFormation
            } else {
                updatedFormations.add(newFormation)
                updatedFormations.sortBy { it.timeMs }
            }
            currentProject.copy(formations = updatedFormations)
        }
        val foundIndex = _project.value.formations.indexOfLast { it.timeMs <= _currentTimeMs.value }
        if (foundIndex != -1) _currentFormationIndex.value = foundIndex
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

    fun updateDancerName(id: Int, newName: String) {
        _project.update { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.map {
                if (it.id == id) it.copy(name = newName) else it
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun updateDancerColor(id: Int, newColor: Long) {
        _project.update { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val updatedDancers = formation.dancers.map {
                if (it.id == id) it.copy(color = newColor) else it
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun updateFormationTime(timeMs: Long) {
        _project.update { currentProject ->
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = updatedFormations[_currentFormationIndex.value].copy(timeMs = timeMs)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun renameFormation(newName: String) {
        _project.update { currentProject ->
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = updatedFormations[_currentFormationIndex.value].copy(name = newName)
            currentProject.copy(formations = updatedFormations)
        }
    }

    fun mirrorFormationHorizontal() {
        _project.update { currentProject ->
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

    fun copyFromPrevious() {
        if (_currentFormationIndex.value > 0) {
            _project.update { currentProject ->
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
        formation.dancers.forEach { dancer ->
            paint.color = dancer.color.toInt()
            canvas.drawCircle(dancer.x, dancer.y, 45f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(dancer.x, dancer.y, 45f, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 35f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(dancer.name, dancer.x, dancer.y + 80f, paint)
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

    fun copyDancerPosition(fromId: Int, toId: Int) {
        _project.update { currentProject ->
            val formation = currentProject.formations[_currentFormationIndex.value]
            val sourceDancer = formation.dancers.find { it.id == fromId } ?: return@update currentProject
            val updatedDancers = formation.dancers.map {
                if (it.id == toId) it.copy(x = sourceDancer.x, y = sourceDancer.y) else it
            }.toMutableList()
            val updatedFormations = currentProject.formations.toMutableList()
            updatedFormations[_currentFormationIndex.value] = formation.copy(dancers = updatedDancers)
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
