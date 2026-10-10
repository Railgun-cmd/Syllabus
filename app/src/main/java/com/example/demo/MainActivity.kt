package com.example.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.ui.ScheduleScreen
import com.example.demo.ui.ScheduleViewModel
import com.example.demo.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScheduleTheme {
                val vm: ScheduleViewModel = viewModel()
                ScheduleScreen(vm)
            }
        }
    }
}
