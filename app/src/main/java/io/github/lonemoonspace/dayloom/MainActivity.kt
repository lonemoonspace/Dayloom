package io.github.lonemoonspace.dayloom

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.lonemoonspace.dayloom.app.nav.AppRoot
import io.github.lonemoonspace.dayloom.core.ui.theme.DayloomTheme

class MainActivity : ComponentActivity() {

    // Deep links from onCreate and onNewIntent take the same path into Compose.
    // onCreate 与 onNewIntent 带来的深链走同一条路径交给 Compose。
    private var pendingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: it swaps the splash theme for the real one. / 必须在 super.onCreate 之前：它把启动页主题换回正式主题。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // After recreation the back stack is restored; replaying the launch intent would push the destination twice.
        // 重建后返回栈已恢复；再重放启动 Intent 会把目的地重复入栈。
        if (savedInstanceState == null) pendingIntent = intent
        enableEdgeToEdge()
        val graph = DayloomApp.from(this).graph
        setContent {
            DayloomTheme {
                AppRoot(graph = graph, pendingIntent = pendingIntent)
            }
        }
    }

    // singleTop (see the manifest) brings notification taps here instead of stacking a new activity.
    // singleTop（见清单）让通知点击走到这里，而不是再叠一个 Activity。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }
}
