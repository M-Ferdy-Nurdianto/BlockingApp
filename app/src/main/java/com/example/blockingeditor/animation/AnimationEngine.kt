package com.example.blockingeditor.animation

import com.example.blockingeditor.model.Dancer

object AnimationEngine {
    fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    fun interpolateDancers(
        startDancers: List<Dancer>,
        endDancers: List<Dancer>,
        t: Float
    ): List<Dancer> {
        val endMap = endDancers.associateBy { it.id }
        return startDancers.map { startDancer ->
            val endDancer = endMap[startDancer.id] ?: startDancer
            startDancer.copy(
                x = lerp(startDancer.x, endDancer.x, t),
                y = lerp(startDancer.y, endDancer.y, t)
            )
        }
    }
}
