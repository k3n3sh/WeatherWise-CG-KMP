package com.weatherwise.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Museum
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.weatherwise.android.R
import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.model.Activity
import com.weatherwise.shared.model.Reason
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
fun activityName(activity: Activity): Int {
    return when (activity) {
        Activity.OUTDOOR_SIGHTSEEING -> R.string.activity_outdoor
        Activity.INDOOR_SIGHTSEEING -> R.string.activity_indoor
        Activity.SKIING -> R.string.activity_skiing
        Activity.SURFING -> R.string.activity_surfing
    }
}

fun activityIcon(activity: Activity): ImageVector {
    return when (activity) {
        Activity.OUTDOOR_SIGHTSEEING -> Icons.Rounded.Hiking
        Activity.INDOOR_SIGHTSEEING -> Icons.Rounded.Museum
        Activity.SKIING -> Icons.Rounded.DownhillSkiing
        Activity.SURFING -> Icons.Rounded.Surfing
    }
}

@StringRes
fun reasonText(reason: Reason): Int {
    return when (reason) {
        Reason.THUNDERSTORM -> R.string.reason_thunderstorm
        Reason.FREEZING_RAIN -> R.string.reason_freezing_rain
        Reason.HEAVY_SNOW -> R.string.reason_heavy_snow
        Reason.STRONG_GUSTS -> R.string.reason_strong_gusts
        Reason.EXTREME_HEAT -> R.string.reason_extreme_heat
        Reason.EXTREME_COLD -> R.string.reason_extreme_cold
        Reason.NO_SNOW -> R.string.reason_no_snow
        Reason.DANGEROUS_WAVES -> R.string.reason_dangerous_waves
        Reason.NO_SEA_DATA -> R.string.reason_no_sea_data
        Reason.MISSING_DATA -> R.string.reason_missing_data
    }
}

@Composable
fun errorText(error: AppError): String {
    return when (error) {
        AppError.Network -> stringResource(R.string.error_network)
        AppError.Timeout -> stringResource(R.string.error_timeout)
        is AppError.Server -> stringResource(R.string.error_server, error.code)
        AppError.Parsing -> stringResource(R.string.error_parsing)
        AppError.Unknown -> stringResource(R.string.error_unknown)
    }
}

// "Thu"
fun shortWeekday(date: LocalDate): String {
    return date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
}

// "Thursday 22 Oct"
fun longDay(date: LocalDate): String {
    return date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
}

// "14:05, 22 Oct" in the phone's own timezone
fun lastUpdatedText(millis: Long): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return dateTime.format(DateTimeFormatter.ofPattern("HH:mm, d MMM", Locale.getDefault()))
}
