package com.saschl.cameragps.ui.device

import com.saschl.cameragps.service.AssociatedDeviceCompat

/**
 * Toggle this flag to true before taking Play Store screenshots,
 * then set it back to false before shipping production builds.
 */
internal const val SCREENSHOT_MODE = false

internal val mockDevices = listOf(
    AssociatedDeviceCompat(
        id = 1,
        address = "AA:BB:CC:DD:EE:01",
        name = "ILCE-7M4",
        device = null,
        isPaired = true,
    ),
    AssociatedDeviceCompat(
        id = 2,
        address = "AA:BB:CC:DD:EE:02",
        name = "ILCE-6700",
        device = null,
        isPaired = true,
    ),
    AssociatedDeviceCompat(
        id = 3,
        address = "AA:BB:CC:DD:EE:03",
        name = "ZV-E10M2",
        device = null,
        isPaired = true,
    ),
    AssociatedDeviceCompat(
        id = 4,
        address = "AA:BB:CC:DD:EE:04",
        name = "ILCE-9M3",
        device = null,
        isPaired = true,
    ),
)

