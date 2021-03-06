package ru.smak.gui.graphics

import ru.smak.gui.graphics.convertation.CartesianScreenPlane
import ru.smak.gui.graphics.convertation.Converter
import ru.smak.math.Complex
import java.awt.Color
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

class FractalPainter(
    val plane: CartesianScreenPlane) : Painter {

    var isInSet : ((Complex)->Float)? = null
    var getColor: ((Float)->Color) = { x -> Color(x, x, x)}
    private var recreate = true
    private var stop = false
    private var bi = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    var savedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    private var partsDone = 0

    private val stripList: MutableList<FractalStripPainter> = mutableListOf()
    private val imageReadyListeners: MutableList<()->Unit> = mutableListOf()
    fun addImageReadyListener(l: ()->Unit){
        imageReadyListeners.add(l)
    }
    fun removeImageReadyListener(l: ()->Unit){
        imageReadyListeners.remove(l)
    }

    private var ms1 = 0L
    private var ms2 = 0L

    companion object {
        /**
         * Количество параллельных подпроцессов для построения фрактала
         * соответствует числу процессоров в системе
         */
        private val stripCount = Runtime.getRuntime().availableProcessors()
    }

    /**
     * Класс для отрисовки полоски с фракталом для
     * ускорения построения в параллельных подпроцессах
     */
    inner class FractalStripPainter(stripId: Int){

        var thread: Thread? = null
        private set

        private val stripImg: BufferedImage
        private val stripWidth: Int = plane.width / stripCount
        private val b: Int
        private val e: Int
        private val add: Int

        init {
            b = stripId * stripWidth
            add = if (stripId == (stripCount - 1)) {plane.width % stripCount} else 0
            e = b + stripWidth + add
            stripImg = BufferedImage(stripWidth + add, plane.height, BufferedImage.TYPE_INT_RGB)
        }

        fun paint(g: Graphics){
            thread = thread {
                isInSet?.let{
                    for (i in b..e) {
                        for (j in 0..plane.height) {
                            if (stop) {
                                return@thread
                            }
                            val r = it.invoke(Complex(
                                            Converter.xScr2Crt(i, plane),
                                            Converter.yScr2Crt(j, plane)
                                    ))
                            val c = if (r eq 1F) Color.BLACK else getColor(r)
                            with (stripImg.graphics) {
                                color = c
                                fillRect(i - b, j, 1, 1)
                            }
                        }
                    }
                    synchronized(stripList) {
                        if (!stop) {
                            g.drawImage(stripImg, b, 0, null)
                            if (++partsDone == stripCount) finished()
                        }
                    }
                }
            }
        }
    }

    private fun finished() {
        if (!stop) {
            savedImage = BufferedImage(plane.width, plane.height, BufferedImage.TYPE_INT_RGB)
            synchronized(stripList) {
                savedImage.graphics.drawImage(bi, 0, 0, null)
            }
            recreate = false
        }
        ms2 = System.currentTimeMillis()
        println((ms2 - ms1) / 1000.0)
        imageReadyListeners.forEach { it.invoke() }
    }

    /**
     * Рисование фрактала
     * @param g графический контекст для рисования
     */
    override fun paint(g: Graphics?) {
        ms1 = System.currentTimeMillis()
        if (isInSet==null || g==null) return
        g.drawImage(
                savedImage,
                0,
                0,
                plane.width,
                plane.height,
                null)
        if (!recreate){
            recreate = true
            return
        }
        stop = true
        stripList.forEach { it.thread?.join() }
        stripList.clear()
        partsDone = 0
        bi = BufferedImage(
                plane.width,
                plane.height,
                BufferedImage.TYPE_INT_RGB
        )
        stop = false
        for (i in 0 until stripCount) {
            stripList.add(FractalStripPainter(i).also {
                it.paint(bi.graphics)
            })
        }
    }

}

private infix fun Float.eq(other: Float) =
        abs(this - other) < max(Math.ulp(this), Math.ulp(other)) * 2
private infix fun Float.neq(other: Float) =
        abs(this - other) > max(Math.ulp(this), Math.ulp(other)) * 2