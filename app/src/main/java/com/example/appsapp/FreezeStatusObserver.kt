package com.example.appsapp

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object FreezeStatusObserver {
    private val _frozenPackages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val frozenPackages = _frozenPackages.asSharedFlow()

    fun notifyPackageFrozen(packageName: String) {
        _frozenPackages.tryEmit(packageName)
    }
}
