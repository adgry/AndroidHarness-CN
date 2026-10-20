package com.androidharness.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Apply Chinese to platform widgets and date/number formatters as well as app copy. */
internal fun chineseContext(base: Context): Context {
    val locale = Locale.SIMPLIFIED_CHINESE
    Locale.setDefault(locale)
    return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    })
}
