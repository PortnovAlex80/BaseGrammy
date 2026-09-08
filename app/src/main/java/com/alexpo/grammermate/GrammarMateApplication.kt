package com.alexpo.grammermate

import android.app.Application
import android.os.StrictMode
import com.alexpo.grammermate.shared.AuditLogger

/**
 * Custom Application class that holds the AppContainer for dependency injection.
 *
 * NOTE: Must be registered in AndroidManifest.xml via android:name=".GrammarMateApplication"
 * on the <application> tag. This is done separately from the architecture migration.
 */
class GrammarMateApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Phase 1 regression guard: main-thread disk I/O must stay out of
            // composition. penaltyLog only — never penaltyDeath, so Robolectric
            // tests instantiating this Application cannot fail on violations.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
        }
        AuditLogger.initialize(this)
        AuditLogger.getInstance().startSession(appVersion = "1.7")
    }
}
