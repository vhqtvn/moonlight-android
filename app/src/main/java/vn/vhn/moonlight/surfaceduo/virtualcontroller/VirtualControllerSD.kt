package vn.vhn.moonlight.surfaceduo.virtualcontroller

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.opengl.Visibility
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.limelight.Game
import com.limelight.R
import com.limelight.binding.input.virtual_controller.VirtualControllerElement
import com.microsoft.device.display.DisplayMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.math.floor
import kotlin.math.roundToInt

public class GameSD : Game() {
    companion object {
        public var strokeWidthOverride: Int? = null
        private var _isDeviceSurfaceDuo: Boolean? = null
    }

    fun isDeviceSurfaceDuo(): Boolean {
        if (_isDeviceSurfaceDuo == null) {
            _isDeviceSurfaceDuo =
                this.packageManager.hasSystemFeature("com.microsoft.device.display.displaymask")
        }
        return _isDeviceSurfaceDuo!!
    }

    private var frameFirst: FrameLayout? = null;
    private var viewSpacer: View? = null;
    private var frameSecond: FrameLayout? = null;

    override fun setContentView(layoutResID: Int) {
        super.setContentView(if (layoutResID == R.layout.activity_game) R.layout.activity_game_sd
        else layoutResID)
        viewSpacer = findViewById(R.id.spacer)
        frameFirst = findViewById(R.id.first)
        frameSecond = findViewById(R.id.second)
    }

    private var isDuoView = false
    private var isRunning = true

    fun isAppSpanned(r: Rect): Boolean {
//        val f =
//            currentLayoutInfo?.displayFeatures?.firstOrNull { it is FoldingFeature && it.isSeparating }
//        if (f == null) return false
//        r.set(f.bounds)
//        return true
        resources.displayMetrics.apply {
            if (maxOf(widthPixels, heightPixels) >= 2222) {
                when (android.os.Build.MODEL) {
                    "Surface Duo 2" ->
                        r.set(0, 1344, 1982, 1344 + 66)
                    "Surface Duo" ->
                        r.set(0, 1350, 1800, 1350 + 84)
                    else ->
                        return false
                }
                return true
            }
        }
        return false
    }

    fun updateSDLayout() {
        if (virtualController == null || !isRunning) return
        var duoView = false
        var r = Rect()
        if (isDeviceSurfaceDuo() && isAppSpanned(r)) {
            duoView = true
        }

        if ((duoView != isDuoView) || duoView) {
            isDuoView = duoView

            if (isDuoView) {
                strokeWidthOverride = 6
                val parent = (frameSecond!!.parent!! as View)
                val los = IntArray(2)
                parent.getLocationOnScreen(los)
                viewSpacer!!.apply {
                    val lp = layoutParams as LinearLayout.LayoutParams
                    if (lp.height == r.height() && visibility == View.VISIBLE) return@apply
                    lp.height = r.height()
                    lp.weight = 0f
                    setBackgroundColor(Color.DKGRAY)
                    layoutParams = lp
                    visibility = View.VISIBLE
                    requestLayout()
                }
                frameSecond!!.apply {
                    val lp = layoutParams as LinearLayout.LayoutParams
                    val newHeight =
                        (maxOf(los[1], los[0]) + maxOf(parent.width, parent.height)) - r.bottom
                    val newWidth = minOf(parent.width, parent.height)
                    Log.d("VHVH",
                        "offset " + los[0] + "," + los[1] + " + " + parent.height + "  - " + r.bottom)
                    lp.weight = 0f
                    lp.height = newHeight
                    layoutParams = lp
                    visibility = View.VISIBLE
                    requestLayout()
                    virtualController.__private_frame_layout_set(this,
                        newWidth,
                        newHeight)
                }
                frameFirst?.apply {
                    val lp = layoutParams as LinearLayout.LayoutParams
                    lp.weight = 1f
                    lp.height = 0
                    layoutParams = lp
                    visibility = View.VISIBLE
                    requestLayout()
                }
            } else {
                strokeWidthOverride = null
                frameFirst?.also {
                    virtualController.__private_frame_layout_set(it, 0, 0)
                }
                viewSpacer?.apply {
                    val spacerLP = layoutParams
                    spacerLP.height = 0
                    layoutParams = spacerLP
                    visibility = View.GONE
                    requestLayout()
                }
                frameSecond?.apply {
                    val secondLP = layoutParams
                    secondLP.height = 0
                    layoutParams = secondLP
                    visibility = View.GONE
                    requestLayout()
                }
            }
        }
    }

    fun updateColors() {
        if (virtualController == null || !isRunning) return
        runOnUiThread {
            if (isDuoView) {
                for (element in virtualController.elements) {
                    when (element.elementId) {
                        VirtualControllerElement.EID_A ->
                            element.setColors(0xf04caf50.toInt(), 0xf0087f23.toInt())
                        VirtualControllerElement.EID_B ->
                            element.setColors(0xf0ff5722.toInt(), 0xf0c41c00.toInt())
                        VirtualControllerElement.EID_X ->
                            element.setColors(0xf003a9f4.toInt(), 0xf0007ac1.toInt())
                        VirtualControllerElement.EID_Y ->
                            element.setColors(0xf0ffeb3b.toInt(), 0xf0c8b900.toInt())
                    }
                }
                virtualController.setOpacity(100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        lifecycleScope.launch(Dispatchers.Main) {
//            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
//                WindowInfoTracker.getOrCreate(this@GameSD).windowLayoutInfo(this@GameSD).collect {
//                    currentLayoutInfo = it
//                    updateSDLayout()
//                }
//            }
//        }
        isRunning = true
        updateSDLayout()
        updateColors()
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        updateSDLayout()
        super.onConfigurationChanged(newConfig)
        updateColors()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun setPreferredOrientationForCurrentDisplay() {
        val r = Rect()
        if (virtualController != null && isDeviceSurfaceDuo() && isAppSpanned(r)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            Timer().schedule(timerTask {
                runOnUiThread {
                    updateSDLayout()
                    updateColors()
                }
            }, 400)
        } else {
            super.setPreferredOrientationForCurrentDisplay()
        }
    }
}
