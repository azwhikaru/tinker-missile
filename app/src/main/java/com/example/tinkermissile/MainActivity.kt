package com.example.tinkermissile

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.flir.flironesdk.Device
import com.flir.flironesdk.Device.TuningState
import com.flir.flironesdk.Frame
import com.flir.flironesdk.FrameProcessor
import com.flir.flironesdk.RenderedImage
import com.flir.flironesdk.RenderedImage.ImageType

class MainActivity : AppCompatActivity(), FrameProcessor.Delegate, Device.Delegate,
    Device.StreamDelegate {

    private val imageSet: Set<ImageType> = setOf(
        ImageType.VisibleAlignedRGBA8888Image,
        ImageType.BlendedMSXRGBA8888Image,
        ImageType.ThermalLinearFlux14BitImage,
        ImageType.ThermalRadiometricKelvinImage,
    )

    private val defaultImageType = ImageType.BlendedMSXRGBA8888Image

    private lateinit var frameProcessor: FrameProcessor
    private lateinit var thermalView: GLSurfaceView
    private lateinit var progressBar: ProgressBar

    private var tuningState = TuningState.Unknown

    private var flir: Device? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        thermalView = findViewById(R.id.thermalView)
        progressBar = findViewById(R.id.progressBar)

        frameProcessor = FrameProcessor(this,  this, imageSet, true)
        frameProcessor.glOutputMode = defaultImageType
        frameProcessor.imageRotation = 270f

        thermalView.preserveEGLContextOnPause = true
        thermalView.setEGLContextClientVersion(2)
        thermalView.setRenderer(frameProcessor)
        thermalView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        thermalView.debugFlags = GLSurfaceView.DEBUG_CHECK_GL_ERROR or GLSurfaceView.DEBUG_LOG_GL_CALLS
    }

    override fun onStart() {
        super.onStart()

        try {
            Device.startDiscovery(this, this)
        } catch (_: IllegalStateException) {

        } catch (_: SecurityException) {
            Toast.makeText(this, "E1", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onStop() {
        super.onStop()

        Device.stopDiscovery()
        flir = null
    }

    override fun onPause() {
        super.onPause()

        thermalView.onPause()
        flir?.stopFrameStream()
    }

    override fun onResume() {
        super.onResume()

        thermalView.onResume()
        flir?.startFrameStream(this)
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

        runOnUiThread {
            when(tuningState) {
                TuningState.InProgress -> progressBar.visibility = View.VISIBLE
                else -> progressBar.visibility = View.GONE
            }
        }
    }

    override fun onAutomaticTuningChanged(p0: Boolean) {}

    override fun onFrameProcessed(p0: RenderedImage?) {}

    override fun onFrameReceived(frame: Frame?) {
        if(tuningState != TuningState.InProgress) {
            frameProcessor.processFrame(frame, FrameProcessor.QueuingOption.SKIP_FRAME)
            thermalView.requestRender()
        }
    }
}