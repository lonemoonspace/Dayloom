package io.github.lonemoonspace.dayloom.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the device position once, when the user taps "Use current location"; never tracks it (design §8).
 * 只在用户点「使用当前位置」时读取一次设备位置；从不持续定位（设计文档 §8）。
 */
interface DeviceLocator {
    fun hasPermission(): Boolean

    /** Null when no position could be obtained (location off, no fix in time). / 拿不到位置（定位关闭、超时无结果）时为 null。 */
    suspend fun locateOnce(locale: Locale): PlaceCandidate?
}

/**
 * The system `LocationManager` with coarse location only: no Google Play services, so an F-Droid build stays possible.
 * 只用粗略定位的系统 `LocationManager`：不依赖 Google Play 服务，保留以后上架 F-Droid 的可能。
 */
class AndroidDeviceLocator(context: Context) : DeviceLocator {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun locateOnce(locale: Locale): PlaceCandidate? {
        if (!hasPermission()) return null
        val location = currentLocation() ?: return null
        val lat = PlacesPolicy.roundCoordinate(location.latitude)
        val lon = PlacesPolicy.roundCoordinate(location.longitude)
        val address = reverseGeocode(lat, lon, locale)
        return PlaceCandidate(
            // Coordinates are numbers, not prose, so they need no translation. / 坐标是数字不是文案，不需要翻译。
            name = address?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: String.format(Locale.ROOT, "%.4f, %.4f", lat, lon),
            detail = address?.let { listOfNotNull(it.adminArea, it.countryName).distinct().joinToString(", ") }.orEmpty(),
            lat = lat,
            lon = lon,
            countryCode = address?.countryCode?.uppercase().orEmpty(),
        )
    }

    // Permission is checked in locateOnce, the only caller. / 唯一的调用方 locateOnce 已检查权限。
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val provider = PROVIDERS.firstOrNull { locationManager.hasProvider(it) && locationManager.isProviderEnabled(it) }
            ?: return null
        val fresh = withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(provider, signal, appContext.mainExecutor) { cont.resume(it) }
                } catch (_: SecurityException) {
                    cont.resume(null)
                } catch (_: IllegalArgumentException) {
                    cont.resume(null)
                }
            }
        }
        return fresh ?: try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double, locale: Locale): Address? {
        // Optional: without a geocoder backend the place is named by its coordinates. / 可选：没有地理编码后端时用坐标命名。
        if (!Geocoder.isPresent()) return null
        return try {
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    Geocoder(appContext, locale).getFromLocation(
                        lat,
                        lon,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        /** Coarse-capable providers, most accurate first. / 粗略定位可用的提供者，精度高的在前。 */
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        const val LOCATE_TIMEOUT_MS = 30_000L
        const val GEOCODE_TIMEOUT_MS = 10_000L
    }
}
