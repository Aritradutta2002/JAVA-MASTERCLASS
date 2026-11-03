package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 733: Flood Fill
 * 
 * Problem: An image is represented by an m x n integer grid image where image[i][j] 
 * represents the pixel value of the image. You are also given three integers sr, sc, 
 * and color. You should perform a flood fill on the image starting from the pixel image[sr][sc].
 * 
 * Time Complexity: O(m * n)
 * Space Complexity: O(m * n) for recursion stack in worst case
 */
public class FloodFill {
    
    /**
     * DFS solution
     */
    public int[][] floodFill(int[][] image, int sr, int sc, int color) {
        if (image == null || image.length == 0 || image[sr][sc] == color) {
            return image;
        }
        
        int originalColor = image[sr][sc];
        dfs(image, sr, sc, originalColor, color);
        return image;
    }
    
    private void dfs(int[][] image, int row, int col, int originalColor, int newColor) {
        // Check bounds and color
        if (row < 0 || row >= image.length || 
            col < 0 || col >= image[0].length || 
            image[row][col] != originalColor) {
            return;
        }
        
        // Fill current pixel
        image[row][col] = newColor;
        
        // Fill 4-directionally connected pixels
        dfs(image, row + 1, col, originalColor, newColor); // Down
        dfs(image, row - 1, col, originalColor, newColor); // Up
        dfs(image, row, col + 1, originalColor, newColor); // Right
        dfs(image, row, col - 1, originalColor, newColor); // Left
    }
    
    /**
     * BFS solution
     */
    public int[][] floodFillBFS(int[][] image, int sr, int sc, int color) {
        if (image == null || image.length == 0 || image[sr][sc] == color) {
            return image;
        }
        
        int originalColor = image[sr][sc];
        int rows = image.length;
        int cols = image[0].length;
        
        java.util.Queue<int[]> queue = new java.util.LinkedList<>();
        queue.offer(new int[]{sr, sc});
        image[sr][sc] = color;
        
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            
            for (int[] dir : directions) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < rows && 
                    newCol >= 0 && newCol < cols && 
                    image[newRow][newCol] == originalColor) {
                    
                    image[newRow][newCol] = color;
                    queue.offer(new int[]{newRow, newCol});
                }
            }
        }
        
        return image;
    }
    
    // Test method
    public static void main(String[] args) {
        FloodFill solution = new FloodFill();
        
        int[][] image = {
            {1, 1, 1},
            {1, 1, 0},
            {1, 0, 1}
        };
        
        System.out.println("Original image:");
        printImage(image);
        
        int[][] result = solution.floodFill(image, 1, 1, 2);
        
        System.out.println("After flood fill:");
        printImage(result);
    }
    
    private static void printImage(int[][] image) {
        for (int[] row : image) {
            for (int pixel : row) {
                System.out.print(pixel + " ");
            }
            System.out.println();
        }
        System.out.println();
    }
}

