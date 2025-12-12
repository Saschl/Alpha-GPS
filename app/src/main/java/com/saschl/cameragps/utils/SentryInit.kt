package com.saschl.cameragps.utils

import android.content.Context
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.timber.SentryTimberIntegration

object SentryInit {

    fun initSentry(context: Context) {
        SentryAndroid.init(context) { options ->
            options.isSendDefaultPii = false
            val macRegex = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")

            options.logs.isEnabled = true

            options.addIntegration(
                SentryTimberIntegration(
                    minEventLevel = SentryLevel.ERROR,
                    minBreadcrumbLevel = SentryLevel.INFO,
                    minLogsLevel = SentryLogLevel.DEBUG
                )
            )
            options.logs.beforeSend = SentryOptions.Logs.BeforeSendLogCallback { event ->
                // Modify the event here if needed
                event.body = event.body.replace(macRegex, "XX:XX:XX:XX:XX:XX")
                event
            }
        }
    }

}