package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Comparator
import kotlin.concurrent.thread

val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathSequential(start: Node, destination: Node): Int {
    start.distance = 0
    val q = PriorityQueue<Node>(NODE_DISTANCE_COMPARATOR)
    q.add(start)
    while (q.isNotEmpty()) {
        val cur = q.poll()
        for (e in cur.outgoingEdges) {
            if (e.to.distance > cur.distance + e.weight) {
                e.to.distance = cur.distance + e.weight
                q.remove(e.to) // inefficient, but used for tests only
                q.add(e.to)
            }
        }
    }
    return destination.distance
}

/**
 * The work should be finished when all threads start to wait. It means that new elements can't appear in the queue.
 * For more efficiency let's try to use synchronized blocks only when working with a queue. Other operations will
 * be done with atomic objects.
 */
fun shortestPathParallel(start: Node, destination: Node): Int {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.atomicDistance = AtomicInteger(0)
    // Create a priority (by distance) queue and add the start node into it
    val q = PriorityQueue<Node.Distance2Node>(workers)
    q.add(Node.Distance2Node(0, start))

    val onFinish = Phaser(workers + 1)
    val waitingWorkers = AtomicInteger(0)
    repeat(workers) {
        thread {
            var iAmWaiting = false
            while (true) {
                var dist2Node: Node.Distance2Node?
                synchronized(q) {
                    while (true) {
                        dist2Node = q.poll()

                        // Don't need to process the node if the distance we store is different than node.atomicDistance.
                        // It means that this node was already processed before.
                        if (dist2Node == null || dist2Node!!.distance == dist2Node!!.node.atomicDistance.get()) {
                            break
                        }
                    }
                }
                val cur = dist2Node?.node
                if (cur == null) {
                    if (waitingWorkers.get() == workers)
                        break
                    else if (!iAmWaiting) {
                        iAmWaiting = true
                        waitingWorkers.incrementAndGet()
                    }
                    continue
                } else if (iAmWaiting) {
                    iAmWaiting = false
                    waitingWorkers.decrementAndGet()
                }

                for (e in cur.outgoingEdges) {
                    do {
                        val before = e.to.atomicDistance.get()
                        val after = cur.atomicDistance.get() + e.weight

                        // Trying to modify distance of the node. If it was successful modifying the queue.
                        if (after < before && e.to.atomicDistance.compareAndSet(before, after)) {
                            synchronized(q) {
                                q.add(Node.Distance2Node(after, e.to))
                            }
                            break
                        }
                    } while (after < before)
                }
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
    return destination.atomicDistance.get()
}