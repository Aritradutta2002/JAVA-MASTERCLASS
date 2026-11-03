package dsa.datastructures.graphs.shortestpath;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class UnweightedShortestPath {
    public static int[] shortest(Graph g, int src) {
        int n = g.n;
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Queue<Integer> q = new ArrayDeque<>();
        dist[src] = 0;
        q.add(src);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : g.adj.get(u))
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = dist[u] + 1;
                    q.add(v);
                }
        }
        return dist;
    }
}



