package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 2359: Find the Closest Node to Given Two Nodes
 * 
 * Problem: You are given a directed graph of n nodes numbered from 0 to n - 1, where each node has at most
 * one outgoing edge. The graph is represented with a given 0-indexed array edges of size n, indicating that there is a
 * directed edge from node i to node edges[i]. If there is no outgoing edge from i, then edges[i] == -1.
 * You are also given two integers node1 and node2.
 * Return the index of the node that can be reached from both node1 and node2, such that the maximum
 * between the distance from node1 to that node, and from node2 to that node is minimized. If there are
 * multiple answers, return the node with the smallest index, and if no possible answer exists, return -1.
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class FindClosestMeetingNode {
    
    /**
     * Main solution using BFS from both nodes
     */
    public int closestMeetingNode(int[] edges, int node1, int node2) {
        int n = edges.length;
        
        // Calculate distances from both starting nodes
        int[] dist1 = getDistances(edges, node1);
        int[] dist2 = getDistances(edges, node2);
        
        int minMaxDist = Integer.MAX_VALUE;
        int result = -1;
        
        // Find the node with minimum maximum distance
        for (int i = 0; i < n; i++) {
            if (dist1[i] != -1 && dist2[i] != -1) {
                int maxDist = Math.max(dist1[i], dist2[i]);
                if (maxDist < minMaxDist || (maxDist == minMaxDist && i < result)) {
                    minMaxDist = maxDist;
                    result = i;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Calculate distances from a starting node using BFS
     */
    private int[] getDistances(int[] edges, int start) {
        int n = edges.length;
        int[] distances = new int[n];
        Arrays.fill(distances, -1);
        
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(start);
        distances[start] = 0;
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            int next = edges[current];
            
            if (next != -1 && distances[next] == -1) {
                distances[next] = distances[current] + 1;
                queue.offer(next);
            }
        }
        
        return distances;
    }
    
    /**
     * DFS solution
     */
    public int closestMeetingNodeDFS(int[] edges, int node1, int node2) {
        int n = edges.length;
        
        int[] dist1 = new int[n];
        int[] dist2 = new int[n];
        Arrays.fill(dist1, -1);
        Arrays.fill(dist2, -1);
        
        // DFS from both nodes
        dfs(edges, node1, dist1, 0);
        dfs(edges, node2, dist2, 0);
        
        int minMaxDist = Integer.MAX_VALUE;
        int result = -1;
        
        for (int i = 0; i < n; i++) {
            if (dist1[i] != -1 && dist2[i] != -1) {
                int maxDist = Math.max(dist1[i], dist2[i]);
                if (maxDist < minMaxDist) {
                    minMaxDist = maxDist;
                    result = i;
                } else if (maxDist == minMaxDist && i < result) {
                    result = i;
                }
            }
        }
        
        return result;
    }
    
    private void dfs(int[] edges, int node, int[] distances, int dist) {
        if (node == -1 || distances[node] != -1) {
            return;
        }
        
        distances[node] = dist;
        dfs(edges, edges[node], distances, dist + 1);
    }
    
    /**
     * Optimized solution using path following
     */
    public int closestMeetingNodeOptimized(int[] edges, int node1, int node2) {
        int n = edges.length;
        
        // Find all reachable nodes from node1 with distances
        Map<Integer, Integer> dist1 = new HashMap<>();
        int current = node1;
        int distance = 0;
        
        while (current != -1 && !dist1.containsKey(current)) {
            dist1.put(current, distance);
            current = edges[current];
            distance++;
        }
        
        // Follow path from node2 and find closest meeting point
        current = node2;
        distance = 0;
        int minMaxDist = Integer.MAX_VALUE;
        int result = -1;
        Set<Integer> visited = new HashSet<>();
        
        while (current != -1 && !visited.contains(current)) {
            visited.add(current);
            
            if (dist1.containsKey(current)) {
                int maxDist = Math.max(dist1.get(current), distance);
                if (maxDist < minMaxDist || (maxDist == minMaxDist && current < result)) {
                    minMaxDist = maxDist;
                    result = current;
                }
            }
            
            current = edges[current];
            distance++;
        }
        
        return result;
    }
    
    /**
     * Two-pointer approach
     */
    public int closestMeetingNodeTwoPointer(int[] edges, int node1, int node2) {
        int n = edges.length;
        
        // Get paths from both nodes
        List<Integer> path1 = getPath(edges, node1);
        List<Integer> path2 = getPath(edges, node2);
        
        // Convert to maps for O(1) lookup
        Map<Integer, Integer> pos1 = new HashMap<>();
        Map<Integer, Integer> pos2 = new HashMap<>();
        
        for (int i = 0; i < path1.size(); i++) {
            pos1.put(path1.get(i), i);
        }
        for (int i = 0; i < path2.size(); i++) {
            pos2.put(path2.get(i), i);
        }
        
        int minMaxDist = Integer.MAX_VALUE;
        int result = -1;
        
        // Check all nodes in both paths
        Set<Integer> allNodes = new HashSet<>(path1);
        allNodes.addAll(path2);
        
        for (int node : allNodes) {
            if (pos1.containsKey(node) && pos2.containsKey(node)) {
                int maxDist = Math.max(pos1.get(node), pos2.get(node));
                if (maxDist < minMaxDist || (maxDist == minMaxDist && node < result)) {
                    minMaxDist = maxDist;
                    result = node;
                }
            }
        }
        
        return result;
    }
    
    private List<Integer> getPath(int[] edges, int start) {
        List<Integer> path = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        int current = start;
        
        while (current != -1 && !visited.contains(current)) {
            visited.add(current);
            path.add(current);
            current = edges[current];
        }
        
        return path;
    }
    
    // Test method
    public static void main(String[] args) {
        FindClosestMeetingNode solution = new FindClosestMeetingNode();
        
        // Test case 1: edges = [2,2,3,-1], node1 = 0, node2 = 1
        int[] edges1 = {2,2,3,-1};
        System.out.println("Closest meeting node: " + solution.closestMeetingNode(edges1, 0, 1)); // 2
        
        // Test case 2: edges = [1,2,-1], node1 = 0, node2 = 2
        int[] edges2 = {1,2,-1};
        System.out.println("Closest meeting node (DFS): " + solution.closestMeetingNodeDFS(edges2, 0, 2)); // 2
        
        // Test case 3: edges = [1,2,3,4,-1], node1 = 0, node2 = 2
        int[] edges3 = {1,2,3,4,-1};
        System.out.println("Closest meeting node (Optimized): " + solution.closestMeetingNodeOptimized(edges3, 0, 2)); // 3
    }
}

