package dijkstra

import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList

class Node {
    private val _outgoingEdges = arrayListOf<Edge>()
    val outgoingEdges: List<Edge> = _outgoingEdges

    var distance = Integer.MAX_VALUE

    /**
     * Added new field for parallel algo, because I didn't want to change anything in the primitive one.
     * I decided that Atomic is expected to be more efficient than synchronization.
     */
    var atomicDistance = AtomicInteger(Integer.MAX_VALUE)

    /**
     * A special data class to store distances in a queue. Now we don't need to remove nodes from the queue.
     * Just add them all the time. After poll operation we will check if the node was modified and if it
     * was simply skip it.
     */
    data class Distance2Node(val distance: Int, val node: Node) : Comparable<Distance2Node> {
        override fun compareTo(other: Distance2Node): Int = distance.compareTo(other.distance)
    }

    fun addEdge(edge: Edge) {
        _outgoingEdges.add(edge)
    }
}

data class Edge(val to: Node, val weight: Int)

fun randomConnectedGraph(nodes: Int, edges: Int, maxWeight: Int = 100): List<Node> {
    require(edges >= nodes - 1)
    val r = Random()
    val nodesList = List(nodes) { Node() }
    // generate a random connected graph with `nodes-1` edges
    val s = ArrayList(nodesList)
    var cur = s.removeAt(r.nextInt(s.size))
    val visited = mutableSetOf<Node>(cur)
    while (s.isNotEmpty()) {
        val neighbor = s.removeAt(r.nextInt(s.size))
        if (visited.add(neighbor)) {
            cur.addEdge(Edge(neighbor, r.nextInt(maxWeight)))
        }
        cur = neighbor
    }
    // add `edges - nodes + 1` random edges
    repeat(edges - nodes + 1) {
        while (true) {
            val first = nodesList[r.nextInt(nodes)]
            val second = nodesList[r.nextInt(nodes)]
            if (first == second) continue
            if (first.outgoingEdges.any { e -> e.to == second }) continue
            val weight = r.nextInt(maxWeight)
            first.addEdge(Edge(second, weight))
            second.addEdge(Edge(first, weight))
            break
        }
    }
    return nodesList
}

fun clearNodes(nodes: List<Node>) {
    nodes.forEach {
        it.distance = Int.MAX_VALUE
        it.atomicDistance = AtomicInteger(Int.MAX_VALUE)
    }
}