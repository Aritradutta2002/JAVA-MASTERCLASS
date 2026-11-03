package dsa.datastructures.graphs.shortestpath;
import com.dsa.datastructures.graphs.utils.Graph;
import java.util.*;

/**
 * A* Search Algorithm - Heuristic Shortest Path
 * 
 * A* is an informed search algorithm that uses a heuristic function to find
 * the shortest path more efficiently than Dijkstra by prioritizing promising paths.
 * 
 * Formula: f(n) = g(n) + h(n)
 * - g(n) = actual cost from start to node n
 * - h(n) = estimated cost from node n to goal (heuristic)
 * - f(n) = estimated total cost
 * 
 * Time Complexity: O((V + E) log V) with good heuristic, O(b^d) worst case
 * Space Complexity: O(V)
 * 
 * Key Properties:
 * - Optimal if heuristic is admissible (never overestimates)
 * - Complete (always finds solution if exists)
 * - Faster than Dijkstra when good heuristic available
 * - Reduces to Dijkstra when h(n) = 0
 * 
 * Common Heuristics:
 * - Manhattan Distance (grid with 4 directions)
 * - Euclidean Distance (grid with 8 directions)
 * - Chebyshev Distance
 * - Octile Distance
 * 
 * Common MAANG Applications:
 * - GPS navigation with obstacles
 * - Game AI pathfinding
 * - Robot motion planning
 * - Puzzle solving (8-puzzle, Rubik's cube)
 * - Network routing with QoS
 */
public class AStarSearch {
    
    /**
     * APPROACH 1: A* Search with Grid (Most Common)
     * 
     * For 2D grid with obstacles, uses Manhattan distance heuristic.
     * Classic implementation for maze/pathfinding problems.
     * 
     * Time Complexity: O((V + E) log V) average
     * Space Complexity: O(V)
     * 
     * @param grid 2D grid (0=walkable, 1=obstacle)
     * @param start Start position [row, col]
     * @param goal Goal position [row, col]
     * @return List of positions representing shortest path
     */
    public static List<int[]> aStarGrid(int[][] grid, int[] start, int[] goal) {
        int rows = grid.length;
        int cols = grid[0].length;
        
        // Priority queue: [row, col, f-score]
        PriorityQueue<Node> openSet = new PriorityQueue<>(
            Comparator.comparingInt(n -> n.fScore)
        );
        
        int[][] gScore = new int[rows][cols];
        boolean[][] visited = new boolean[rows][cols];
        Node[][] parent = new Node[rows][cols];
        
        // Initialize g-scores to infinity
        for (int[] row : gScore) {
            Arrays.fill(row, Integer.MAX_VALUE);
        }
        
        // Start node
        gScore[start[0]][start[1]] = 0;
        int h = manhattanDistance(start, goal);
        openSet.offer(new Node(start[0], start[1], 0, h));
        
        // Directions: up, right, down, left
        int[][] dirs = {{-1,0}, {0,1}, {1,0}, {0,-1}};
        
        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            
            // Goal reached
            if (current.row == goal[0] && current.col == goal[1]) {
                return reconstructPath(parent, current);
            }
            
            // Skip if already visited
            if (visited[current.row][current.col]) {
                continue;
            }
            visited[current.row][current.col] = true;
            
            // Explore neighbors
            for (int[] dir : dirs) {
                int newRow = current.row + dir[0];
                int newCol = current.col + dir[1];
                
                // Check boundaries and obstacles
                if (newRow < 0 || newRow >= rows || newCol < 0 || newCol >= cols ||
                    grid[newRow][newCol] == 1 || visited[newRow][newCol]) {
                    continue;
                }
                
                int tentativeG = gScore[current.row][current.col] + 1;
                
                if (tentativeG < gScore[newRow][newCol]) {
                    gScore[newRow][newCol] = tentativeG;
                    int hScore = manhattanDistance(new int[]{newRow, newCol}, goal);
                    int fScore = tentativeG + hScore;
                    
                    parent[newRow][newCol] = current;
                    openSet.offer(new Node(newRow, newCol, tentativeG, hScore));
                }
            }
        }
        
        return new ArrayList<>(); // No path found
    }
    
    /**
     * APPROACH 2: A* with 8-Directional Movement (Diagonal)
     * 
     * Allows diagonal movement, uses Euclidean or Octile distance.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V)
     * 
     * @param grid 2D grid
     * @param start Start position
     * @param goal Goal position
     * @return Shortest path with diagonal moves
     */
    public static List<int[]> aStarDiagonal(int[][] grid, int[] start, int[] goal) {
        int rows = grid.length;
        int cols = grid[0].length;
        
        PriorityQueue<Node> openSet = new PriorityQueue<>(
            Comparator.comparingInt(n -> n.fScore)
        );
        
        double[][] gScore = new double[rows][cols];
        boolean[][] visited = new boolean[rows][cols];
        Node[][] parent = new Node[rows][cols];
        
        for (double[] row : gScore) {
            Arrays.fill(row, Double.MAX_VALUE);
        }
        
        gScore[start[0]][start[1]] = 0;
        int h = octileDistance(start, goal);
        openSet.offer(new Node(start[0], start[1], 0, h));
        
        // 8 directions including diagonals
        int[][] dirs = {{-1,0}, {-1,1}, {0,1}, {1,1}, {1,0}, {1,-1}, {0,-1}, {-1,-1}};
        
        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            
            if (current.row == goal[0] && current.col == goal[1]) {
                return reconstructPath(parent, current);
            }
            
            if (visited[current.row][current.col]) {
                continue;
            }
            visited[current.row][current.col] = true;
            
            for (int i = 0; i < dirs.length; i++) {
                int[] dir = dirs[i];
                int newRow = current.row + dir[0];
                int newCol = current.col + dir[1];
                
                if (newRow < 0 || newRow >= rows || newCol < 0 || newCol >= cols ||
                    grid[newRow][newCol] == 1 || visited[newRow][newCol]) {
                    continue;
                }
                
                // Diagonal moves cost √2 ≈ 1.414, straight moves cost 1
                double moveCost = (i % 2 == 1) ? Math.sqrt(2) : 1.0;
                double tentativeG = gScore[current.row][current.col] + moveCost;
                
                if (tentativeG < gScore[newRow][newCol]) {
                    gScore[newRow][newCol] = tentativeG;
                    int hScore = octileDistance(new int[]{newRow, newCol}, goal);
                    int fScore = (int)(tentativeG + hScore);
                    
                    parent[newRow][newCol] = current;
                    openSet.offer(new Node(newRow, newCol, (int)tentativeG, hScore));
                }
            }
        }
        
        return new ArrayList<>();
    }
    
    /**
     * APPROACH 3: A* with Custom Heuristic
     * 
     * Generic A* that works with any heuristic function.
     * Useful for non-grid problems.
     * 
     * @param graph Input graph
     * @param start Start vertex
     * @param goal Goal vertex
     * @param heuristic Heuristic function
     * @return Shortest path
     */
    public static List<Integer> aStarGeneric(Graph graph, int start, int goal, 
                                            HeuristicFunction heuristic) {
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>(
            Comparator.comparingInt(n -> n.fScore)
        );
        
        int[] gScore = new int[graph.vertexCount];
        boolean[] visited = new boolean[graph.vertexCount];
        int[] parent = new int[graph.vertexCount];
        
        Arrays.fill(gScore, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        
        gScore[start] = 0;
        openSet.offer(new AStarNode(start, 0, heuristic.estimate(start, goal)));
        
        while (!openSet.isEmpty()) {
            AStarNode current = openSet.poll();
            
            if (current.vertex == goal) {
                return reconstructPathGeneric(parent, start, goal);
            }
            
            if (visited[current.vertex]) {
                continue;
            }
            visited[current.vertex] = true;
            
            for (int[] edge : graph.adjW.get(current.vertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (visited[neighbor]) {
                    continue;
                }
                
                int tentativeG = gScore[current.vertex] + weight;
                
                if (tentativeG < gScore[neighbor]) {
                    gScore[neighbor] = tentativeG;
                    parent[neighbor] = current.vertex;
                    int h = heuristic.estimate(neighbor, goal);
                    openSet.offer(new AStarNode(neighbor, tentativeG, h));
                }
            }
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Manhattan Distance (L1 norm)
     * Best for 4-directional grids
     */
    private static int manhattanDistance(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }
    
    /**
     * Euclidean Distance (L2 norm)
     * Best for any-angle movement
     */
    private static int euclideanDistance(int[] a, int[] b) {
        int dx = a[0] - b[0];
        int dy = a[1] - b[1];
        return (int) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Octile Distance (Diagonal)
     * Best for 8-directional grids
     */
    private static int octileDistance(int[] a, int[] b) {
        int dx = Math.abs(a[0] - b[0]);
        int dy = Math.abs(a[1] - b[1]);
        return Math.max(dx, dy) + (int)((Math.sqrt(2) - 1) * Math.min(dx, dy));
    }
    
    /**
     * Reconstruct path from parent matrix
     */
    private static List<int[]> reconstructPath(Node[][] parent, Node goal) {
        List<int[]> path = new ArrayList<>();
        Node current = goal;
        
        while (current != null) {
            path.add(new int[]{current.row, current.col});
            current = parent[current.row][current.col];
        }
        
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Reconstruct path for generic graph
     */
    private static List<Integer> reconstructPathGeneric(int[] parent, int start, int goal) {
        List<Integer> path = new ArrayList<>();
        int current = goal;
        
        while (current != -1) {
            path.add(current);
            current = parent[current];
        }
        
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Node class for grid-based A*
     */
    static class Node {
        int row, col;
        int gScore; // Cost from start
        int hScore; // Estimated cost to goal
        int fScore; // Total estimated cost
        
        Node(int row, int col, int gScore, int hScore) {
            this.row = row;
            this.col = col;
            this.gScore = gScore;
            this.hScore = hScore;
            this.fScore = gScore + hScore;
        }
    }
    
    /**
     * Node class for generic A*
     */
    static class AStarNode {
        int vertex;
        int gScore;
        int fScore;
        
        AStarNode(int vertex, int gScore, int hScore) {
            this.vertex = vertex;
            this.gScore = gScore;
            this.fScore = gScore + hScore;
        }
    }
    
    /**
     * Interface for custom heuristic functions
     */
    public interface HeuristicFunction {
        int estimate(int from, int to);
    }
    
    /**
     * Demonstration
     */
    public static void main(String[] args) {
        System.out.println("=== A* Search Algorithm ===\n");
        
        // Test 1: Grid with obstacles
        int[][] grid = {
            {0, 0, 0, 0, 0},
            {0, 1, 1, 1, 0},
            {0, 0, 0, 1, 0},
            {0, 1, 0, 0, 0},
            {0, 0, 0, 0, 0}
        };
        
        int[] start = {0, 0};
        int[] goal = {4, 4};
        
        System.out.println("Grid (0=walkable, 1=obstacle):");
        for (int[] row : grid) {
            System.out.println(Arrays.toString(row));
        }
        
        List<int[]> path = aStarGrid(grid, start, goal);
        System.out.println("\nA* Path (4-directional):");
        for (int[] pos : path) {
            System.out.println("(" + pos[0] + ", " + pos[1] + ")");
        }
        
        // Test 2: Diagonal movement
        List<int[]> pathDiag = aStarDiagonal(grid, start, goal);
        System.out.println("\nA* Path (8-directional with diagonals):");
        for (int[] pos : pathDiag) {
            System.out.println("(" + pos[0] + ", " + pos[1] + ")");
        }
        
        System.out.println("\n4-dir path length: " + path.size());
        System.out.println("8-dir path length: " + pathDiag.size());
    }
}



