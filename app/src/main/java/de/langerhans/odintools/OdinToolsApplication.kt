package de.langerhans.odintools

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.langerhans.odintools.tools.StickLights
import javax.inject.Inject

@HiltAndroidApp
class OdinToolsApplication : Application() {

    @Inject
    lateinit var stickLights: StickLights

    override fun onCreate() {
        super.onCreate()
        // NovaTools' process starts after each boot and update (its accessibility service): the stick lights
        // tool doesn't survive a reboot, and an update moves it
        stickLights.ensureRunning()
    }
}
