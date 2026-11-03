package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode #1976 - Number of Ways to Arrive at Destination
 * 
 * You are in a city with n intersections (nodes 0 to n-1) and roads. roads[i] = [ui, vi, timei] means
 * an undirected edge between ui and vi takes timei minutes.
 * 
 * You want to go from node 0 to node n-1. Find the number of ways you can arrive at n-1 in the
 * shortest time possible.
 * Since the answer may be large, return it modulo 10^9 + 7.
 * 
 * Time Complexity: O(E log V)
 * Space Complexity: O(V + E)
 */
public class NumberOfWaysArriveDestination {
    public int countPaths(int n, int[][] roads) {
        long MOD = 1_000_000_007;
        
        // Build Adjacency List
        Map<Integer, List<long[]>> adj = new HashMap<>();
        for (int[] road : roads) {
            adj.putIfAbsent(road[0], new ArrayList<>());
            adj.putIfAbsent(road[1], new ArrayList<>());
            adj.get(road[0]).add(new long[]{road[1], road[2]});
            adj.get(road[1]).add(new long[]{road[0], road[2]});
        }
        
        // dist[i] = shortest time from 0 to i
        long[] dist = new long[n];
        Arrays.fill(dist, Long.MAX_VALUE);
        
        // ways[i] = number of shortest paths from 0 to i
        long[] ways = new long[n];
        
        // Min-heap stores {time, node}
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], a[1]));
        
        // Start at node 0
        dist[0] = 0;
        ways[0] = 1;
        pq.offer(new long[]{0, 0}); // {time, node}
        
        while (!pq.isEmpty()) {
            long[] current = pq.poll();
            long time = current[0];
            int u = (int)current[1];
            
            // If we've found a shorter path after adding this to the PQ, skip this one.
            if (time > dist[u]) {
                continue;
            }
            
            if (adj.containsKey(u)) {
                for (long[] edge : adj.get(u)) {
                    int v = (int)edge[0];
                    long w = edge[1];
                    long newDist = time + w;
                    
                    // Case 1: Found a new shorter path
                    if (newDist < dist[v]) {
                        dist[v] = newDist;
                        ways[v] = ways[u]; // Inherit ways from parent
                        pq.offer(new long[]{newDist, v});
                    }
                    // Case 2: Found another path of the same shortest length
                    else if (newDist == dist[v]) {
                        ways[v] = (ways[v] + ways[u]) % MOD;
                    }
                }
            }
        }
        
        return (int)ways[n - 1];
    }
}

