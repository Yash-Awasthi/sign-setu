package app.signsetu

import android.content.Context
import app.signsetu.data.settings.OfficerLocale
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import app.signsetu.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(OfficerLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Drops taps that pass through an overlay drawn by another app.
        window.decorView.filterTouchesWhenObscured = true
        if (BuildConfig.SECURE_SCREEN) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        // The system splash fades into the first screen instead of cutting to it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) splashScreen.setOnExitAnimationListener { splash ->
            splash.iconView?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(300)?.start()
            splash.animate().alpha(0f).setDuration(300).withEndAction { splash.remove() }.start()
        }
        val container = (application as SignSetuApp).container
        setContent { AppRoot(container) }
    }
}
