package com.aurorasoft.brazorobot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isNotEmpty
import com.aurorasoft.gympix.camera.CameraXFrameProvider
import com.ingenieriajhr.blujhr.BluJhr


class MainActivity : AppCompatActivity() {

    private lateinit var devicesBluetooth: ArrayList<String>
    private lateinit var viewCamera: ConstraintLayout
    private lateinit var viewBluetooth: ConstraintLayout

    private lateinit var overlayHand: HandOverlayView

    private lateinit var cameraPreview: ImageView

    private lateinit var cameraProvider: CameraXFrameProvider

    private lateinit var handDetector: HandDetector


    lateinit var blue: BluJhr

    lateinit var deviceListView: ListView

    private var isProcessingFrame = false

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) initCamera()
            else {
                Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
                finish()
            }
        }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        deviceListView  = findViewById<ListView>(R.id.deviceListView)
        viewBluetooth = findViewById<ConstraintLayout>(R.id.viewBluetooth)
        viewCamera = findViewById<ConstraintLayout>(R.id.viewCamera)
        cameraPreview = findViewById<ImageView>(R.id.cameraPreview)
        overlayHand = findViewById<HandOverlayView>(R.id.overlayHand)


        handDecector()
        bleDevicesView()
        blue = BluJhr(this)
        blue.onBluetooth()
        clickList()


    }

    private fun handDecector() {
        handDetector = HandDetector(this){result->
            runOnUiThread {
                if (result.landmarks().isNotEmpty()){
                    overlayHand.setResults(result)
                    isProcessingFrame = false
                }else{
                    isProcessingFrame = false
                    overlayHand.clear()
                }
            }
        }

    }

    /**
     * ViewCameraView
     */
    fun cameraView(){
        viewBluetooth.visibility = View.GONE
        viewCamera.visibility = View.VISIBLE
    }

    /**
     * ViewBleView
     */
    fun bleDevicesView(){
        viewBluetooth.visibility = View.VISIBLE
        viewCamera.visibility = View.GONE
    }

    private fun clickList() {



        deviceListView.setOnItemClickListener{adapterView,view,i,l ->

            if (deviceListView.isNotEmpty()){

               // Toast.makeText(this, "${devicesBluetooth[i]}", Toast.LENGTH_SHORT).show()
                blue.connect(devicesBluetooth[i])
                blue.setDataLoadFinishedListener(object : BluJhr.ConnectedBluetooth{
                    override fun onConnectState(state: BluJhr.Connected) {
                        when(state){

                            BluJhr.Connected.True->{
                                Toast.makeText(applicationContext,"Conectado",Toast.LENGTH_SHORT).show()
                                cameraView()
                                requestCameraPermission()
                                rxReceived()
                            }

                            BluJhr.Connected.Pending->{
                                Toast.makeText(applicationContext,"Conectando",Toast.LENGTH_SHORT).show()

                            }

                            BluJhr.Connected.False->{
                                Toast.makeText(applicationContext,"Error en conexión",Toast.LENGTH_SHORT).show()
                                bleDevicesView()
                            }

                            BluJhr.Connected.Disconnect->{
                                Toast.makeText(applicationContext,"Desconectado",Toast.LENGTH_SHORT).show()
                                bleDevicesView()
                            }

                        }
                    }


                })

            }
        }
    }


    private fun rxReceived() {

    }



    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            //permiso concedido
            initCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }


    private fun initCamera() {


        cameraProvider = CameraXFrameProvider(this)
        cameraProvider.initCameraExecutor()
        cameraProvider.setFrameListener(object : CameraXFrameProvider.FrameListener {
            override fun onFrameAvailable(bitmap: Bitmap, rotationDegrees: Int) {

                val systemTime = System.currentTimeMillis()

                if (!isProcessingFrame){
                    handDetector.detectLiveStream(bitmap,systemTime)
                    isProcessingFrame = true
                }
                 cameraPreview.post {
                     cameraPreview.setImageBitmap(bitmap)
                 }
            }
        })
        cameraProvider.setupCamera(this)
    }






    /**
     * pedimos los permisos correspondientes, para android 12 hay que pedir los siguientes BLUETOOTH_SCAN y BLUETOOTH_CONNECT
     * en android 12 o superior se requieren permisos adicionales
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (blue.checkPermissions(requestCode,grantResults)){
            Toast.makeText(this, "Exit", Toast.LENGTH_SHORT).show()
            blue.initializeBluetooth()
        }else{
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S){
                blue.initializeBluetooth()
            }else{
                Toast.makeText(this, "Algo salio mal", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!blue.stateBluetoooth() && requestCode == 100){
            blue.initializeBluetooth()
        }else{
            if (requestCode == 100){
                devicesBluetooth = blue.deviceBluetooth()
                if (devicesBluetooth.isNotEmpty()){
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_expandable_list_item_1,
                        devicesBluetooth
                    )
                    deviceListView.adapter = adapter
                }else{
                    Toast.makeText(this, "No tienes vinculados dispositivos", Toast.LENGTH_SHORT).show()
                }

            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }




}