package space.snapp.waygo.data.api.models

data class PlanDelayResponse(
    val delays: Map<String, Int> = emptyMap()
)
