package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode #1129 - Shortest Path with Alternating Colors
 * 
 * You are given an integer n (number of nodes 0 to n-1), and two arrays redEdges and blueEdges.
 * redEdges[i] = [ai, bi] means a red directed edge from ai to bi.
 * blueEdges[i] = [ai, bi] means a blue directed edge from ai to bi.
 * 
 * Return an array answer of length n where answer[x] is the length of the shortest path from
 * node 0 to node x such that the edge colors alternate (red, blue, red, ...) or (blue, red, blue, ...).
 * If no such path exists, answer[x] should be -1.
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V + E)
 */
public class ShortestPathAlternatingColors {
    private static final int RED = 0;
    private static final int BLUE = 1;
    
    public int[] shortestAlternatingPaths(int n, int[][] redEdges, int[][] blueEdges) {
        // Build Adjacency Lists
        List<List<Integer>> adjRed = new ArrayList<>();
        List<List<Integer>> adjBlue = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adjRed.add(new ArrayList<>());
            adjBlue.add(new ArrayList<>());
        }
        
        for (int[] e : redEdges) adjRed.get(e[0]).add(e[1]);
        for (int[] e : blueEdges) adjBlue.get(e[0]).add(e[1]);
        
        // Initialize answer array
        int[] answer = new int[n];
        Arrays.fill(answer, -1);
        
        // visited[node][color] = true if we've visited 'node' arriving from an edge of 'color'.
        boolean[][] visited = new boolean[n][2];
        
        // Queue stores {node, color, steps}
        Queue<int[]> queue = new LinkedList<>();
        
        // We can start by "arriving" at 0 from either color (with 0 steps)
        queue.offer(new int[]{0, RED, 0});
        queue.offer(new int[]{0, BLUE, 0});
        visited[0][RED] = true;
        visited[0][BLUE] = true;
        answer[0] = 0;
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int u = current[0];
            int color = current[1];
            int steps = current[2];
            
            // We must now take the opposite color edge
            List<List<Integer>> adj = (color == RED) ? adjBlue : adjRed;
            int nextColor = (color == RED) ? BLUE : RED;
            
            for (int v : adj.get(u)) {
                if (!visited[v][nextColor]) {
                    visited[v][nextColor] = true;
                    queue.offer(new int[]{v, nextColor, steps + 1});
                    
                    // If this is the first time we're reaching 'v'
                    if (answer[v] == -1) {
                        answer[v] = steps + 1;
                    }
                }
            }
        }
        
        return answer;
    }
}

