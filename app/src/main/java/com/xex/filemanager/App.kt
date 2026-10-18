package com.xex.filemanager

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.xex.filemanager.fs.Fs
import com.xex.filemanager.fs.ShizukuManager

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Fs.mode = getSharedPreferences("settings", MODE_PRIVATE).getInt("shizukuMode", 1)
        ShizukuManager.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
