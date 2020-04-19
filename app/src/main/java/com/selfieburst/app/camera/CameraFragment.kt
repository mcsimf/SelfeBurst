/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.selfieburst.app.camera

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.webkit.MimeTypeMap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.selfieburst.R
import com.selfieburst.app.MainViewModel
import com.selfieburst.core.db.model.RecentPic
import kotlinx.android.synthetic.main.camera_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.roundToInt

class CameraFragment : Fragment() {

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(camId!!) // Only front camera
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** Where the camera preview is displayed */
    private lateinit var viewFinder: SurfaceView

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    private val viewModel by activityViewModels<MainViewModel>()

    private var camId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camId = arguments?.getString(ARG_CAM_ID)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.camera_fragment, null)


    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = surface_view
        surface_view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                val previewSize =
                    getPreviewOutputSize(
                        viewFinder.display, characteristics, SurfaceHolder::class.java
                    )
                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                surface_view.holder.setFixedSize(previewSize.width, previewSize.height)
                surface_view.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                surface_view.post { initializeCamera() }
            }
        })

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(
            requireContext(),
            characteristics
        ).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            })
        }
    }


    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        // Open the selected camera
        camera = openCamera(cameraManager, camId!!/*args.cameraId*/, cameraHandler)

        // Initialize an image reader which will be used to capture still photos
        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!
            .getOutputSizes(ImageFormat.JPEG).maxBy { it.height * it.width }!!
        imageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.JPEG,
            IMAGE_BUFFER_SIZE
        )

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(viewFinder.holder.surface, imageReader.surface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        ).apply { addTarget(viewFinder.holder.surface) }

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

        viewModel.timer.observe(viewLifecycleOwner, Observer {
            Log.e(TAG, "timer over live data " + it.time + " " + System.identityHashCode(it))
            readyToClose = it.isFinished
            text.text = "${getString(R.string.lbl_cam)}\n${6 - it.time}"
            if (!it.used) {
                it.used = true
                when (it.time) {
                    1, 3, 5 -> initCapture()
                }
            }
        })

    }


    private var readyToClose = false


    private fun initCapture() {

        // Display flash animation to indicate that photo was captured
        flash.postDelayed({
            flash.visibility = View.VISIBLE
            flash.postDelayed(
                { flash.visibility = View.INVISIBLE }, 50
            )
        }, 100)

        // Perform I/O heavy operations in a different scope
        lifecycleScope.launch(Dispatchers.IO) {
            takePhoto().use { result ->
                Log.d(TAG, "Result received: $result")
                // Save the result to disk
                val output = saveResult(result)
                Log.d(TAG, "Image saved: $output")
                viewModel.putRecent(
                    RecentPic(
                        output.toString(),
                        System.currentTimeMillis()
                    )
                )
                notifyGalleries(output)
                if (readyToClose) findNavController().popBackStack()
            }
        }
    }


    private fun notifyGalleries(savedUri: Uri) {

        requireContext().sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, savedUri)
        )

        // Implicit broadcasts will be ignored for devices running API level >= 24
        // so if you only target API level 24+ you can remove this statement
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            requireActivity().sendBroadcast(
                Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
            )
        }

        // If the folder selected is an external media directory, this is
        // unnecessary but otherwise other apps will not be able to access our
        // images unless we scan them using [MediaScannerConnection]
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            requireContext().contentResolver.getType(savedUri)
        )
        MediaScannerConnection.scanFile(
            context,
            arrayOf(savedUri.toString()),
            arrayOf(mimeType)
        ) { _, uri ->
            Log.d(TAG, "Image capture scanned into media store: $uri")
        }

    }


    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE
        ).apply { addTarget(imageReader.surface) }
        session.capture(
            captureRequest.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                    Log.d(TAG, "Capture result received: $resultTimestamp")

                    // Set a timeout in case image captured is dropped from the pipeline
                    val exc = TimeoutException("Image dequeuing took too long")
                    val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                    imageReaderHandler.postDelayed(
                        timeoutRunnable,
                        IMAGE_CAPTURE_TIMEOUT_MILLIS
                    )

                    // Loop in the coroutine's context until an image with matching timestamp comes
                    // We need to launch the coroutine context again because the callback is done in
                    //  the handler provided to the `capture` method, not in our coroutine context
                    @Suppress("BlockingMethodInNonBlockingContext")
                    lifecycleScope.launch(cont.context) {
                        while (true) {

                            // Dequeue images while timestamps don't match
                            val image = imageQueue.take()
                            // TODO(owahltinez): b/142011420
                            // if (image.timestamp != resultTimestamp) continue
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp
                            ) continue
                            Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                            // Unset the image reader listener
                            imageReaderHandler.removeCallbacks(timeoutRunnable)
                            imageReader.setOnImageAvailableListener(null, null)

                            // Clear the queue of images, if there are left
                            while (imageQueue.size > 0) {
                                imageQueue.take().close()
                            }

                            // Compute EXIF orientation metadata
                            val rotation = relativeOrientation.value ?: 0
                            val mirrored =
                                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                        CameraCharacteristics.LENS_FACING_FRONT
                            val exifOrientation =
                                computeExifOrientation(
                                    rotation,
                                    mirrored
                                )

                            // Build the result and resume progress
                            cont.resume(
                                CombinedCaptureResult(
                                    image, result, exifOrientation, imageReader.imageFormat
                                )
                            )

                            // There is no need to break out of the loop, this coroutine will suspend
                        }
                    }
                }
            },
            cameraHandler
        )
    }


    /** Helper function used to save a [CombinedCaptureResult] into a [File] */
    private suspend fun saveResult(result: CombinedCaptureResult): Uri =
        suspendCoroutine { cont ->
            when (result.format) {
                /* For test task we will use only jpeg formats */
                // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
                ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    val size =
                        newSize(
                            result.image.width,
                            result.image.height
                        )
                    val scaled =
                        scale(
                            bytes,
                            size.width,
                            size.height
                        )
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val resolver: ContentResolver = requireContext().contentResolver
                            val fileName =
                                fileName(
                                    "jpg"
                                )
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                                put(MediaStore.MediaColumns.ORIENTATION, result.orientation)
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_PICTURES
                                )
                            }
                            val uri = resolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )
                            Log.d(TAG, "Image uri: $uri")
                            if (null != uri) {
                                resolver.openOutputStream(uri).use {
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 100, it)
                                }
                                cont.resume(uri)
                            } else {
                                val exc = RuntimeException("Can't save image with MediaStore")
                                cont.resumeWithException(exc)
                            }

                        } else {
                            val output =
                                createFile(
                                    requireContext(),
                                    "jpg"
                                )

                            FileOutputStream(output).use {
                                scaled.compress(Bitmap.CompressFormat.JPEG, 100, it)
                            }

                            // If the result is a JPEG file, update EXIF metadata with orientation info
                            val exif = ExifInterface(output.absolutePath)
                            exif.setAttribute(
                                ExifInterface.TAG_ORIENTATION, result.orientation.toString()
                            )
                            exif.saveAttributes()
                            Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
                            cont.resume(Uri.fromFile(output))
                        }

                    } catch (exc: IOException) {
                        Log.e(TAG, "Unable to write JPEG image to file " + exc.message)
                        cont.resumeWithException(exc)
                    }
                }

                // No other formats are supported by this sample
                else -> {
                    val exc = RuntimeException("Unknown image format: ${result.image.format}")
                    Log.e(TAG, exc.message, exc)
                    cont.resumeWithException(exc)
                }
            }
        }


    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }


    companion object {

        const val ARG_CAM_ID = "cam_id"

        private val TAG = CameraFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        /**
         * Create a [File] named a using formatted timestamp with the current date and time.
         *
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM
                ), "Camera"
            )
            return File(dir, fileName(extension))
        }

        private fun fileName(ext: String): String {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return "IMG_${sdf.format(Date())}.$ext"
        }

        private fun newSize(w: Int, h: Int): Size {
            val scale = 1000F / max(w, h)
            return Size((w * scale).roundToInt(), (h * scale).roundToInt())
        }

        private fun scale(img: ByteArray, newWidth: Int, newHeight: Int): Bitmap {
            val bitmapImage: Bitmap = BitmapFactory.decodeByteArray(img, 0, img.size)
            return Bitmap.createScaledBitmap(bitmapImage, newWidth, newHeight, false)
        }
    }
}
