package app.coordinate.lab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil

/** Marker semantics stay in the map's title/content description, not in rasterized labels. */
internal enum class MapMarkerRole { REAL, SELECTED, MOVING, START, END, VIA, LOOP, ANNOTATION }

/** The anchor is the exact geographic point, including the bitmap's transparent padding. */
internal data class MapMarkerAsset(val bitmap: Bitmap, val anchorX: Float, val anchorY: Float)

/** Small, density-aware native map art. The renderer owns caching and bitmap lifetime. */
internal object MapMarkerArt {
    private val emerald = Color.rgb(23, 99, 73)
    private val blue = Color.rgb(59, 130, 246)
    private val violet = Color.rgb(128, 96, 168)
    private val clay = Color.rgb(168, 102, 66)
    private val shadow = Color.argb(48, 16, 42, 35)

    fun create(role: MapMarkerRole, label: String = "", density: Float = 1f, accentColor: Int = emerald): MapMarkerAsset {
        val scale = if (density.isFinite() && density > 0f) density.coerceIn(.5f, 6f) else 1f
        val geometry = when (role) {
            MapMarkerRole.SELECTED -> Geometry(34f, 42f, 17f, 36f)
            MapMarkerRole.ANNOTATION -> Geometry(32f, 36f, 16f, 30f)
            MapMarkerRole.REAL, MapMarkerRole.MOVING -> Geometry(30f, 30f, 15f, 15f)
            else -> Geometry(32f, 32f, 16f, 16f)
        }
        val bitmap = Bitmap.createBitmap(
            ceil(geometry.width * scale).toInt(),
            ceil(geometry.height * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        // SDK descriptors must use our explicit pixel density rather than implicit resource scaling.
        bitmap.density = Bitmap.DENSITY_NONE
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        when (role) {
            MapMarkerRole.REAL -> drawDot(canvas, paint, blue, moving = false)
            MapMarkerRole.MOVING -> drawDot(canvas, paint, accentColor, moving = true)
            MapMarkerRole.SELECTED -> drawSelected(canvas, paint, accentColor)
            MapMarkerRole.ANNOTATION -> drawAnnotation(canvas, paint)
            else -> drawRoute(canvas, paint, role, label, accentColor)
        }
        return MapMarkerAsset(
            bitmap,
            geometry.anchorX * scale / bitmap.width,
            geometry.anchorY * scale / bitmap.height
        )
    }

    private data class Geometry(val width: Float, val height: Float, val anchorX: Float, val anchorY: Float)

    private fun drawDot(canvas: Canvas, paint: Paint, color: Int, moving: Boolean) {
        // This fixed decorative halo does not represent or imply a measured accuracy radius.
        paint.color = withAlpha(color, 26)
        canvas.drawCircle(15f, 15f, 12.5f, paint)
        paint.color = withAlpha(color, 24)
        canvas.drawCircle(15f, 15f, 9.8f, paint)
        paint.color = Color.WHITE
        paint.setShadowLayer(1.5f, 0f, .8f, shadow)
        canvas.drawCircle(15f, 15f, 7.3f, paint)
        paint.clearShadowLayer()
        paint.color = color
        canvas.drawCircle(15f, 15f, 5.3f, paint)
        if (moving) {
            // Symmetric, so the marker never advertises an invented device/course bearing.
            paint.color = Color.WHITE
            canvas.drawPath(Path().apply {
                moveTo(15f, 11.5f)
                lineTo(18.5f, 15f)
                lineTo(15f, 18.5f)
                lineTo(11.5f, 15f)
                close()
            }, paint)
        }
    }

    private fun drawSelected(canvas: Canvas, paint: Paint, accent: Int) {
        val pin = Path().apply {
            moveTo(17f, 36f)
            cubicTo(14.6f, 32.8f, 5.5f, 24.2f, 5.5f, 16f)
            cubicTo(5.5f, 9.65f, 10.65f, 4.5f, 17f, 4.5f)
            cubicTo(23.35f, 4.5f, 28.5f, 9.65f, 28.5f, 16f)
            cubicTo(28.5f, 24.2f, 19.4f, 32.8f, 17f, 36f)
            close()
        }
        fillFramed(canvas, paint, pin, accent)
        paint.color = Color.WHITE
        canvas.drawCircle(17f, 16f, 4.3f, paint)
    }

    private fun drawAnnotation(canvas: Canvas, paint: Paint) {
        val diamond = Path().apply {
            moveTo(16f, 3.8f)
            cubicTo(18.5f, 3.8f, 27f, 12.1f, 27f, 15.5f)
            cubicTo(27f, 19.2f, 19.5f, 26.2f, 16f, 30f)
            cubicTo(12.5f, 26.2f, 5f, 19.2f, 5f, 15.5f)
            cubicTo(5f, 12.1f, 13.5f, 3.8f, 16f, 3.8f)
            close()
        }
        fillFramed(canvas, paint, diamond, violet)
        paint.color = Color.WHITE
        canvas.drawPath(Path().apply {
            moveTo(12.4f, 10.4f)
            lineTo(19.6f, 10.4f)
            lineTo(19.6f, 20f)
            lineTo(16f, 17.7f)
            lineTo(12.4f, 20f)
            close()
        }, paint)
    }

    private fun drawRoute(canvas: Canvas, paint: Paint, role: MapMarkerRole, label: String, accent: Int) {
        val light = role == MapMarkerRole.VIA || role == MapMarkerRole.LOOP
        val fill = if (light) Color.WHITE else if (role == MapMarkerRole.END) clay else accent
        val outline = Path().apply { addCircle(16f, 16f, 11.4f, Path.Direction.CW) }
        fillFramed(canvas, paint, outline, fill)
        if (light) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = .9f
            paint.color = withAlpha(accent, 96)
            canvas.drawCircle(16f, 16f, 10.7f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = if (light) accent else Color.WHITE
        val number = label.trim().takeIf { it.matches(Regex("[0-9]{1,2}")) }
        if (number != null) {
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = if (number.length == 2) 11.5f else 12.5f
            val baseline = 16f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            canvas.drawText(number, 16f, baseline, paint)
            return
        }
        when (role) {
            MapMarkerRole.START -> canvas.drawPath(Path().apply {
                moveTo(13.6f, 10.9f)
                lineTo(21f, 16f)
                lineTo(13.6f, 21.1f)
                close()
            }, paint)
            MapMarkerRole.END -> {
                paint.strokeWidth = 1.8f
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(12.6f, 10.6f, 12.6f, 21.5f, paint)
                canvas.drawPath(Path().apply {
                    moveTo(13.1f, 10.8f)
                    lineTo(20.6f, 10.8f)
                    lineTo(18.8f, 14.2f)
                    lineTo(20.6f, 17.2f)
                    lineTo(13.1f, 17.2f)
                    close()
                }, paint)
            }
            MapMarkerRole.LOOP -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.9f
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(RectF(10.5f, 10.5f, 21.5f, 21.5f), -35f, 287f, false, paint)
                paint.style = Paint.Style.FILL
                canvas.drawPath(Path().apply {
                    moveTo(21.7f, 9.8f)
                    lineTo(21.4f, 15.3f)
                    lineTo(17f, 12.2f)
                    close()
                }, paint)
            }
            else -> canvas.drawCircle(16f, 16f, 3f, paint)
        }
    }

    private fun fillFramed(canvas: Canvas, paint: Paint, path: Path, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        // Keep the blur well inside every asset's transparent padding (especially route badges).
        paint.setShadowLayer(1f, 0f, .8f, shadow)
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00ffffff) or (alpha shl 24)
}
