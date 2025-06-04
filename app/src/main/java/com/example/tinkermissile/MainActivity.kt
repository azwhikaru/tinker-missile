package com.example.tinkermissile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.flir.flironesdk.Device
import com.flir.flironesdk.Device.TuningState
import com.flir.flironesdk.Frame
import com.flir.flironesdk.FrameProcessor
import com.flir.flironesdk.RenderedImage
import com.flir.flironesdk.RenderedImage.ImageType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.EnumSet
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity(), FrameProcessor.Delegate, Device.Delegate, Device.StreamDelegate {

    /*
    ThermalLinearFlux14BitImage:
        Linear 14 bit image data, padded to 16 bits per pixel.
        This is the raw image from the thermal image sensor.
    ThermalRGBA8888Image:
        Thermal RGBA image data, with a palette applied.
    BlendedMSXRGBA8888Image:
        MSX (thermal + visual) RGBA image data, with a palette applied.
        This shows an outline of objects using the visible light camera, overlaid on the thermal image.
    VisibleAlignedRGBA8888Image:
        Visual RGBA image data, aligned with the thermal.
    ThermalRadiometricKelvinImage:
        Radiometric centi-kelvin (cK) temperature data.
        Note that is is in centi-kelvin, so a reading of 31015 is equal to 310.15K (98.6ºF or 37ºC).
    VisibleUnalignedYUV888Image:
        Visual YUV888 image data, unaligned with the thermal.
    */
    private val defaultImageType = ImageType.ThermalRGBA8888Image

    private lateinit var frameProcessor: FrameProcessor
    private var flir: Device? = null
    private var thermalImageView: ImageView? = null
    private var tuningState = TuningState.Unknown
    private var thermalBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        thermalImageView = findViewById(R.id.imageView)

        frameProcessor = FrameProcessor(this, this, EnumSet.of(defaultImageType))
    }

    override fun onStart() {
        super.onStart()
        /*
        Searching for available devices when launching the application.

        There are 3 types of devices
        1. SimulatedDevice
        2. EmbeddedDevice  <- CAT S60
        3. FlirUsbDevice

        Check com.flir.flironesdk.Device.getSupportedDeviceClasses for details.
        */
        Device.startDiscovery(this, this)
    }

    override fun onStop() {
        super.onStop()
        Device.stopDiscovery()
        flir = null
    }

    override fun onDeviceConnected(dev: Device?) {
        flir = dev
        flir?.startFrameStream(this)
    }

    override fun onDeviceDisconnected(dev: Device?) {
        flir?.stopFrameStream()
        flir = null
    }

    override fun onTuningStateChanged(value: TuningState?) {
        value ?: return
        tuningState = value
    }

    override fun onAutomaticTuningChanged(p0: Boolean) {}

    override fun onFrameReceived(frame: Frame?) {
        if (tuningState != TuningState.InProgress) {
            /*
            It's a callback function.

            Frame the raw data retrieved from the sensor.
            We use the FrameProcessor and a pre-defined target (defaultImageType) to handle it.
            */
            frameProcessor.processFrame(frame)
        }
    }

    override fun onFrameProcessed(renderedImage: RenderedImage?) {
        if (renderedImage == null) return

        /*
        It's a callback function.

        When the data from the sensor is converted to a target,
        where it will be for next setp processing.

        e.g. to present the image to the user, or to do other things.
        */

        if (renderedImage.imageType() == ImageType.VisualYCbCr888Image) {
            val visBytes = renderedImage.pixelData()
            val visBitmap = BitmapFactory.decodeByteArray(visBytes, 0, visBytes.size)
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(visBitmap, 0, 0, visBitmap.width, visBitmap.height, matrix, true)
            updateImageView(rotated)
        } else if (renderedImage.imageType() == ImageType.ThermalLinearFlux14BitImage) {
            val width = renderedImage.width()
            val height = renderedImage.height()
            if (thermalBitmap == null || thermalBitmap?.width != width || thermalBitmap?.height != height) {
                thermalBitmap = createBitmap(width, height)
            }
            val pixelData = renderedImage.pixelData()
            val shortPixels = ShortArray(pixelData.size / 2)
            ByteBuffer.wrap(pixelData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortPixels)
            val argb = ByteArray(width * height * 4)
            var offset = 0
            var minVal = Int.MAX_VALUE
            var maxVal = Int.MIN_VALUE
            for (v in shortPixels) {
                val value = v.toInt() and 0xffff
                if (value < minVal) minVal = value
                if (value > maxVal) maxVal = value
            }
            val range = (maxVal - minVal).coerceAtLeast(1)
            for (i in shortPixels.indices) {
                val normalized = ((shortPixels[i].toInt() and 0xffff) - minVal) * 255 / range
                val g = normalized.coerceIn(0, 255).toByte()
                argb[offset++] = g
                argb[offset++] = g
                argb[offset++] = g
                argb[offset++] = 0xFF.toByte()
            }
            thermalBitmap?.copyPixelsFromBuffer(ByteBuffer.wrap(argb))
            thermalBitmap?.let { updateImageView(it) }
        } else {
            val width = renderedImage.width()
            val height = renderedImage.height()
            if (thermalBitmap == null || thermalBitmap?.width != width || thermalBitmap?.height != height) {
                thermalBitmap = createBitmap(width, height)
            }
            thermalBitmap?.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()))
            thermalBitmap?.let { updateImageView(it) }
        }
    }

    private fun updateImageView(bitmap: Bitmap) {
        runOnUiThread {
            thermalImageView?.setImageBitmap(bitmap)
        }
    }
}
