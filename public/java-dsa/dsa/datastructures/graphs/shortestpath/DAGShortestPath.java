package dsa.datastructures.graphs.shortestpath;

import java.util.*;

public class DAGShortestPath {
    public static int[] shortestDAG(int n, List<List<int[]>> adj, int src) {
        int[] indeg = new int[n];
        for (int u = 0; u < n; u++) for (int[] e : adj.get(u)) indeg[e[0]]++;
        Deque<Integer> dq = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) dq.add(i);
        List<Integer> topo = new ArrayList<>();
        while (!dq.isEmpty()) {
            int u = dq.poll();
            topo.add(u);
            for (int[] e : adj.get(u)) if (--indeg[e[0]] == 0) dq.add(e[0]);
        }
        int INF = Integer.MAX_VALUE / 4;
        int[] dist = new int[n];
        Arrays.fill(dist, INF);
        dist[src] = 0;
        for (int u : topo)
            if (dist[u] < INF) for (int[] e : adj.get(u)) dist[e[0]] = Math.min(dist[e[0]], dist[u] + e[1]);
        return dist;
    }
}


