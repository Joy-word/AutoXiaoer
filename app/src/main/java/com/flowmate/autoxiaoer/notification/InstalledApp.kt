package com.flowmate.autoxiaoer.notification

import android.graphics.drawable.Drawable

/**
 * Represents an installed app available for selection as a notification trigger target.
 *
 * @property packageName The app's package name (unique identifier)
 * @property label The app's display name shown to the user
 * @property icon The app's launcher icon (not persisted, loaded on demand)
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
)
