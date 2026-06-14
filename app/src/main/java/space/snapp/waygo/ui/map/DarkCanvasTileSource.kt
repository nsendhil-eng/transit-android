package space.snapp.waygo.ui.map

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Esri tile services use a z/y/x URL layout (row before column), unlike the
 * z/x/y layout assumed by osmdroid's XYTileSource.
 */
class EsriTileSource(name: String, baseUrl: String) : OnlineTileSourceBase(
    name, 0, 16, 256, "",
    arrayOf(baseUrl)
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "$zoom/$y/$x"
    }
}

/** Muted dark gray basemap — no labels, low contrast. */
val ESRI_DARK_GRAY_BASE = EsriTileSource(
    "Esri.DarkGrayCanvas",
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/"
)

/** Street/place name labels + road lines, transparent background, drawn on top of the base. */
private val ESRI_DARK_GRAY_LABELS = EsriTileSource(
    "Esri.DarkGrayReference",
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/"
)

/** Adds the street label/reference layer on top of [ESRI_DARK_GRAY_BASE]. */
fun MapView.addDarkCanvasLabels(context: Context) {
    val labelsOverlay = TilesOverlay(MapTileProviderBasic(context, ESRI_DARK_GRAY_LABELS), context)
    labelsOverlay.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
    overlays.add(labelsOverlay)
}
