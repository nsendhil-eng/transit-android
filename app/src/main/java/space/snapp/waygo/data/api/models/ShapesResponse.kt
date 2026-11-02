package space.snapp.waygo.data.api.models

/** /api/shapes: shapeId -> [[lat, lon], …], the whole route in travel order */
data class ShapesResponse(val shapes: Map<String, List<List<Double>>> = emptyMap())
