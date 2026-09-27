package com.mortargoblin.shipka

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Hosts the game loop on a dedicated thread. The surface is rendered at a reduced
 * fixed resolution (about 720px on the short side) and scaled up by the compositor,
 * which keeps software canvas drawing fast on high-resolution phones.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private val game = Game(context)
    private val lock = Any()
    @Volatile private var thread: Thread? = null

    @Volatile private var resumed = false
    @Volatile private var surfaceReady = false

    /** Multiply view (touch) coordinates by this to get surface coordinates. */
    @Volatile private var touchScale = 1f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val shortSide = minOf(w, h)
        val scale = if (shortSide > TARGET_SHORT_SIDE) TARGET_SHORT_SIDE.toFloat() / shortSide else 1f
        touchScale = scale
        holder.setFixedSize((w * scale).toInt(), (h * scale).toInt())
    }

    fun resume() {
        resumed = true
        startLoop()
    }

    fun pause() {
        resumed = false
        synchronized(lock) { game.onPause() }
        stopLoop()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(lock) { game.resize(width, height) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopLoop()
    }

    private fun startLoop() {
        if (!resumed || !surfaceReady || thread != null) return
        val t = Thread(this, "game-loop")
        thread = t // assign before start(): run() checks that it is still the current loop thread
        t.start()
    }

    private fun stopLoop() {
        val t = thread ?: return
        thread = null
        try {
            t.join()
        } catch (_: InterruptedException) {
        }
    }

    override fun run() {
        var last = System.nanoTime()
        while (resumed && surfaceReady && thread === Thread.currentThread()) {
            val frameStart = System.nanoTime()
            val dt = ((frameStart - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = frameStart

            synchronized(lock) { game.update(dt) }

            val canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                synchronized(lock) { game.draw(canvas) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }

            val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000
            if (elapsedMs < FRAME_MS) {
                try {
                    Thread.sleep(FRAME_MS - elapsedMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(lock) { game.onTouch(event, touchScale) }
        return true
    }

    companion object {
        private const val TARGET_SHORT_SIDE = 720
        private const val FRAME_MS = 16L
    }
}
