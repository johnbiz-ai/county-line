package net.johnbiz.countyline.county

import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import net.johnbiz.countyline.location.LocationPermissions

/** One location fix and the county it resolves to (`fips` is `null` off the map). Never persisted. */
data class CurrentPosition(val lat: Double, val lng: Double, val fips: String?)

fun interface CurrentPositionSource {
    /** @return a fresh fix, or `null` without permission or when none arrives in time. */
    suspend fun current(): CurrentPosition?
}

/**
 * One-shot fix from the fused provider, reusing a fix up to a minute old, resolved with the same
 * [CountyRepository] resolver the tracker uses so the answer matches crossing alerts.
 */
fun CountyRepository.currentPositionSource(context: Context) = CurrentPositionSource {
    if (!LocationPermissions.hasForegroundLocation(context)) return@CurrentPositionSource null
    val request = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
        .setDurationMillis(FIX_TIMEOUT_MS)
        .build()
    val cancel = CancellationTokenSource()
    @Suppress("MissingPermission") // checked just above
    val location = LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(request, cancel.token)
        .await(cancel) ?: return@CurrentPositionSource null
    val county = resolver().resolve(location.latitude, location.longitude)
    CurrentPosition(location.latitude, location.longitude, county?.fips)
}

private const val MAX_FIX_AGE_MS = 60_000L
private const val FIX_TIMEOUT_MS = 10_000L
