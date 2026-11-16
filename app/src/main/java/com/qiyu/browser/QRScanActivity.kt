package com.qiyu.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/** 菜单「扫码」：扫二维码，结果在浏览器新标签打开（非网址则转搜索） */
class QRScanActivity : ComponentActivity() {

    private lateinit var decoratedView: DecoratedBarcodeView
    private lateinit var captureManager: CaptureManager
    private var cameraGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        decoratedView = DecoratedBarcodeView(this).apply {
            setDecoderFactory(DefaultDecoderFactory(
                listOf(BarcodeFormat.QR_CODE),
                mapOf(DecodeHintType.POINT_TO_POINT to true),
                null,
                0
            ))
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    result.text?.let { openResult(it) }
                }
            })
        }
        captureManager = CaptureManager(this, decoratedView)
        setContentView(
            decoratedView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CaptureManager.getCameraPermissionReqCode()
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CaptureManager.getCameraPermissionReqCode()) {
            cameraGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) {
                captureManager.decode()
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        captureManager.onResume()
        if (cameraGranted) captureManager.decode()
    }

    override fun onPause() {
        captureManager.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        captureManager.onDestroy()
        super.onDestroy()
    }

    private fun openResult(text: String) {
        val url = if (text.startsWith("http://") || text.startsWith("https://")) {
            text
        } else {
            "https://www.google.com/search?q=${Uri.encode(text)}"
        }
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
