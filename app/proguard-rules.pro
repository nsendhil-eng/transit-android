# Gson reads and writes these by their field names, so R8 must not rename or remove them
-keep class space.snapp.waygo.data.api.models.** { *; }
-keep class space.snapp.waygo.data.models.** { *; }
-keep class space.snapp.waygo.ui.map.HomeSnapshot { *; }
