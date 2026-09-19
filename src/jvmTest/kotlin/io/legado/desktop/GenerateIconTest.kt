package io.legado.desktop

import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.imageio.ImageIO

class GenerateIconTest {

    @Test
    fun generateIcons() {
        val size = 256
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Gradient background
        val gradient = GradientPaint(0f, 0f, Color(0xFF6750A4.toInt()), size.toFloat(), size.toFloat(), Color(0xFF381E72.toInt()))
        g.paint = gradient
        g.fill(RoundRectangle2D.Float(16f, 16f, (size - 32).toFloat(), (size - 32).toFloat(), 56f, 56f))

        // Book pages icon in center
        g.color = Color.WHITE
        // Left page
        g.fillRoundRect(50, 65, 72, 105, 12, 12)
        // Right page
        g.fillRoundRect(134, 65, 72, 105, 12, 12)
        // Spine
        g.color = Color(0xFFFFD8E4.toInt())
        g.fillRect(122, 65, 12, 105)

        // Text lines on left page
        g.color = Color(0xFF6750A4.toInt())
        g.fillRoundRect(62, 85, 48, 8, 4, 4)
        g.fillRoundRect(62, 105, 48, 8, 4, 4)
        g.fillRoundRect(62, 125, 36, 8, 4, 4)

        // Text lines on right page
        g.fillRoundRect(146, 85, 48, 8, 4, 4)
        g.fillRoundRect(146, 105, 48, 8, 4, 4)
        g.fillRoundRect(146, 125, 36, 8, 4, 4)

        // Title at bottom
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 26)
        val fm = g.fontMetrics
        val text = "阅 读"
        val textWidth = fm.stringWidth(text)
        g.drawString(text, (size - textWidth) / 2, 212)

        g.dispose()

        val resDir = File("src/jvmMain/resources")
        if (!resDir.exists()) resDir.mkdirs()

        // 1. Save PNG
        val pngFile = File(resDir, "icon.png")
        ImageIO.write(img, "PNG", pngFile)

        // 2. Save ICO (Standard Windows ICO format with embedded PNG)
        val pngBytes = ByteArrayOutputStream().apply { ImageIO.write(img, "PNG", this) }.toByteArray()
        val icoFile = File(resDir, "icon.ico")
        icoFile.outputStream().use { fos ->
            val dos = DataOutputStream(fos)
            // ICONDIR header
            dos.writeShort(swap(0))      // Reserved
            dos.writeShort(swap(1))      // Type 1 = ICO
            dos.writeShort(swap(1))      // Number of images
            // ICONDIRENTRY
            dos.writeByte(0)            // 256 width is 0
            dos.writeByte(0)            // 256 height is 0
            dos.writeByte(0)            // Colors
            dos.writeByte(0)            // Reserved
            dos.writeShort(swap(1))      // Color planes
            dos.writeShort(swap(32))     // Bits per pixel
            dos.writeInt(swapInt(pngBytes.size)) // Size of image data
            dos.writeInt(swapInt(22))    // Offset of image data (6 + 16 = 22)
            // Image data
            dos.write(pngBytes)
            dos.flush()
        }

        assert(pngFile.exists() && pngFile.length() > 0)
        assert(icoFile.exists() && icoFile.length() > 0)
    }

    private fun swap(v: Int): Int = ((v and 0xFF) shl 8) or ((v shr 8) and 0xFF)
    private fun swapInt(v: Int): Int =
        ((v and 0xFF) shl 24) or (((v shr 8) and 0xFF) shl 16) or (((v shr 16) and 0xFF) shl 8) or ((v shr 24) and 0xFF)
}
