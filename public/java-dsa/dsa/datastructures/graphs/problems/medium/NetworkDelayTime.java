package dsa.datastructures.graphs.problems.medium;
import java.util.*;

public class NetworkDelayTime {
    public static int networkDelayTime(int[][] times, int n, int k) {
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int[] t : times) adj.get(t[0] - 1).add(new int[]{t[1] - 1, t[2]});
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE / 4);
        dist[k - 1] = 0;
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.add(new int[]{k - 1, 0});
        boolean[] vis = new boolean[n];
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            int u = cur[0];
            if (vis[u]) continue;
            vis[u] = true;
            for (int[] e : adj.get(u)) {
                if (dist[u] + e[1] < dist[e[0]]) {
                    dist[e[0]] = dist[u] + e[1];
                    pq.add(new int[]{e[0], dist[e[0]]});
                }
            }
        }
        int ans = 0;
        for (int d : dist) {
            if (d >= Integer.MAX_VALUE / 4) return -1;
            ans = Math.max(ans, d);
        }
        return ans;
    }
}

