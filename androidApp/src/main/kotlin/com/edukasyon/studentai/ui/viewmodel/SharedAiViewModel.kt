package com.edukasyon.studentai.ui.viewmodel

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun sharedAiViewModel(): AiViewModel {
    val activity = LocalContext.current.findComponentActivity()
        ?: error("AI screen requires a ComponentActivity")
    return hiltViewModel(viewModelStoreOwner = activity)
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
