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

/** The base map's land colour; the launch window (themes.xml) matches it */
private const val MAP_DARK = 0xFF262626.toInt()

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

/** Each map's labels overlay: a tile provider owns threads and a cache, so a map only ever needs one */
private val labelsOverlays = java.util.WeakHashMap<MapView, TilesOverlay>()

/**
 * Adds the street label/reference layer on top of [ESRI_DARK_GRAY_BASE]. Safe to call again after
 * clearing a map's overlays: it puts the same overlay back. (A new one per call leaked a tile
 * provider, with its threads, on every redraw.) It's released with the map, in MapView.onDetach.
 */
fun MapView.addDarkCanvasLabels(context: Context) {
    // Tiles not loaded yet: the map's own colour, not osmdroid's grey grid
    overlayManager.tilesOverlay.loadingBackgroundColor = MAP_DARK
    overlayManager.tilesOverlay.loadingLineColor = MAP_DARK
    val labelsOverlay = labelsOverlays.getOrPut(this) {
        TilesOverlay(MapTileProviderBasic(context, ESRI_DARK_GRAY_LABELS), context).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
        }
    }
    if (labelsOverlay !in overlays) overlays.add(labelsOverlay)
}
