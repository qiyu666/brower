package com.qiyu.browser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/** 菜单「扫码」：扫二维码与一维条形码（EAN/UPC/Code128 等），结果在浏览器新标签打开；顶栏可开关手电筒 */
class QRScanActivity : ComponentActivity() {

    private lateinit var decoratedView: DecoratedBarcodeView
    private lateinit var captureManager: CaptureManager
    private var torchOn = false
    private lateinit var torchButton: TextView

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

        // 显式支持 2D 二维码 + 常用 1D 条形码（用 Java 数组构造，规避编译环境对 Kotlin 集合的平台类型问题）
        decoratedView = DecoratedBarcodeView(this).apply {
            setDecoderFactory(DefaultDecoderFactory(
                java.util.Arrays.asList(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93
                )
            ))
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    result.text?.let { openResult(it) }
                }
            })
        }
        captureManager = CaptureManager(this, decoratedView)

        // 根布局：相机预览 + 顶栏（关闭 | 手电筒）
        val root = FrameLayout(this)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.setBackgroundColor(Color.BLACK)
        root.addView(
            decoratedView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setBackgroundColor(0x99000000.toInt())
        topBar.gravity = Gravity.CENTER_VERTICAL
        val padPx = (12 * resources.displayMetrics.density).toInt()
        topBar.setPadding(padPx, padPx, padPx, padPx)
        topBar.addView(buildTextButton("✕ 关闭") { finish() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        topBar.addView(View(this),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        torchButton = buildTextButton("手电筒") { toggleTorch() }
        topBar.addView(torchButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        )
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildTextButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            val p = (10 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { onClick() }
        }
    }

    private fun toggleTorch() {
        try {
            torchOn = !torchOn
            if (torchOn) {
                decoratedView.setTorchOn()
            } else {
                decoratedView.setTorchOff()
            }
            torchButton.text = if (torchOn) "关灯" else "手电筒"
        } catch (e: Exception) {
            torchOn = false
            torchButton.text = "手电筒"
            Toast.makeText(this, "此设备不支持手电筒", Toast.LENGTH_SHORT).show()
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
        try {
            if (torchOn) decoratedView.setTorchOff()
        } catch (e: Exception) {}
        captureManager.onDestroy()
        super.onDestroy()
    }

    private fun openResult(text: String) {
        try {
            if (torchOn) decoratedView.setTorchOff()
        } catch (e: Exception) {}
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
