package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.AppDatabase
import com.example.data.DocumentRepository
import com.example.ui.DocDriverViewModel
import com.example.ui.DocDriverViewModelFactory
import com.example.ui.screens.MainUiContent
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Instantiate DB and Repo
    val database = AppDatabase.getDatabase(this)
    val repository = DocumentRepository(database.documentDao())
    val factory = DocDriverViewModelFactory(repository)
    val viewModel: DocDriverViewModel by viewModels { factory }
    
    setContent {
      MyApplicationTheme {
        MainUiContent(viewModel = viewModel)
      }
    }
  }
}
