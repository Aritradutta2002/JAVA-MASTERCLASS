package dsa.datastructures.graphs.components;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ConnectedComponents {
    public static List<List<Integer>> components(Graph g) {
        int n = g.n;
        boolean[] vis = new boolean[n];
        List<List<Integer>> res = new ArrayList<>();
        for (int i = 0; i < n; i++)
            if (!vis[i]) {
                List<Integer> comp = new ArrayList<>();
                Deque<Integer> st = new ArrayDeque<>();
                st.push(i);
                while (!st.isEmpty()) {
                    int u = st.pop();
                    if (vis[u]) continue;
                    vis[u] = true;
                    comp.add(u);
                    for (int v : g.adj.get(u)) if (!vis[v]) st.push(v);
                }
                res.add(comp);
            }
        return res;
    }
}



