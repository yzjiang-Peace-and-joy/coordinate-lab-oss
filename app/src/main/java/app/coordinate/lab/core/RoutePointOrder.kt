package app.coordinate.lab.core

import kotlin.math.roundToInt

object RoutePointOrder {
    fun target(start: Int,deltaPixels: Float,slotPixels: Float,count: Int): Int {
        require(count>0 && start in 0 until count && slotPixels.isFinite() && slotPixels>0 && deltaPixels.isFinite())
        return (start.toLong()+(deltaPixels/slotPixels).roundToInt()).coerceIn(0L,(count-1).toLong()).toInt()
    }
    fun <T> move(points: List<T>,from: Int,to: Int): List<T> {
        require(from in points.indices && to in points.indices)
        return points.toMutableList().apply {add(to,removeAt(from))}.toList()
    }
}
