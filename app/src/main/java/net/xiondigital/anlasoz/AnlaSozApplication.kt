package net.xiondigital.anlasoz

import android.app.Application

class AnlaSozApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
