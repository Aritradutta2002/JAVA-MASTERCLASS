package dsa.datastructures.graphs.components;
import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

public class EulerianPathCircuit {
    // Hierholzer's algorithm for Eulerian path/circuit in undirected graph
    // adj edges as (to, edgeId), each edge appears twice (u<->v) sharing edgeId
    public static List<Integer> eulerianPathUndirected(int n, List<List<int[]>> adj, int start) {
        List<Integer> path = new ArrayList<>();
        int m = 0;
        for (List<int[]> lst : adj) for (int[] e : lst) m = Math.max(m, e[1] + 1);
        boolean[] used = new boolean[m];
        Deque<Integer> st = new ArrayDeque<>();
        st.push(start);
        int[] it = new int[n];
        while (!st.isEmpty()) {
            int u = st.peek();
            while (it[u] < adj.get(u).size() && used[adj.get(u).get(it[u])[1]]) it[u]++;
            if (it[u] == adj.get(u).size()) {
                path.add(u);
                st.pop();
            } else {
                int[] e = adj.get(u).get(it[u]++);
                if (!used[e[1]]) {
                    used[e[1]] = true;
                    st.push(e[0]);
                }
            }
        }
        Collections.reverse(path);
        return path;
    }
}



