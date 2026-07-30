package sg.edu.nus.iss.canmakan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import sg.edu.nus.iss.canmakan.data.SampleData
import sg.edu.nus.iss.canmakan.ui.components.ProfileDrawerContent
import sg.edu.nus.iss.canmakan.ui.screens.EditDietaryRequirementsSheet
import sg.edu.nus.iss.canmakan.ui.screens.HistoryScreen
import sg.edu.nus.iss.canmakan.ui.screens.ProductDetailScreen
import sg.edu.nus.iss.canmakan.ui.screens.ScannerScreen
import sg.edu.nus.iss.canmakan.ui.theme.CanMakanTheme
import kotlinx.coroutines.launch

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
