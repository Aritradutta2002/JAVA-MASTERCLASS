package dsa.datastructures.graphs.problems.hard;
import java.util.Arrays;

public class CheapestFlightsWithinKStops {
    public static int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
        int INF = 1_000_000_000;
        int[] dist = new int[n];
        Arrays.fill(dist, INF);
        dist[src] = 0;
        for (int i = 0; i <= k; i++) {
            int[] nd = dist.clone();
            for (int[] f : flights) {
                int u = f[0], v = f[1], w = f[2];
                if (dist[u] != INF && dist[u] + w < nd[v]) nd[v] = dist[u] + w;
            }
            dist = nd;
        }
        return dist[dst] >= INF ? -1 : dist[dst];
    }
}

