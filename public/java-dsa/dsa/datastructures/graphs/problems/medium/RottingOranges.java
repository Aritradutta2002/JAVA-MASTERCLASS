package dsa.datastructures.graphs.problems.medium;
import java.util.ArrayDeque;
import java.util.Queue;

public class RottingOranges {
    public static int orangesRotting(int[][] grid) {
        int m = grid.length, n = grid[0].length;
        Queue<int[]> q = new ArrayDeque<>();
        int fresh = 0;
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 2) q.add(new int[]{i, j});
                if (grid[i][j] == 1) fresh++;
            }
        int mins = 0;
        int[][] dir = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty() && fresh > 0) {
            int sz = q.size();
            while (sz-- > 0) {
                int[] p = q.poll();
                for (int[] d : dir) {
                    int x = p[0] + d[0], y = p[1] + d[1];
                    if (x >= 0 && y >= 0 && x < m && y < n && grid[x][y] == 1) {
                        grid[x][y] = 2;
                        fresh--;
                        q.add(new int[]{x, y});
                    }
                }
            }
            mins++;
        }
        return fresh == 0 ? mins : -1;
    }
}

