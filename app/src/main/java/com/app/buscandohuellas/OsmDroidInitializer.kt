package com.app.buscandohuellas

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

object OsmDroidInitializer {
    fun initialize(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName

            val cacheDir = File(context.cacheDir, "osmdroid")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            osmdroidTileCache = cacheDir
        }
    }
}
