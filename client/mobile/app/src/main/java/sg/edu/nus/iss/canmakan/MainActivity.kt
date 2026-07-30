package sg.edu.nus.iss.canmakan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import sg.edu.nus.iss.canmakan.theme.CanMakanTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CanMakanTheme {
                CanMakanNavGraph()
            }
        }
    }
}
