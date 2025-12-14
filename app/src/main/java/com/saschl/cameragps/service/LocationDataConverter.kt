package com.saschl.cameragps.service

import android.location.Location
import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Utility class for converting location and time data to byte arrays for Sony camera communication
 */
object LocationDataConverter {

    /**
     * Converts location coordinates to byte array format expected by Sony cameras
     */
    fun convertCoordinates(location: Location): ByteArray {
        val latitude = (location.latitude * 10000000).toInt()
        val latitudeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(latitude).array()

        val longitude = (location.longitude * 10000000).toInt()
        val longitudeBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(longitude).array()

        return latitudeBytes + longitudeBytes
    }

    /**
     * Converts current date/time to byte array format expected by Sony cameras
     */
    fun convertDate(): ByteArray {
        val currentDateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"))
        val yearBytes = currentDateTime.year.toShort().toByteArray()

        return byteArrayOf(
            yearBytes[0],
            yearBytes[1],
            currentDateTime.monthValue.toByte(),
            currentDateTime.dayOfMonth.toByte(),
            currentDateTime.hour.toByte(),
            currentDateTime.minute.toByte(),
            currentDateTime.second.toByte()
        )
    }

    /**
     * Converts timezone offset to byte array format
     */
    fun convertTimeZoneOffset(timezoneId: ZoneId): ByteArray {
        val offsetMin = timezoneId.rules.getStandardOffset(Instant.now()).totalSeconds / 60
        return offsetMin.toShort().toByteArray()
    }

    /**
     * Converts DST offset to byte array format
     */
    fun convertDstOffset(timezoneId: ZoneId): ByteArray {
        if (!timezoneId.rules.isDaylightSavings(Instant.now())) {
            return byteArrayOf(0, 0) // No DST offset
        }
        val offsetDstMin = timezoneId.rules.getDaylightSavings(Instant.now()).toMinutes().toInt()
        return offsetDstMin.toShort().toByteArray()
    }

    /**
     * Extension function to convert Short to ByteArray
     */
    private fun Short.toByteArray(): ByteArray {
        return byteArrayOf((this.toInt() shr 8).toByte(), this.toByte())
    }

    /**
     * Builds the complete location data packet to send to the camera
     */
    fun buildLocationDataPacket(
        locationDataConfig: LocationDataConfig,
        locationResult: Location
    ): ByteArray {
        val timeZoneId = locationDataConfig.timeZoneId
        val paddingBytes = ByteArray(65)

        val locationBytes = convertCoordinates(locationResult)
        val dateBytes = convertDate()
        val timeZoneOffsetBytes = convertTimeZoneOffset(timeZoneId)
        val dstOffsetBytes = convertDstOffset(timeZoneId)

        val data = ByteArray(locationDataConfig.dataSize)
        var currentPosition = 0

        // Copy fixed header bytes
        System.arraycopy(locationDataConfig.fixedBytes, 0, data, currentPosition, locationDataConfig.fixedBytes.size)
        currentPosition += locationDataConfig.fixedBytes.size

        // Copy location data
        System.arraycopy(locationBytes, 0, data, currentPosition, locationBytes.size)
        currentPosition += locationBytes.size

        // Copy date data
        System.arraycopy(dateBytes, 0, data, currentPosition, dateBytes.size)
        currentPosition += dateBytes.size

        // Copy padding
        System.arraycopy(paddingBytes, 0, data, currentPosition, paddingBytes.size)

        // Add timezone and DST data if required
        if (locationDataConfig.shouldSendTimeZoneAndDst) {
            currentPosition += paddingBytes.size
            System.arraycopy(timeZoneOffsetBytes, 0, data, currentPosition, timeZoneOffsetBytes.size)
            currentPosition += timeZoneOffsetBytes.size
            System.arraycopy(dstOffsetBytes, 0, data, currentPosition, dstOffsetBytes.size)
        }

        return data
    }

    // For cameras that have explicit time sync
    fun serializeTimeAreaData(zonedDateTime: ZonedDateTime): ByteArray {
        val systemZone = ZoneId.systemDefault()
        val instant = zonedDateTime.toInstant()
        val standardOffsetMinutes = systemZone.rules.getStandardOffset(instant).totalSeconds / 60
        val hoursComponent = abs(standardOffsetMinutes / 60)
        val offsetMinutesComponent = abs(standardOffsetMinutes % 60)
        val signedOffsetHourByte = (if (standardOffsetMinutes < 0) -hoursComponent else hoursComponent).toByte()

        return ByteArray(13).apply {
            this[0] = 12
            this[1] = 0
            this[2] = 0
            zonedDateTime.year.toShort().toByteArray().let {
                this[3] = it[0]
                this[4] = it[1]
            }
            this[5] = zonedDateTime.monthValue.toByte()
            this[6] = zonedDateTime.dayOfMonth.toByte()
            this[7] = zonedDateTime.hour.toByte()
            this[8] = zonedDateTime.minute.toByte()
            this[9] = zonedDateTime.second.toByte()
            this[10] = if (systemZone.rules.isDaylightSavings(instant)) 1 else 0
            this[11] = signedOffsetHourByte
            this[12] = offsetMinutesComponent.toByte()
        }
    }
}

/**
 * Data class representing location data packet configuration
 */
data class LocationDataConfig(
    val shouldSendTimeZoneAndDst: Boolean,
    val timeZoneId: ZoneId = ZoneId.systemDefault()
) {
    val dataSize: Int = if (shouldSendTimeZoneAndDst) 95 else 91

    val fixedBytes: ByteArray = byteArrayOf(
        0x00,
        if (shouldSendTimeZoneAndDst) 0x5D else 0x59,
        0x08,
        0x02,
        0xFC.toByte(),
        if (shouldSendTimeZoneAndDst) 0x03 else 0x00,
        0x00,
        0x00,
        0x10,
        0x10,
        0x10
    )
}


/**
 * Extension function to convert ByteArray to hex string for debugging
 */
fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
