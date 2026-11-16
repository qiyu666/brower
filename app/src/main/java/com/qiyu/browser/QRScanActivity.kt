package com.qiyu.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.zxing.DecodeHintType
import com.journeyapps.barcodescanner.DecoderFactory
import com.journeyapps.barcodescanner.DecoderView

/** 菜单「扫码」：扫 QR / EAN 码，结果在浏览器新标签打开 */
class QRScanActivity : ComponentActivity() {

    private lateinit var decoderView: DecoderView
    private var cameraGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        decoderView = DecoderView(this)
        decoderView.setDecoderFactory(DecoderFactory().apply {
            setHints(mapOf(DecodeHintType.POINT_TO_POINT to true))
        })
        decoderView.decodeContinuous { barcode, _ ->
            val text = barcode?.text ?: return@decodeContinuous
            openResult(text)
        }

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            addView(decoderView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(root)

        cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            cameraGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (!cameraGranted) {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (cameraGranted) decoderView.resume()
    }

    override fun onPause() {
        decoderView.pause()
        super.onPause()
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
