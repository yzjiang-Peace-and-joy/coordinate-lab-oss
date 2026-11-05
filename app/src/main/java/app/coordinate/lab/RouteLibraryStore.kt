package app.coordinate.lab

import app.coordinate.lab.core.GeoPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/** Bounded, versioned binary records. Doubles are stored exactly, not rounded coordinates. */
object RouteLibraryCodec {
    private const val MAGIC = 0x43524C31
    private const val VERSION = 1
    const val MAX_RECORD_BYTES = 256_000

    private fun envelope(kind: Int, writer: DataOutputStream.() -> Unit): ByteArray {
        val payload = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { it.writer() } }.toByteArray()
        require(payload.size <= MAX_RECORD_BYTES - 24)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(kind); out.writeInt(payload.size)
                out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
            }
        }.toByteArray()
    }

    private fun <T> open(bytes: ByteArray, kind: Int, reader: DataInputStream.() -> T): T {
        require(bytes.size in 24..MAX_RECORD_BYTES) { "收藏文件大小不可用" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION && input.readInt() == kind)
            val size = input.readInt(); val checksum = input.readLong()
            require(size == bytes.size - 24)
            val payload = ByteArray(size); input.readFully(payload)
            require(CRC32().apply { update(payload) }.value == checksum) { "收藏文件校验失败" }
            DataInputStream(ByteArrayInputStream(payload)).use { record ->
                val value = record.reader(); require(record.available() == 0); value
            }
        }
    }

    private fun DataOutputStream.point(point: GeoPoint) { writeDouble(point.latitude); writeDouble(point.longitude) }
    private fun DataInputStream.point() = GeoPoint(readDouble(), readDouble())
    private fun DataOutputStream.place(place: Place) {
        writeUTF(place.name); writeUTF(place.city); point(place.point); writeUTF(place.detail)
    }
    private fun DataInputStream.place() = RouteLibraryPolicy.validatePlace(Place(readUTF(), readUTF(), point(), readUTF()))

    fun encodeRoute(route: SavedRoute): ByteArray {
        val safe = RouteLibraryPolicy.validate(route)
        return envelope(1) {
            writeUTF(safe.id); writeUTF(safe.name); writeUTF(safe.kind.name); writeLong(safe.createdAt)
            writeBoolean(safe.road); writeBoolean(safe.closed); writeDouble(safe.speedKph); writeBoolean(safe.previewGeometryReady)
            writeInt(safe.waypoints.size); safe.waypoints.forEach { place(it) }
            writeInt(safe.points.size); safe.points.forEach { point(it) }
        }
    }

    fun decodeRoute(bytes: ByteArray): SavedRoute = open(bytes, 1) {
        val id = readUTF(); val name = readUTF(); val kind = SavedRouteKind.valueOf(readUTF()); val time = readLong()
        val road = readBoolean(); val closed = readBoolean(); val speed = readDouble(); val ready = readBoolean()
        val waypointCount = readInt(); require(waypointCount in 0..20)
        val waypoints = List(waypointCount) { place() }
        val pointCount = readInt(); require(pointCount in 2..RouteLibraryPolicy.MAX_TRACE_POINTS)
        val points = List(pointCount) { point() }
        RouteLibraryPolicy.validate(SavedRoute(id, name, kind, waypoints, points, road, closed, speed, time, ready))
    }

    fun encodeMarker(marker: Place): ByteArray = envelope(2) { place(RouteLibraryPolicy.validatePlace(marker)) }
    fun decodeMarker(bytes: ByteArray): Place = open(bytes, 2) { place() }
}

data class RouteLibrarySnapshot(val routes: List<SavedRoute>, val markers: List<Place>, val unreadableRecords: Int = 0)

/** One atomic file per record keeps a damaged route from invalidating the rest of the library.
 * This store never reads, writes, or clears the app's Key/preferences file. */
class RouteLibraryStore(private val directory: File) {
    private val routeDirectory = File(directory, "routes")
    private val markerDirectory = File(directory, "markers")

    @Synchronized fun load(): RouteLibrarySnapshot {
        var unreadable = 0
        val routes = files(routeDirectory).mapNotNull { file ->
            runCatching {
                RouteLibraryCodec.decodeRoute(readBounded(file)).also { require(file.name == "${it.id}.bin") }
            }.getOrElse { unreadable++; null }
        }.sortedByDescending { it.createdAt }
        val accepted = mutableListOf<SavedRoute>()
        var total = 0
        routes.forEach { route ->
            if (accepted.size < RouteLibraryPolicy.MAX_ROUTES && total + route.points.size <= RouteLibraryPolicy.MAX_TOTAL_POINTS) {
                accepted.add(route); total += route.points.size
            } else unreadable++
        }
        val markers = files(markerDirectory).mapNotNull { file ->
            runCatching {
                RouteLibraryCodec.decodeMarker(readBounded(file)).also { require(file.name == markerFileName(it.id)) }
            }.getOrElse { unreadable++; null }
        }.distinctBy { it.id }.take(RouteLibraryPolicy.MAX_MARKERS)
        return RouteLibrarySnapshot(accepted, markers, unreadable)
    }

    @Synchronized fun writeRoute(route: SavedRoute) {
        val safe = RouteLibraryPolicy.validate(route)
        val existing = load().routes.filterNot { it.id == safe.id }
        RouteLibraryPolicy.add(existing, safe)
        writeAtomic(File(routeDirectory, "${safe.id}.bin"), RouteLibraryCodec.encodeRoute(safe))
    }

    @Synchronized fun deleteRoute(id: String) {
        require(runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false))
        Files.deleteIfExists(File(routeDirectory, "$id.bin").toPath())
    }

    @Synchronized fun writeMarker(marker: Place) {
        RouteLibraryPolicy.validatePlace(marker)
        val existing = load().markers
        require(existing.any { it.id == marker.id } || existing.size < RouteLibraryPolicy.MAX_MARKERS) { "最多保存 ${RouteLibraryPolicy.MAX_MARKERS} 个地图标记" }
        writeAtomic(File(markerDirectory, markerFileName(marker.id)), RouteLibraryCodec.encodeMarker(marker))
    }

    @Synchronized fun deleteMarker(id: String) {
        Files.deleteIfExists(File(markerDirectory, markerFileName(id)).toPath())
    }

    private fun markerFileName(id: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".bin"

    private fun files(folder: File): List<File> = folder.listFiles()?.asSequence()
        ?.filter { it.isFile && it.name.endsWith(".bin") }?.sortedBy { it.name }?.toList().orEmpty()

    private fun readBounded(file: File): ByteArray {
        require(file.length() in 24..RouteLibraryCodec.MAX_RECORD_BYTES.toLong())
        return file.inputStream().use { input ->
            val result = ByteArray(file.length().toInt()); DataInputStream(input).readFully(result)
            require(input.read() == -1); result
        }
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建收藏目录" }
        val temporary = File.createTempFile("record-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { out -> out.write(bytes); out.fd.sync() }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            // Only our exact temporary file is removed; never a directory or library record.
            temporary.delete()
        }
    }
}
