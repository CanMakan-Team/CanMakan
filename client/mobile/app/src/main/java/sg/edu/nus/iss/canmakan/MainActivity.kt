package sg.edu.nus.iss.canmakan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import sg.edu.nus.iss.canmakan.navigation.CanMakanApp
import sg.edu.nus.iss.canmakan.shared.ui.theme.CanMakanTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanMakanTheme {
                CanMakanApp()
            }
        }
    }
}
