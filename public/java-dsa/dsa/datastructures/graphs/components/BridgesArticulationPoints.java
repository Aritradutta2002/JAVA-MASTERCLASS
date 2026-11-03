package dsa.datastructures.graphs.components;

import java.util.*;

public class BridgesArticulationPoints {
    static int time;

    public static List<int[]> bridges(int n, List<List<Integer>> adj) {
        time = 0;
        int[] disc = new int[n], low = new int[n];
        Arrays.fill(disc, -1);
        List<int[]> res = new ArrayList<>();
        for (int i = 0; i < n; i++) if (disc[i] == -1) dfsBridge(i, -1, adj, disc, low, res);
        return res;
    }

    private static void dfsBridge(int u, int p, List<List<Integer>> adj, int[] disc, int[] low, List<int[]> res) {
        disc[u] = low[u] = ++time;
        for (int v : adj.get(u)) {
            if (v == p) continue;
            if (disc[v] == -1) {
                dfsBridge(v, u, adj, disc, low, res);
                low[u] = Math.min(low[u], low[v]);
                if (low[v] > disc[u]) res.add(new int[]{u, v});
            } else low[u] = Math.min(low[u], disc[v]);
        }
    }

    public static Set<Integer> articulationPoints(int n, List<List<Integer>> adj) {
        time = 0;
        int[] disc = new int[n], low = new int[n], parent = new int[n];
        Arrays.fill(disc, -1);
        Arrays.fill(parent, -1);
        boolean[] ap = new boolean[n];
        for (int i = 0; i < n; i++) if (disc[i] == -1) dfsAP(i, adj, disc, low, parent, ap);
        Set<Integer> res = new HashSet<>();
        for (int i = 0; i < n; i++) if (ap[i]) res.add(i);
        return res;
    }

    private static void dfsAP(int u, List<List<Integer>> adj, int[] disc, int[] low, int[] parent, boolean[] ap) {
        disc[u] = low[u] = ++time;
        int children = 0;
        for (int v : adj.get(u)) {
            if (disc[v] == -1) {
                children++;
                parent[v] = u;
                dfsAP(v, adj, disc, low, parent, ap);
                low[u] = Math.min(low[u], low[v]);
                if (parent[u] == -1 && children > 1) ap[u] = true;
                if (parent[u] != -1 && low[v] >= disc[u]) ap[u] = true;
            } else if (v != parent[u]) low[u] = Math.min(low[u], disc[v]);
        }
    }
}


