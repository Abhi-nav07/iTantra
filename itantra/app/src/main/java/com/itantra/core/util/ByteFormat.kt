package com.itantra.core.util

/** Formats a byte count as a short human-readable string (e.g. "62 MB"). */
fun Long.toHumanReadableBytes(): String {
    if (this <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = this.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${this} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}
