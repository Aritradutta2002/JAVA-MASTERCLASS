package dsa.algorithms.recursion.problems.hard;
import java.util.*;
@SuppressWarnings("unused")

public class Rat_In_A_Maze {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int[][] maze1 = {
                { 1, 0, 0, 0 },
                { 1, 1, 0, 1 },
                { 0, 1, 0, 0 },
                { 1, 1, 1, 1 }
        };
        int[][] maze2 = {
                { 1, 0, 0, 0 },
                { 1, 1, 0, 1 },
                { 1, 1, 0, 0 },
                { 0, 1, 1, 1 }
        };

        System.out.println(findPath(maze1));
        System.out.println(findPath(maze2));
    }

    public static int N = 0;

    public static List<String> findPath(int[][] grid) {
        N = grid.length;
        List<String> result = new ArrayList<>();
        solveMaze(grid, "", 0, 0, result);
        return result;
    }

    public static void solveMaze(int[][] grid, String dir, int x, int y, List<String> result) {
        if (x == N - 1 && y == N - 1 && grid[x][y] == 1) {
            result.add(dir);
            return;
        }

        if (isSafe(grid, x, y)) {

            grid[x][y] = 0;

            solveMaze(grid, dir + "D", x + 1, y, result); // for moving down

            solveMaze(grid, dir + "L", x, y - 1, result); // for moving left

            solveMaze(grid, dir + "R", x, y + 1, result); // for moving right

            solveMaze(grid, dir + "U", x - 1, y, result); // for moving up

            grid[x][y] = 1; // Backtrack if nothing work then.

        }
    }

    public static boolean isSafe(int[][] grid, int x, int y) {
        return x >= 0 && x < grid.length && y >= 0 && y < grid[0].length && grid[x][y] == 1;
    }
}
