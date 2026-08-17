package com.harsh.jarvis.maps

import android.content.Context
import android.location.Location
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.PriorityQueue
import kotlin.math.*

internal data class RouteNode(val id: String, val point: GeoPoint)
internal data class RouteEdge(val to: String, val meters: Double, val name: String? = null)
internal data class RouteInstruction(val text: String, val distanceMeters: Double)
internal data class OfflineRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val instructions: List<RouteInstruction>
)

/** Offline routing graph. Loaded once per process and routed with A* to avoid repeatedly
 * parsing a large city/office graph on every GPS update. */
internal object OfflineRouteEngine {
    private data class State(val id: String, val g: Double, val f: Double) : Comparable<State> {
        override fun compareTo(other: State): Int = f.compareTo(other.f)
    }

    @Volatile private var cached: Pair<Map<String, RouteNode>, Map<String, List<RouteEdge>>>? = null
    @Volatile private var cachedIndex: GridIndex? = null
    private val lock = Any()

    /** Small geographic grid used to find the nearest graph node without scanning the whole city. */
    private class GridIndex(nodes: Map<String, RouteNode>, private val cellSize: Double = 0.01) {
        private val cells = mutableMapOf<Pair<Int, Int>, MutableList<RouteNode>>()
        init {
            nodes.values.forEach { node ->
                cells.getOrPut(key(node.point)) { mutableListOf() }.add(node)
            }
        }
        private fun key(point: GeoPoint) = (floor(point.latitude / cellSize).toInt()) to
            (floor(point.longitude / cellSize).toInt())

        fun nearest(point: GeoPoint): RouteNode? {
            val center = key(point)
            var best: RouteNode? = null
            var bestDistance = Double.POSITIVE_INFINITY
            for (radius in 0..64) {
                for (lat in center.first - radius..center.first + radius) {
                    for (lon in center.second - radius..center.second + radius) {
                        for (node in cells[lat to lon].orEmpty()) {
                            val d = distance(node.point, point)
                            if (d < bestDistance) { bestDistance = d; best = node }
                        }
                    }
                }
                // Once the next unvisited ring is farther away than the current
                // best distance, the nearest node is guaranteed to be found.
                val ringLowerBoundMeters = radius * cellSize * 111_000.0
                if (best != null && ringLowerBoundMeters > bestDistance + cellSize * 111_000.0 * 1.5) {
                    return best
                }
            }
            return best ?: nodes.values.minByOrNull { distance(it.point, point) }
        }
    }

    fun load(context: Context): Pair<Map<String, RouteNode>, Map<String, List<RouteEdge>>> {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val base = File(context.filesDir, "offline_maps")
            val file = File(base, "route_graph.json")
            val loaded = runCatching {
                val root = JSONObject(file.readText())
                val nodes = mutableMapOf<String, RouteNode>()
                val nodesJson = root.optJSONArray("nodes") ?: return@runCatching emptyMap<String, RouteNode>() to emptyMap()
                for (i in 0 until nodesJson.length()) {
                    val n = nodesJson.getJSONObject(i)
                    nodes[n.getString("id")] = RouteNode(n.getString("id"), GeoPoint(n.getDouble("lat"), n.getDouble("lon")))
                }
                val graph = nodes.keys.associateWith { mutableListOf<RouteEdge>() }.toMutableMap()
                root.optJSONArray("edges")?.let { edges ->
                    for (i in 0 until edges.length()) {
                        val e = edges.getJSONObject(i)
                        val from = e.getString("from"); val to = e.getString("to")
                        if (nodes.containsKey(from) && nodes.containsKey(to)) {
                            val meters = e.optDouble("meters", distance(nodes.getValue(from).point, nodes.getValue(to).point))
                            val name = e.optString("name").takeIf { it.isNotBlank() }
                            graph.getValue(from).add(RouteEdge(to, meters, name))
                            if (e.optBoolean("bidirectional", true)) graph.getValue(to).add(RouteEdge(from, meters, name))
                        }
                    }
                }
                nodes to graph
            }.getOrElse { emptyMap<String, RouteNode>() to emptyMap() }
            cached = loaded
            cachedIndex = GridIndex(loaded.first)
            return loaded
        }
    }

    fun clearCache() { synchronized(lock) { cached = null; cachedIndex = null } }

    private fun nearestNode(nodes: Map<String, RouteNode>, point: GeoPoint): RouteNode? {
        val index = cachedIndex
        if (index != null && cached?.first === nodes) return index.nearest(point)
        return nodes.values.minByOrNull { distance(it.point, point) }
    }

    fun route(nodes: Map<String, RouteNode>, graph: Map<String, List<RouteEdge>>, start: GeoPoint, goal: GeoPoint): OfflineRoute? {
        if (nodes.isEmpty()) return null
        val startId = nearestNode(nodes, start)?.id ?: return null
        val goalId = nearestNode(nodes, goal)?.id ?: return null
        val gScore = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val previous = mutableMapOf<String, Pair<String, RouteEdge>?>()
        val queue = PriorityQueue<State>()
        gScore[startId] = 0.0
        val startNode = nodes[startId] ?: return null
        val goalNode = nodes[goalId] ?: return null
        queue.add(State(startId, 0.0, heuristic(startNode.point, goalNode.point)))

        while (queue.isNotEmpty()) {
            val cur = queue.poll()
            if (cur.g > gScore.getValue(cur.id) + 1e-6) continue
            if (cur.id == goalId) break
            for (edge in graph[cur.id].orEmpty()) {
                val nextG = cur.g + edge.meters
                if (nextG + 1e-6 < gScore.getValue(edge.to)) {
                    gScore[edge.to] = nextG
                    previous[edge.to] = cur.id to edge
                    val nextNode = nodes[edge.to] ?: continue
                    val h = heuristic(nextNode.point, goalNode.point)
                    queue.add(State(edge.to, nextG, nextG + h))
                }
            }
        }
        if (!gScore.containsKey(goalId)) return null

        val ids = mutableListOf<String>()
        val edges = mutableListOf<RouteEdge>()
        var id: String? = goalId
        while (id != null) {
            ids.add(id)
            val prev = previous[id]
            if (prev != null) { id = prev.first; edges.add(prev.second) } else id = null
        }
        ids.reverse(); edges.reverse()
        val points = ids.mapNotNull { nodes[it]?.point }
        if (points.size != ids.size) return null
        return OfflineRoute(points, gScore.getValue(goalId), buildInstructions(points, edges))
    }

    private fun buildInstructions(points: List<GeoPoint>, edges: List<RouteEdge>): List<RouteInstruction> {
        if (points.size < 2 || edges.isEmpty()) return emptyList()
        val result = mutableListOf<RouteInstruction>()
        var groupDistance = edges.first().meters
        var groupName = edges.first().name
        var groupAction = "Start"
        var previousBearing = bearing(points[0], points[1])

        for (i in 1 until edges.size) {
            val currentBearing = bearing(points[i], points[i + 1])
            val action = turnLabel(previousBearing, currentBearing)
            val nameChanged = edges[i].name != null && edges[i].name != groupName
            if (action != "Continue straight" || nameChanged) {
                result.add(RouteInstruction(
                    text = groupAction + (groupName?.let { " on $it" } ?: ""),
                    distanceMeters = groupDistance
                ))
                groupDistance = edges[i].meters
                groupName = edges[i].name ?: groupName
                groupAction = action
            } else {
                groupDistance += edges[i].meters
            }
            previousBearing = currentBearing
        }
        result.add(RouteInstruction("Arrive at destination", groupDistance))
        return result
    }

    private fun turnLabel(previous: Double, next: Double): String {
        var delta = (next - previous + 540.0) % 360.0 - 180.0
        val absDelta = abs(delta)
        return when {
            absDelta < 20 -> "Continue straight"
            absDelta < 55 -> if (delta > 0) "Bear right" else "Bear left"
            absDelta < 135 -> if (delta > 0) "Turn right" else "Turn left"
            else -> "Make a U-turn"
        }
    }

    private fun heuristic(a: GeoPoint, b: GeoPoint): Double = distance(a, b)

    private fun distance(a: GeoPoint, b: GeoPoint): Double {
        val r = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r)
        return r[0].toDouble()
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
