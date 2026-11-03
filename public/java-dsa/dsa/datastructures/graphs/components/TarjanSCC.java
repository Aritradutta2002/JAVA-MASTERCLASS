package dsa.datastructures.graphs.components;

import java.util.*;

public class TarjanSCC {
    static int time;

    public static List<List<Integer>> scc(int n, List<List<Integer>> adj) {
        int[] disc = new int[n], low = new int[n];
        Arrays.fill(disc, -1);
        boolean[] inStack = new boolean[n];
        Deque<Integer> st = new ArrayDeque<>();
        List<List<Integer>> comps = new ArrayList<>();
        time = 0;
        for (int i = 0; i < n; i++) if (disc[i] == -1) dfs(i, adj, disc, low, inStack, st, comps);
        return comps;
    }

    private static void dfs(int u, List<List<Integer>> adj, int[] disc, int[] low, boolean[] inStack, Deque<Integer> st, List<List<Integer>> comps) {
        disc[u] = low[u] = ++time;
        st.push(u);
        inStack[u] = true;
        for (int v : adj.get(u)) {
            if (disc[v] == -1) {
                dfs(v, adj, disc, low, inStack, st, comps);
                low[u] = Math.min(low[u], low[v]);
            } else if (inStack[v]) low[u] = Math.min(low[u], disc[v]);
        }
        if (low[u] == disc[u]) {
            List<Integer> comp = new ArrayList<>();
            while (true) {
                int x = st.pop();
                inStack[x] = false;
                comp.add(x);
                if (x == u) break;
            }
            comps.add(comp);
        }
    }
}


