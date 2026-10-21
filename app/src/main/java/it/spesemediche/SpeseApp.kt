package it.spesemediche

import android.app.Application
import it.spesemediche.data.AppDatabase
import it.spesemediche.storage.DocumentStorage

class SpeseApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val storage by lazy { DocumentStorage(this) }
}
