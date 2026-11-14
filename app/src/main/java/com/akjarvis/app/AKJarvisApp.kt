package com.akjarvis.app

import android.app.Application
import com.akjarvis.app.di.ServiceLocator

/**
 * Application entry point. Initializes the manual dependency graph once,
 * so all ViewModels and services share the same repositories and engines.
 */
class AKJarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
