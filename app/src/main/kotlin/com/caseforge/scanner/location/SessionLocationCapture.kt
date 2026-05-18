package com.caseforge.scanner.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

data class SessionGeo(val latitude: Double?, val longitude: Double?)

object SessionLocationCapture {
    fun capture(context: Context): SessionGeo {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SessionGeo(null, null)
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return SessionGeo(null, null)
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return SessionGeo(loc?.latitude, loc?.longitude)
    }
}
