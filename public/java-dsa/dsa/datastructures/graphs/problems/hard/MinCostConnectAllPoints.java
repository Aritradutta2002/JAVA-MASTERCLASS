package dsa.datastructures.graphs.problems.hard;
import java.util.PriorityQueue;

/**
 * LeetCode #1584 - Min Cost to Connect All Points
 * 
 * You are given an array points representing integer coordinates of some points on a 2D-plane,
 * where points[i] = [xi, yi].
 * 
 * The cost of connecting two points [xi, yi] and [xj, yj] is the manhattan distance between
 * them: |xi - xj| + |yi - yj|.
 * 
 * Return the minimum cost to make all points connected. All points are connected if there is
 * exactly one simple path between any two points.
 * 
 * Time Complexity: O(N^2 log N)
 * Space Complexity: O(N^2)
 */
public class MinCostConnectAllPoints {
    public int minCostConnectPoints(int[][] points) {
        int n = points.length;
        
        // Min-heap stores {cost, point_index}
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        boolean[] visited = new boolean[n];
        int totalCost = 0;
        int nodesVisited = 0;
        
        // Start at node 0
        pq.offer(new int[]{0, 0}); // Cost to connect node 0 to "itself" is 0
        
        while (!pq.isEmpty() && nodesVisited < n) {
            int[] current = pq.poll();
            int cost = current[0];
            int u = current[1];
            
            // If already in the MST, skip
            if (visited[u]) {
                continue;
            }
            
            // Add to MST
            visited[u] = true;
            totalCost += cost;
            nodesVisited++;
            
            // Add all edges from 'u' to unvisited neighbors
            for (int v = 0; v < n; v++) {
                if (!visited[v]) {
                    int dist = Math.abs(points[u][0] - points[v][0]) +
                              Math.abs(points[u][1] - points[v][1]);
                    pq.offer(new int[]{dist, v});
                }
            }
        }
        
        return totalCost;
    }
}

