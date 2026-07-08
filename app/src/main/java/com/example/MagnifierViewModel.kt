package com.example

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// A kimerevített nyers képkocka nem fér el a savedInstanceState-ben (TransactionTooLargeException),
// ezért konfigurációváltásnál (pl. forgatás) ViewModel őrzi meg.
class MagnifierViewModel : ViewModel() {
    var rawFrozenBitmap by mutableStateOf<Bitmap?>(null)
}

