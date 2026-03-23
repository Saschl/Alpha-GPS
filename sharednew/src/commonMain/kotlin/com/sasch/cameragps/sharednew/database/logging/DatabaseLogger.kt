package com.sasch.cameragps.sharednew.database.logging

import com.diamondedge.logging.Logger
import kotlin.time.Clock

class DatabaseLogger(private val logRepository: LogRepository) : Logger {
    override fun verbose(tag: String, msg: String) {
        logRepository.insertLog(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            priority = 1,
            tag = tag,
            message = msg,
            exception = null
        )
    }

    override fun debug(tag: String, msg: String) {
        logRepository.insertLog(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            priority = 2, // INFO
            tag = tag,
            message = msg,
            exception = null
        )
    }

    override fun info(tag: String, msg: String) {
        logRepository.insertLog(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            priority = 3,
            tag = tag,
            message = msg,
            exception = null
        )
    }

    override fun warn(tag: String, msg: String, t: Throwable?) {
        logRepository.insertLog(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            priority = 4,
            tag = tag,
            message = msg,
            exception = null
        )
    }

    override fun error(tag: String, msg: String, t: Throwable?) {
        logRepository.insertLog(
            timestamp = Clock.System.now().toEpochMilliseconds(),
            priority = 5,
            tag = tag,
            message = msg,
            exception = null
        )
    }

    override fun isLoggingVerbose(): Boolean = false

    override fun isLoggingDebug(): Boolean = false

    override fun isLoggingInfo(): Boolean = true

    override fun isLoggingWarning(): Boolean = true

    override fun isLoggingError(): Boolean = true
}
