package sg.edu.nus.iss.canmakan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import sg.edu.nus.iss.canmakan.utils.ProfileDrawerContent
import sg.edu.nus.iss.canmakan.dietaryreq.EditDietaryRequirementsSheet
import sg.edu.nus.iss.canmakan.history.HistoryScreen
import sg.edu.nus.iss.canmakan.productdetail.ProductDetailScreen
import sg.edu.nus.iss.canmakan.scanner.ScannerScreen
import sg.edu.nus.iss.canmakan.theme.CanMakanTheme
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.theme.CanMakanTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanMakanTheme {
                CanMakanNavGraph()
            }
        }
    }
}
