package com.selfieburst.app.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.selfieburst.R
import com.selfieburst.app.MainViewModel
import com.selfieburst.app.camera.CameraFragment
import com.selfieburst.core.UserManager
import com.selfieburst.core.db.model.RecentPic
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.frag_gallery.*


/**
 * A simple [Fragment] subclass.
 * Use the [GalleryFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class GalleryFragment : Fragment() {


    private val viewModel by activityViewModels<MainViewModel>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.authState().observe(viewLifecycleOwner, Observer {
            if (it == UserManager.AuthState.NOT_AUTHORIZED) {
                findNavController().navigate(R.id.action_fragGallery_to_fragLogin)
            }
        })

        camId = ensureFrontCamera()

        return inflater.inflate(R.layout.frag_gallery, container, false)
    }


    lateinit var camId: String


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        btn_take_photo.setOnClickListener {
            if (camId == NO_FRONT_CAMERA) {
                // LETS GO HARDEST POSSIBLE APOCALYPTIC WAY
                Snackbar.make(
                    btn_take_photo, getString(R.string.msg_no_front_cam),
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                if (/*hasCameraPermission()*/hasPermissions(requireContext())) {
                    navigateToCamera()
                } else {
                    //makeRequest()
                    requestPermissions(PERMISSIONS_REQUIRED, CAMERA_REQUEST_CODE)
                }
            }
        }

        btn_logout.setOnClickListener {
            viewModel.logout()
        }

        val llm = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        val adapter = PicAdapter()
        recycler_gallery.layoutManager = llm
        recycler_gallery.adapter = adapter

        viewModel.recentPics().observe(viewLifecycleOwner, Observer {
            adapter.pics = it
        })
    }


    /**
     *
     */
    private class PicAdapter : RecyclerView.Adapter<ViewHolder>() {

        var pics: List<RecentPic>? = null
            set(value) {
                notifyDataSetChanged()
                field = value
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            val pic = LayoutInflater.from(parent.context).inflate(R.layout.item_pic, null)

            val lp = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            pic.layoutParams = lp

            return ViewHolder(pic)
        }

        override fun getItemCount(): Int = pics?.size ?: 0

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = Uri.parse(pics?.get(position)?.path)
            Picasso.get().load(uri).into(holder.item)
        }
    }


    /**
     *
     */
    private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val item: ImageView = itemView.findViewById(R.id.pic)
    }


    /** Convenience method used to check if all permissions required by this app are granted */
    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
                    navigateToCamera()
            }
        }
    }


    private fun navigateToCamera() {
        viewModel.initTimer()
        val args: Bundle = Bundle()
        args.putString(CameraFragment.ARG_CAM_ID, camId)
        findNavController().navigate(R.id.action_fragGallery_to_cameraFragment, args)
    }


    private fun ensureFrontCamera(): String {
        val cameraManager =
            requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraIds = cameraManager.cameraIdList

        cameraIds.filter {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )
            capabilities?.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
            ) ?: false
        }

        cameraIds.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val orientation = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (CameraCharacteristics.LENS_FACING_FRONT == orientation) return id
        }

        return NO_FRONT_CAMERA
    }


    companion object {

        private const val CAMERA_REQUEST_CODE = 13

        private val PERMISSIONS_REQUIRED = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        private const val NO_FRONT_CAMERA = "no_front_cam"

        @JvmStatic
        fun newInstance() = GalleryFragment()
    }

}
