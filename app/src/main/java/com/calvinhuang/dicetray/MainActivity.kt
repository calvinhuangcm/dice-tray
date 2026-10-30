package com.calvinhuang.dicetray

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.calvinhuang.dicetray.ui.DiceTrayApp
import com.calvinhuang.dicetray.ui.DiceTrayTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DiceRollerViewModel by viewModels {
        DiceRollerViewModel.Factory(application)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val selectedLanguage = LanguageManager.current(this)
        setContent {
            DiceTrayTheme {
                DiceTrayApp(
                    viewModel = viewModel,
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { language ->
                        LanguageManager.save(this, language)
                        recreate()
                    },
                )
            }
        }
    }
}
