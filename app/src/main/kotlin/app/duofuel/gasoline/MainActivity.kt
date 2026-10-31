package app.duofuel.gasoline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.duofuel.gasoline.ui.GasoLineRoot
import app.duofuel.gasoline.ui.theme.GasoLineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GasoLineTheme {
                GasoLineRoot()
            }
        }
    }
}
