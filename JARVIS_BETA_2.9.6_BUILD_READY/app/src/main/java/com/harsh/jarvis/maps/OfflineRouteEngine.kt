package com.harsh.jarvis.maps

import android.content.Context
import android.location.Location
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal data class RouteNode(
    val id: String,
    val point: GeoPoint
)

internal data class RouteEdge(
    val to: String,
    val meters: Double,
    val name: String? = null
)

internal data class RouteInstruction(
    val text: String,
    val distanceMeters: Double
)

internal data class OfflineRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val instructions: List<RouteInstruction>
)

/**
 * Fully local offline routing engine.
 *
 * Features:
 * - Safe JSON loading
 * - Explicit Kotlin types to avoid inference/compiler issues
 * - Cached graph
 * - Cached spatial index
 * - A* routing
 * - Safe handling of missing nodes/edges
 * - Defensive handling of malformed route_graph.json
 * - No network access
 */
internal object OfflineRouteEngine {

    private data class GraphData(
        val nodes: Map<String, RouteNode>,
        val graph: Map<String, List<RouteEdge>>
    )

    private data class State(
        val id: String,
        val g: Double,
        val f: Double
    ) : Comparable<State> {

        override fun compareTo(other: State): Int {
            return f.compareTo(other.f)
        }
    }

    @Volatile
    private var cachedGraph: GraphData? = null

    @Volatile
    private var cachedIndex: GridIndex? = null

    private val lock = Any()

    /**
     * Geographic grid used to find nearby graph nodes efficiently.
     */
    private class GridIndex(
        nodes: Map<String, RouteNode>,
        private val cellSize: Double = 0.01
    ) {

        private val cells: MutableMap<Pair<Int, Int>, MutableList<RouteNode>> =
            mutableMapOf()

        init {
            for (node in nodes.values) {
                val cell = key(node.point)

                val list: MutableList<RouteNode> =
                    cells.getOrPut(cell) {
                        mutableListOf()
                    }

                list.add(node)
            }
        }

        private fun key(point: GeoPoint): Pair<Int, Int> {
            val latCell = floor(point.latitude / cellSize).toInt()
            val lonCell = floor(point.longitude / cellSize).toInt()

            return Pair(latCell, lonCell)
        }

        fun nearest(point: GeoPoint): RouteNode? {
            if (cells.isEmpty()) {
                return null
            }

            val center: Pair<Int, Int> = key(point)

            var best: RouteNode? = null
            var bestDistance = Double.POSITIVE_INFINITY

            /*
             * Search progressively larger rings.
             */
            for (radius in 0..64) {

                val minLat = center.first - radius
                val maxLat = center.first + radius
                val minLon = center.second - radius
                val maxLon = center.second + radius

                for (lat in minLat..maxLat) {
                    for (lon in minLon..maxLon) {

                        /*
                         * Only inspect the outer ring after radius > 0.
                         * This avoids repeatedly scanning inner cells.
                         */
                        if (radius > 0) {
                            val onRing =
                                lat == minLat ||
                                lat == maxLat ||
                                lon == minLon ||
                                lon == maxLon

                            if (!onRing) {
                                continue
                            }
                        }

                        val cellNodes: List<RouteNode> =
                            cells[Pair(lat, lon)].orEmpty()

                        for (node in cellNodes) {
                            val currentDistance =
                                distance(node.point, point)

                            if (currentDistance < bestDistance) {
                                bestDistance = currentDistance
                                best = node
                            }
                        }
                    }
                }

                if (best != null) {
                    val ringLowerBoundMeters =
                        radius.toDouble() *
                            cellSize *
                            111_000.0

                    if (
                        ringLowerBoundMeters >
                        bestDistance +
                        cellSize * 111_000.0
                    ) {
                        return best
                    }
                }
            }

            return best
        }
    }

    /**
     * Loads the offline graph.
     *
     * Expected file:
     *
     * files/offline_maps/route_graph.json
     */
    fun load(
        context: Context
    ): Pair<Map<String, RouteNode>, Map<String, List<RouteEdge>>> {

        val existing: GraphData? = cachedGraph

        if (existing != null) {
            return Pair(existing.nodes, existing.graph)
        }

        synchronized(lock) {

            val secondCheck: GraphData? = cachedGraph

            if (secondCheck != null) {
                return Pair(secondCheck.nodes, secondCheck.graph)
            }

            val baseDirectory =
                File(context.filesDir, "offline_maps")

            val file =
                File(baseDirectory, "route_graph.json")

            val loaded: GraphData = loadFromFile(file)

            cachedGraph = loaded
            cachedIndex = GridIndex(loaded.nodes)

            return Pair(
                loaded.nodes,
                loaded.graph
            )
        }
    }

    /**
     * Safely parses route_graph.json.
     *
     * Any invalid/missing graph produces an empty graph instead
     * of crashing the application.
     */
    private fun loadFromFile(file: File): GraphData {

        if (!file.exists() || !file.isFile) {
            return GraphData(
                nodes = emptyMap(),
                graph = emptyMap()
            )
        }

        return runCatching {

            val text: String = file.readText()

            if (text.isBlank()) {
                return@runCatching GraphData(
                    nodes = emptyMap(),
                    graph = emptyMap()
                )
            }

            val root = JSONObject(text)

            val nodes: MutableMap<String, RouteNode> =
                mutableMapOf()

            val nodesJson = root.optJSONArray("nodes")

            if (nodesJson != null) {

                for (i in 0 until nodesJson.length()) {

                    val nodeObject =
                        nodesJson.optJSONObject(i)
                            ?: continue

                    val id: String =
                        nodeObject.optString("id").trim()

                    if (id.isBlank()) {
                        continue
                    }

                    val lat: Double =
                        nodeObject.optDouble(
                            "lat",
                            Double.NaN
                        )

                    val lon: Double =
                        nodeObject.optDouble(
                            "lon",
                            Double.NaN
                        )

                    if (!lat.isFinite() || !lon.isFinite()) {
                        continue
                    }

                    if (lat !in -90.0..90.0) {
                        continue
                    }

                    if (lon !in -180.0..180.0) {
                        continue
                    }

                    nodes[id] =
                        RouteNode(
                            id = id,
                            point = GeoPoint(lat, lon)
                        )
                }
            }

            /*
             * Explicitly create the graph with mutable lists.
             * This avoids Kotlin's problematic type inference around
             * associateWith + mutable collections.
             */
            val mutableGraph:
                MutableMap<String, MutableList<RouteEdge>> =
                mutableMapOf()

            for (id: String in nodes.keys) {
                mutableGraph[id] = mutableListOf()
            }

            val edgesJson = root.optJSONArray("edges")

            if (edgesJson != null) {

                for (i in 0 until edgesJson.length()) {

                    val edgeObject =
                        edgesJson.optJSONObject(i)
                            ?: continue

                    val from: String =
                        edgeObject
                            .optString("from")
                            .trim()

                    val to: String =
                        edgeObject
                            .optString("to")
                            .trim()

                    if (from.isBlank() || to.isBlank()) {
                        continue
                    }

                    val fromNode: RouteNode =
                        nodes[from]
                            ?: continue

                    val toNode: RouteNode =
                        nodes[to]
                            ?: continue

                    val calculatedDistance: Double =
                        distance(
                            fromNode.point,
                            toNode.point
                        )

                    val metersValue: Double =
                        edgeObject.optDouble(
                            "meters",
                            calculatedDistance
                        )

                    val meters: Double =
                        if (
                            metersValue.isFinite() &&
                            metersValue >= 0.0
                        ) {
                            metersValue
                        } else {
                            calculatedDistance
                        }

                    val name: String? =
                        edgeObject
                            .optString("name")
                            .trim()
                            .takeIf { value ->
                                value.isNotBlank()
                            }

                    val edge =
                        RouteEdge(
                            to = to,
                            meters = meters,
                            name = name
                        )

                    mutableGraph
                        .getOrPut(from) {
                            mutableListOf()
                        }
                        .add(edge)

                    val bidirectional: Boolean =
                        edgeObject.optBoolean(
                            "bidirectional",
                            true
                        )

                    if (bidirectional) {

                        val reverseEdge =
                            RouteEdge(
                                to = from,
                                meters = meters,
                                name = name
                            )

                        mutableGraph
                            .getOrPut(to) {
                                mutableListOf()
                            }
                            .add(reverseEdge)
                    }
                }
            }

            val graph:
                Map<String, List<RouteEdge>> =
                mutableGraph.mapValues { entry ->
                    entry.value.toList()
                }

            GraphData(
                nodes = nodes.toMap(),
                graph = graph
            )

        }.getOrElse {
            GraphData(
                nodes = emptyMap(),
                graph = emptyMap()
            )
        }
    }

    /**
     * Clears the cached graph.
     *
     * Call this if route_graph.json is replaced while the
     * application is running.
     */
    fun clearCache() {

        synchronized(lock) {
            cachedGraph = null
            cachedIndex = null
        }
    }

    private fun nearestNode(
        nodes: Map<String, RouteNode>,
        point: GeoPoint
    ): RouteNode? {

        if (nodes.isEmpty()) {
            return null
        }

        val graph: GraphData? = cachedGraph
        val index: GridIndex? = cachedIndex

        if (
            graph != null &&
            index != null &&
            graph.nodes === nodes
        ) {
            return index.nearest(point)
        }

        return nodes.values.minByOrNull { node ->
            distance(node.point, point)
        }
    }

    /**
     * Performs A* routing.
     */
    fun route(
        nodes: Map<String, RouteNode>,
        graph: Map<String, List<RouteEdge>>,
        start: GeoPoint,
        goal: GeoPoint
    ): OfflineRoute? {

        if (nodes.isEmpty()) {
            return null
        }

        if (graph.isEmpty()) {
            return null
        }

        val startNode: RouteNode =
            nearestNode(nodes, start)
                ?: return null

        val goalNode: RouteNode =
            nearestNode(nodes, goal)
                ?: return null

        val startId: String = startNode.id
        val goalId: String = goalNode.id

        if (startId == goalId) {
            return OfflineRoute(
                points = listOf(startNode.point),
                distanceMeters = 0.0,
                instructions = emptyList()
            )
        }

        val gScore: MutableMap<String, Double> =
            mutableMapOf()

        val previous:
            MutableMap<String, Pair<String, RouteEdge>?> =
            mutableMapOf()

        val queue: PriorityQueue<State> =
            PriorityQueue()

        gScore[startId] = 0.0

        val initialHeuristic: Double =
            heuristic(
                startNode.point,
                goalNode.point
            )

        queue.add(
            State(
                id = startId,
                g = 0.0,
                f = initialHeuristic
            )
        )

        var found = false

        while (queue.isNotEmpty()) {

            val current: State =
                queue.poll()

            val knownScore: Double =
                gScore[current.id]
                    ?: Double.POSITIVE_INFINITY

            if (current.g > knownScore + 1e-6) {
                continue
            }

            if (current.id == goalId) {
                found = true
                break
            }

            val neighbors: List<RouteEdge> =
                graph[current.id].orEmpty()

            for (edge: RouteEdge in neighbors) {

                if (!nodes.containsKey(edge.to)) {
                    continue
                }

                val edgeMeters: Double =
                    if (
                        edge.meters.isFinite() &&
                        edge.meters >= 0.0
                    ) {
                        edge.meters
                    } else {
                        val currentNode =
                            nodes[current.id]

                        val nextNode =
                            nodes[edge.to]

                        if (
                            currentNode != null &&
                            nextNode != null
                        ) {
                            distance(
                                currentNode.point,
                                nextNode.point
                            )
                        } else {
                            continue
                        }
                    }

                val nextG: Double =
                    current.g + edgeMeters

                val oldScore: Double =
                    gScore[edge.to]
                        ?: Double.POSITIVE_INFINITY

                if (nextG + 1e-6 < oldScore) {

                    gScore[edge.to] = nextG

                    previous[edge.to] =
                        Pair(
                            current.id,
                            edge.copy(meters = edgeMeters)
                        )

                    val nextNode: RouteNode =
                        nodes[edge.to]
                            ?: continue

                    val h: Double =
                        heuristic(
                            nextNode.point,
                            goalNode.point
                        )

                    queue.add(
                        State(
                            id = edge.to,
                            g = nextG,
                            f = nextG + h
                        )
                    )
                }
            }
        }

        if (!found) {
            return null
        }

        val totalDistance: Double =
            gScore[goalId]
                ?: return null

        /*
         * Reconstruct path.
         */
        val ids: MutableList<String> =
            mutableListOf()

        val edges: MutableList<RouteEdge> =
            mutableListOf()

        var currentId: String? = goalId

        var safetyCounter = 0

        while (
            currentId != null &&
            safetyCounter < nodes.size + 1
        ) {

            safetyCounter++

            ids.add(currentId)

            val previousStep:
                Pair<String, RouteEdge>? =
                previous[currentId]

            if (previousStep == null) {
                currentId = null
            } else {
                currentId = previousStep.first
                edges.add(previousStep.second)
            }
        }

        if (safetyCounter >= nodes.size + 1) {
            return null
        }

        ids.reverse()
        edges.reverse()

        val points: MutableList<GeoPoint> =
            mutableListOf()

        for (id in ids) {

            val node: RouteNode =
                nodes[id]
                    ?: return null

            points.add(node.point)
        }

        if (points.isEmpty()) {
            return null
        }

        if (points.size != ids.size) {
            return null
        }

        return OfflineRoute(
            points = points,
            distanceMeters = totalDistance,
            instructions = buildInstructions(
                points,
                edges
            )
        )
    }

    /**
     * Converts graph edges into readable navigation instructions.
     */
    private fun buildInstructions(
        points: List<GeoPoint>,
        edges: List<RouteEdge>
    ): List<RouteInstruction> {

        if (points.size < 2 || edges.isEmpty()) {
            return emptyList()
        }

        val result:
            MutableList<RouteInstruction> =
            mutableListOf()

        var groupDistance: Double =
            edges.first().meters

        var groupName: String? =
            edges.first().name

        var groupAction: String =
            "Start"

        var previousBearing: Double =
            bearing(
                points[0],
                points[1]
            )

        for (i in 1 until edges.size) {

            if (i + 1 >= points.size) {
                break
            }

            val currentBearing: Double =
                bearing(
                    points[i],
                    points[i + 1]
                )

            val action: String =
                turnLabel(
                    previousBearing,
                    currentBearing
                )

            val currentName: String? =
                edges[i].name

            val nameChanged: Boolean =
                currentName != null &&
                    currentName != groupName

            if (
                action != "Continue straight" ||
                nameChanged
            ) {

                val instructionText: String =
                    buildInstructionText(
                        groupAction,
                        groupName
                    )

                result.add(
                    RouteInstruction(
                        text = instructionText,
                        distanceMeters = groupDistance
                    )
                )

                groupDistance =
                    edges[i].meters

                if (currentName != null) {
                    groupName = currentName
                }

                groupAction = action

            } else {

                groupDistance +=
                    edges[i].meters
            }

            previousBearing =
                currentBearing
        }

        result.add(
            RouteInstruction(
                text = "Arrive at destination",
                distanceMeters = groupDistance
            )
        )

        return result
    }

    private fun buildInstructionText(
        action: String,
        streetName: String?
    ): String {

        return if (
            streetName.isNullOrBlank()
        ) {
            action
        } else {
            "$action on $streetName"
        }
    }

    /**
     * Converts bearing difference into a human-readable turn.
     */
    private fun turnLabel(
        previous: Double,
        next: Double
    ): String {

        val delta: Double =
            (next - previous + 540.0) % 360.0 - 180.0

        val absoluteDelta: Double =
            abs(delta)

        return when {
            absoluteDelta < 20.0 ->
                "Continue straight"

            absoluteDelta < 55.0 ->
                if (delta > 0.0) {
                    "Bear right"
                } else {
                    "Bear left"
                }

            absoluteDelta < 135.0 ->
                if (delta > 0.0) {
                    "Turn right"
                } else {
                    "Turn left"
                }

            else ->
                "Make a U-turn"
        }
    }

    /**
     * Straight-line distance used as A* heuristic.
     */
    private fun heuristic(
        a: GeoPoint,
        b: GeoPoint
    ): Double {
        return distance(a, b)
    }

    /**
     * Distance in meters.
     */
    private fun distance(
        a: GeoPoint,
        b: GeoPoint
    ): Double {

        val result = FloatArray(1)

        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            result
        )

        return result[0].toDouble()
    }

    /**
     * Compass bearing from point A to point B.
     */
    private fun bearing(
        a: GeoPoint,
        b: GeoPoint
    ): Double {

        val lat1: Double =
            Math.toRadians(a.latitude)

        val lat2: Double =
            Math.toRadians(b.latitude)

        val deltaLongitude: Double =
            Math.toRadians(
                b.longitude - a.longitude
            )

        val y: Double =
            sin(deltaLongitude) * cos(lat2)

        val x: Double =
            cos(lat1) * sin(lat2) -
                sin(lat1) *
                cos(lat2) *
                cos(deltaLongitude)

        return (
            Math.toDegrees(
                atan2(y, x)
            ) + 360.0
        ) % 360.0
    }
}
