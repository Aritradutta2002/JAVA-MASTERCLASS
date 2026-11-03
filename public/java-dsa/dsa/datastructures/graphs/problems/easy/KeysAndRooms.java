package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 841: Keys and Rooms
 * 
 * Problem: There are n rooms labeled from 0 to n - 1 and all the rooms are locked except for room 0.
 * Your goal is to visit all the rooms. However, you cannot enter a locked room without having its key.
 * When you visit a room, you may find a set of distinct keys in it. Each key has a number on it, 
 * denoting which room it unlocks, and you can take all of them with you to unlock the other rooms.
 * 
 * Time Complexity: O(n + k) where k is total number of keys
 * Space Complexity: O(n)
 */
public class KeysAndRooms {
    
    /**
     * DFS solution
     */
    public boolean canVisitAllRooms(List<List<Integer>> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return true;
        }
        
        boolean[] visited = new boolean[rooms.size()];
        dfs(rooms, 0, visited);
        
        // Check if all rooms are visited
        for (boolean visit : visited) {
            if (!visit) return false;
        }
        
        return true;
    }
    
    private void dfs(List<List<Integer>> rooms, int room, boolean[] visited) {
        visited[room] = true;
        
        // Visit all rooms whose keys we can find in current room
        for (int key : rooms.get(room)) {
            if (!visited[key]) {
                dfs(rooms, key, visited);
            }
        }
    }
    
    /**
     * BFS solution
     */
    public boolean canVisitAllRoomsBFS(List<List<Integer>> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return true;
        }
        
        boolean[] visited = new boolean[rooms.size()];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.offer(0);
        visited[0] = true;
        
        while (!queue.isEmpty()) {
            int currentRoom = queue.poll();
            
            // Get all keys from current room
            for (int key : rooms.get(currentRoom)) {
                if (!visited[key]) {
                    visited[key] = true;
                    queue.offer(key);
                }
            }
        }
        
        // Check if all rooms are visited
        for (boolean visit : visited) {
            if (!visit) return false;
        }
        
        return true;
    }
    
    // Test method
    public static void main(String[] args) {
        KeysAndRooms solution = new KeysAndRooms();
        
        // Test case 1: [[1],[2],[3],[]]
        List<List<Integer>> rooms1 = Arrays.asList(
            Arrays.asList(1),
            Arrays.asList(2),
            Arrays.asList(3),
            Arrays.asList()
        );
        
        System.out.println("Can visit all rooms: " + solution.canVisitAllRooms(rooms1)); // true
        
        // Test case 2: [[1,3],[3,0,1],[2],[0]]
        List<List<Integer>> rooms2 = Arrays.asList(
            Arrays.asList(1, 3),
            Arrays.asList(3, 0, 1),
            Arrays.asList(2),
            Arrays.asList(0)
        );
        
        System.out.println("Can visit all rooms: " + solution.canVisitAllRoomsBFS(rooms2)); // false
    }
}

