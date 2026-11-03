package dsa.datastructures.graphs.components;

import java.util.*;

public class KosarajuSCC {
    public static List<List<Integer>> scc(int n, List<List<Integer>> adj) {
        boolean[] vis = new boolean[n];
        Deque<Integer> order = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (!vis[i]) dfs1(i, adj, vis, order);
        List<List<Integer>> radj = new ArrayList<>();
        for (int i = 0; i < n; i++) radj.add(new ArrayList<>());
        for (int u = 0; u < n; u++) for (int v : adj.get(u)) radj.get(v).add(u);
        Arrays.fill(vis, false);
        List<List<Integer>> comps = new ArrayList<>();
        while (!order.isEmpty()) {
            int u = order.pop();
            if (!vis[u]) {
                List<Integer> comp = new ArrayList<>();
                dfs2(u, radj, vis, comp);
                comps.add(comp);
            }
        }
        return comps;
    }

    private static void dfs1(int u, List<List<Integer>> adj, boolean[] vis, Deque<Integer> order) {
        vis[u] = true;
        for (int v : adj.get(u)) if (!vis[v]) dfs1(v, adj, vis, order);
        order.push(u);
    }

    private static void dfs2(int u, List<List<Integer>> adj, boolean[] vis, List<Integer> comp) {
        vis[u] = true;
        comp.add(u);
        for (int v : adj.get(u)) if (!vis[v]) dfs2(v, adj, vis, comp);
    }
}


