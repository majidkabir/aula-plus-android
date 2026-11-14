package com.majidkabir.aulaplus.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.majidkabir.aulaplus.AulaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Finds faces (ML Kit) and turns each into a 128-number "faceprint" with MobileFaceNet
 * (sirius-ai/MobileFaceNet_TF, Apache-2.0). Everything runs on the phone.
 */
object FaceEngine {
    class Face(val embedding: FloatArray, val size: Float)

    private val lock = Mutex()
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.05f)
                .build(),
        )
    }
    private val interpreter by lazy {
        val fd = AulaApp.context.assets.openFd("mobilefacenet.tflite")
        val model = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        Interpreter(model, Interpreter.Options().setNumThreads(2))
    }

    /** Faces at least ~36 px wide, biggest first. One photo at a time (CPU and battery). */
    suspend fun faces(bitmap: Bitmap): List<Face> = lock.withLock {
        withContext(Dispatchers.Default) {
            val found = runCatching { Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0))) }.getOrDefault(emptyList())
            // Clear, roughly frontal faces at least ~56 px wide with both eyes found: small or
            // side-on faces give unreliable faceprints.
            found.filter {
                it.boundingBox.width() >= 56 && kotlin.math.abs(it.headEulerAngleY) <= 30f && kotlin.math.abs(it.headEulerAngleX) <= 25f &&
                    it.getLandmark(FaceLandmark.LEFT_EYE) != null && it.getLandmark(FaceLandmark.RIGHT_EYE) != null
            }
                .sortedByDescending { it.boundingBox.width() }
                .take(12)
                .mapNotNull { f ->
                    val crop = aligned(bitmap, f) ?: return@mapNotNull null
                    embed(crop)?.let { Face(it, f.boundingBox.width().toFloat() / bitmap.width) }
                }
        }
    }

    private fun embed(face: Bitmap): FloatArray? = runCatching {
        val input = ByteBuffer.allocateDirect(112 * 112 * 3 * 4).order(ByteOrder.nativeOrder())
        val px = IntArray(112 * 112)
        face.getPixels(px, 0, 112, 0, 0, 112, 112)
        for (p in px) {
            input.putFloat(((p shr 16 and 0xFF) - 127.5f) * 0.0078125f)
            input.putFloat(((p shr 8 and 0xFF) - 127.5f) * 0.0078125f)
            input.putFloat(((p and 0xFF) - 127.5f) * 0.0078125f)
        }
        val out = Array(1) { FloatArray(128) }
        interpreter.run(input, out)
        val v = out[0]
        val n = sqrt(v.fold(0f) { a, x -> a + x * x })
        if (n == 0f) null else FloatArray(v.size) { v[it] / n }
    }.getOrNull()

    /**
     * 112×112 crop with the eyes level and where MobileFaceNet expects them
     * (ArcFace layout: eye centres at (38.3, 51.7) and (73.5, 51.5)). Falls back to the box.
     */
    private fun aligned(src: Bitmap, f: com.google.mlkit.vision.face.Face): Bitmap? {
        val box = f.boundingBox
        var midX = box.exactCenterX(); var midY = box.exactCenterY() - box.height() * 0.12f
        var angle = 0.0
        var scale = 112f / (box.width() * 1.05f)
        val l = f.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val r = f.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (l != null && r != null) {
            val (a, b) = if (l.x < r.x) l to r else r to l
            val d = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
            if (d > 4) {
                angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))
                scale = 35.2f / d
                midX = (a.x + b.x) / 2; midY = (a.y + b.y) / 2
            }
        }
        val out = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(AColor.GRAY)
        val m = Matrix()
        m.postTranslate(-midX, -midY)
        m.postScale(scale, scale)
        m.postRotate(-angle.toFloat())
        m.postTranslate(55.9f, 51.6f) // y-down: eyes at y = 51.6
        canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

/**
 * Which photos show my children, worked out on the phone. Faceprints of other people are never
 * kept: only each photo's similarity to my own children is stored.
 */
object FaceIndex {
    /**
     * Similarity (average of the best 3 matches against the child's reference faces) that counts as a match.
     * Young children look alike to the model, so this is strict.
     */
    const val THRESHOLD = 0.62f
    /** A face must be this much closer to one of my children than to the others. */
    const val MARGIN = 0.06f
    /** Bump to throw away results from an older, less careful version. */
    private const val SCHEMA = 2

    @Serializable
    private data class Store(
        val refs: Map<Int, List<FloatArray>> = emptyMap(),
        val refsFrom: Map<Int, List<Int>> = emptyMap(),
        val scores: Map<Int, Map<Int, Float>> = emptyMap(),
        val version: Int = 0,
        val scoredWith: Map<Int, Int> = emptyMap(),
        val rejected: Map<Int, Set<Int>> = emptyMap(),
        val found: Map<Int, List<Media>> = emptyMap(),
        val schema: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
    private val file get() = AulaApp.context.filesDir.resolve("faces.json")
    private var store by mutableStateOf(load())

    var isEnabled by mutableStateOf(if (AulaApp.isReady) AulaApp.prefs.getBoolean("faceFinder", true) else false)
        private set
    /** Photos being checked right now: (done, total). */
    var scanning by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var learning by mutableStateOf(false)
        private set

    fun switchOn(on: Boolean) {
        isEnabled = on
        AulaApp.prefs.edit().putBoolean("faceFinder", on).apply()
    }

    private fun load(): Store = if (!AulaApp.isReady || Demo.isOn) Store(schema = SCHEMA) else
        runCatching { json.decodeFromString<Store>(file.readText()) }.getOrNull()?.takeIf { it.schema == SCHEMA } ?: Store(schema = SCHEMA)

    private fun save() {
        if (!AulaApp.isReady || Demo.isOn) return
        val s = store
        runCatching { file.writeText(json.encodeToString(s)) }
    }

    fun referenceCount(childId: Int) = store.refs[childId]?.size ?: 0
    fun hasReferences(childId: Int) = referenceCount(childId) > 0

    /** Found by face (not tagged by staff), and not rejected by the user. */
    fun isFaceMatch(m: Media, childId: Int): Boolean {
        if (!isEnabled || m.isTagged(childId) || store.rejected[childId]?.contains(m.id) == true) return false
        return (store.scores[m.id]?.get(childId) ?: 0f) >= THRESHOLD
    }

    /** Tagged, or found by face. */
    fun shows(m: Media, childId: Int) = m.isTagged(childId) || isFaceMatch(m, childId)

    fun found(childId: Int): List<Media> =
        if (!isEnabled) emptyList() else store.found[childId].orEmpty().filter { isFaceMatch(it, childId) }

    /** Reference faces from photos the child is tagged in (one face only, so it's surely them). */
    suspend fun learn(childId: Int, tagged: List<Media>) {
        if (!isEnabled || learning || referenceCount(childId) >= 25) return
        val have = store.refsFrom[childId].orEmpty().toSet()
        val todo = tagged.filter { !it.isVideo && it.id !in have }.take(40)
        if (todo.isEmpty()) return
        learning = true
        try {
            var added = 0
            for (m in todo) {
                val bmp = image(m) ?: continue
                val faces = FaceEngine.faces(bmp)
                val refs = store.refs[childId].orEmpty()
                val one = faces.singleOrNull()?.takeIf { it.size > 0.06f }
                store = store.copy(
                    refsFrom = store.refsFrom + (childId to store.refsFrom[childId].orEmpty() + m.id),
                    refs = if (one != null) store.refs + (childId to refs + one.embedding) else store.refs,
                )
                if (one != null) added++
                if (referenceCount(childId) >= 25) break
            }
            if (added > 0) store = store.copy(version = store.version + 1)
            save()
        } finally {
            learning = false
        }
    }

    /** "Det er Emma": the biggest face in this photo becomes one of the child's reference faces. */
    suspend fun confirm(m: Media, childId: Int): Boolean {
        val face = image(m)?.let { FaceEngine.faces(it).firstOrNull() } ?: return false
        store = store.copy(
            refs = store.refs + (childId to store.refs[childId].orEmpty() + face.embedding),
            refsFrom = store.refsFrom + (childId to store.refsFrom[childId].orEmpty() + m.id),
            rejected = store.rejected + (childId to store.rejected[childId].orEmpty() - m.id),
            version = store.version + 1,
        )
        save()
        return true
    }

    /** "Ikke Emma". */
    fun reject(m: Media, childId: Int) {
        store = store.copy(rejected = store.rejected + (childId to store.rejected[childId].orEmpty() + m.id))
        save()
    }

    /** Checks photos not yet scored against the current reference faces, one at a time. */
    suspend fun scan(media: List<Media>, children: List<Child>) {
        if (!isEnabled || scanning != null) return
        val kids = children.filter { hasReferences(it.id) }
        if (kids.isEmpty()) return
        val todo = media.filter { !it.isVideo && store.scoredWith[it.id] != store.version }
        if (todo.isEmpty()) return
        // References that don't look like the rest (e.g. a teacher in a tagged photo) are left out.
        val refs = kids.associate { it.id to cleaned(store.refs[it.id].orEmpty()) }
        scanning = 0 to todo.size
        try {
            todo.forEachIndexed { i, m ->
                if (!isEnabled) return
                val bmp = image(m)
                if (bmp != null) {
                    val faces = FaceEngine.faces(bmp)
                    val row = mutableMapOf<Int, Float>()
                    for (face in faces) {
                        val sims = kids.map { it.id to similarity(face, refs[it.id].orEmpty()) }
                        for ((id, sim) in sims) {
                            // Closer to this child than to my other children by a clear margin.
                            val others = sims.filter { it.first != id }.maxOfOrNull { it.second } ?: 0f
                            val score = if (sims.size == 1 || sim - others >= MARGIN) sim else 0f
                            row[id] = maxOf(row[id] ?: 0f, score)
                        }
                    }
                    var found = store.found
                    for (k in kids) if ((row[k.id] ?: 0f) >= THRESHOLD && !m.isTagged(k.id)) {
                        found = found + (k.id to (listOf(m) + found[k.id].orEmpty().filter { it.id != m.id }).take(1000))
                    }
                    store = store.copy(scores = store.scores + (m.id to row), scoredWith = store.scoredWith + (m.id to store.version), found = found)
                }
                scanning = (i + 1) to todo.size
                if (i % 20 == 19) save()
            }
        } finally {
            scanning = null
            save()
        }
    }

    fun eraseAll() {
        store = Store(schema = SCHEMA)
        if (AulaApp.isReady) file.delete()
    }

    /** A face against the child: mean of its 3 highest cosine similarities to the reference faces. */
    private fun similarity(face: FaceEngine.Face, refs: List<FloatArray>): Float {
        if (refs.isEmpty()) return 0f
        val top = refs.map { dot(face.embedding, it) }.sortedDescending().take(3)
        return top.sum() / top.size
    }

    private fun dot(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    /**
     * Drops reference faces that don't resemble the others (most references are right, a few come
     * from a tagged photo where someone else's face was the one found).
     */
    fun cleaned(refs: List<FloatArray>): List<FloatArray> {
        if (refs.size < 3) return refs
        val typical = refs.indices.map { i -> refs.indices.filter { it != i }.map { dot(refs[i], refs[it]) }.sorted().let { it[it.size / 2] } }
        val median = typical.sorted()[typical.size / 2]
        val keep = refs.indices.filter { typical[it] >= maxOf(0.35f, median - 0.12f) }
        return if (keep.size >= 2) keep.map { refs[it] } else refs
    }

    private suspend fun image(m: Media): Bitmap? = withContext(Dispatchers.IO) {
        val ctx = AulaApp.context
        val req = coil.request.ImageRequest.Builder(ctx)
            .data(m.scanUrl ?: m.fullUrl ?: m.thumbUrl)
            .memoryCacheKey("m${m.id}").diskCacheKey("m${m.id}")
            .size(1280).allowHardware(false)
            .build()
        val result = coil.Coil.imageLoader(ctx).execute(req)
        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }
}

/** Runs face finding: learns each child's face from their tagged photos, then checks photos. */
object FaceFinding {
    /** While searching every album: (album number, album count). */
    var allAlbums by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    suspend fun learnAndScan(photos: List<Media>, children: List<Child>) {
        if (!FaceIndex.isEnabled || children.isEmpty() || Demo.isOn) return
        learn(children)
        FaceIndex.scan(photos, children)
    }

    private suspend fun learn(children: List<Child>) {
        for (c in children) if (FaceIndex.referenceCount(c.id) < 8) {
            val tagged = Repo.taggedMedia(c.id)
            tagged.load(86_400)
            FaceIndex.learn(c.id, tagged.value.orEmpty())
        }
    }

    suspend fun searchAllAlbums(children: List<Child>) {
        if (allAlbums != null || Demo.isOn) return
        allAlbums = 0 to 0
        try {
            learn(children)
            Repo.albums.load(3600)
            val albums = Repo.albums.value.orEmpty()
            albums.forEachIndexed { i, a ->
                allAlbums = i to albums.size
                val media = Repo.media(a.id)
                media.load(3600)
                FaceIndex.scan(media.value.orEmpty(), children)
            }
        } finally {
            allAlbums = null
        }
    }
}

/** App-wide scope for work that should outlive a screen (e.g. checking photos for faces). */
val AppScope = kotlinx.coroutines.MainScope()
