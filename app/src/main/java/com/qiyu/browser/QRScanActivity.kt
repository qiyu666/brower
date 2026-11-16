package com.qiyu.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** 菜单「扫码」：扫二维码，结果在浏览器新标签打开（非网址则转搜索） */
class QRScanActivity : ComponentActivity() {

    private lateinit var decoratedView: DecoratedBarcodeView
    private lateinit var captureManager: CaptureManager
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            captureManager.decode()
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        decoratedView = DecoratedBarcodeView(this)
        decoratedView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                result.text?.let { openResult(it) }
            }
        })
        captureManager = CaptureManager(this, decoratedView)
        setContentView(
            decoratedView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        captureManager.onResume()
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
