package space.snapp.waygo.ui.plan

import org.osmdroid.util.GeoPoint

/** Decodes a Google encoded polyline string into a list of GeoPoints. */
fun decodePolyline(encoded: String): List<GeoPoint> {
    val result = mutableListOf<GeoPoint>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var b: Int
        var shift = 0
        var result2 = 0
        do {
            b = encoded[index++].code - 63
            result2 = result2 or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLat = if (result2 and 1 != 0) (result2 shr 1).inv() else result2 shr 1
        lat += dLat
        shift = 0
        result2 = 0
        do {
            b = encoded[index++].code - 63
            result2 = result2 or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLng = if (result2 and 1 != 0) (result2 shr 1).inv() else result2 shr 1
        lng += dLng
        result.add(GeoPoint(lat / 1e5, lng / 1e5))
    }
    return result
}
