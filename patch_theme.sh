sed -i 's/MyApplicationTheme {//g' app/src/main/java/com/example/MainActivity.kt
sed -i 's/    DriveSphereApp()/DriveSphereApp()/g' app/src/main/java/com/example/MainActivity.kt
sed -i '/            }/d' app/src/main/java/com/example/MainActivity.kt

# Put it back correctly:
cat << 'MAIN_EOF' > app/src/main/java/com/example/MainActivity.kt
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveSphereApp()
        }
    }
}
MAIN_EOF

sed -i '/import com.example.viewmodel.TripEvent/a import com.example.ui.theme.MyApplicationTheme\nimport androidx.compose.foundation.isSystemInDarkTheme' app/src/main/java/com/example/AppNavigation.kt

sed -i 's/val pastTrips by dbViewModel.pastTrips.collectAsState()/val pastTrips by dbViewModel.pastTrips.collectAsState()\n    \n    val systemDark = isSystemInDarkTheme()\n    val isDark = state.isDarkMode ?: systemDark\n\n    MyApplicationTheme(darkTheme = isDark) {/g' app/src/main/java/com/example/AppNavigation.kt

echo "    }" >> app/src/main/java/com/example/AppNavigation.kt
