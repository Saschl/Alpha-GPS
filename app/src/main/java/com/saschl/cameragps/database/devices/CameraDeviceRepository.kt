package com.saschl.cameragps.database.devices

import android.content.Context
import com.saschl.cameragps.database.logging.LogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class CameraDeviceRepository(context: Context) {

    private val logDao = LogDatabase.getDatabase(context).cameraDeviceDao()
    private val scope = CoroutineScope(Dispatchers.IO)

}