package com.example.blockingeditor.model

import kotlinx.serialization.Serializable

@Serializable
data class Dancer(
    val id: Int,
    var name: String,
    var color: Long,
    var x: Float,
    var y: Float
)

@Serializable
data class Formation(
    val name: String,
    val timeMs: Long, // Pin this formation to this time in the music
    val dancers: MutableList<Dancer>
)

@Serializable
data class Project(
    val name: String,
    val stageWidth: Float = 1080f,
    val stageHeight: Float = 1920f,
    var realWidth: Float = 10f, // 10 meters default
    var realHeight: Float = 10f, // 10 meters default
    var musicUri: String? = null,
    val formations: MutableList<Formation>
)
