package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 133: Clone Graph
 * 
 * Problem: Given a reference of a node in a connected undirected graph,
 * return a deep copy (clone) of the graph.
 * 
 * Time Complexity: O(V + E) where V is vertices and E is edges
 * Space Complexity: O(V) for the hash map and recursion stack
 */
public class CloneGraph {
    
    // Definition for a Node
    static class Node {
        public int val;
        public List<Node> neighbors;
        
        public Node() {
            val = 0;
            neighbors = new ArrayList<Node>();
        }
        
        public Node(int _val) {
            val = _val;
            neighbors = new ArrayList<Node>();
        }
        
        public Node(int _val, ArrayList<Node> _neighbors) {
            val = _val;
            neighbors = _neighbors;
        }
    }
    
    /**
     * DFS Solution with HashMap to track cloned nodes
     */
    private Map<Node, Node> clonedNodes = new HashMap<>();
    
    public Node cloneGraph(Node node) {
        if (node == null) {
            return null;
        }
        
        // If node is already cloned, return the clone
        if (clonedNodes.containsKey(node)) {
            return clonedNodes.get(node);
        }
        
        // Create a clone of the current node
        Node cloneNode = new Node(node.val);
        clonedNodes.put(node, cloneNode);
        
        // Clone all the neighbors
        for (Node neighbor : node.neighbors) {
            cloneNode.neighbors.add(cloneGraph(neighbor));
        }
        
        return cloneNode;
    }
    
    /**
     * BFS Solution
     */
    public Node cloneGraphBFS(Node node) {
        if (node == null) return null;
        
        Map<Node, Node> visited = new HashMap<>();
        Queue<Node> queue = new LinkedList<>();
        
        // Clone the first node and add to queue
        visited.put(node, new Node(node.val));
        queue.offer(node);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            
            // Process all neighbors
            for (Node neighbor : current.neighbors) {
                if (!visited.containsKey(neighbor)) {
                    // Clone the neighbor
                    visited.put(neighbor, new Node(neighbor.val));
                    queue.offer(neighbor);
                }
                
                // Add the cloned neighbor to cloned current node's neighbors
                visited.get(current).neighbors.add(visited.get(neighbor));
            }
        }
        
        return visited.get(node);
    }
    
    /**
     * Iterative DFS with Stack
     */
    public Node cloneGraphIterativeDFS(Node node) {
        if (node == null) return null;
        
        Map<Node, Node> visited = new HashMap<>();
        Stack<Node> stack = new Stack<>();
        
        visited.put(node, new Node(node.val));
        stack.push(node);
        
        while (!stack.isEmpty()) {
            Node current = stack.pop();
            
            for (Node neighbor : current.neighbors) {
                if (!visited.containsKey(neighbor)) {
                    visited.put(neighbor, new Node(neighbor.val));
                    stack.push(neighbor);
                }
                visited.get(current).neighbors.add(visited.get(neighbor));
            }
        }
        
        return visited.get(node);
    }
    
    // Helper method to create a test graph
    public static Node createTestGraph() {
        Node node1 = new Node(1);
        Node node2 = new Node(2);
        Node node3 = new Node(3);
        Node node4 = new Node(4);
        
        node1.neighbors.add(node2);
        node1.neighbors.add(node4);
        node2.neighbors.add(node1);
        node2.neighbors.add(node3);
        node3.neighbors.add(node2);
        node3.neighbors.add(node4);
        node4.neighbors.add(node1);
        node4.neighbors.add(node3);
        
        return node1;
    }
    
    // Helper method to print graph
    public static void printGraph(Node node, Set<Node> visited) {
        if (node == null || visited.contains(node)) return;
        
        visited.add(node);
        System.out.print("Node " + node.val + " -> ");
        for (Node neighbor : node.neighbors) {
            System.out.print(neighbor.val + " ");
        }
        System.out.println();
        
        for (Node neighbor : node.neighbors) {
            printGraph(neighbor, visited);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        CloneGraph solution = new CloneGraph();
        
        // Create test graph
        Node original = createTestGraph();
        
        System.out.println("Original Graph:");
        printGraph(original, new HashSet<>());
        
        // Clone the graph
        Node cloned = solution.cloneGraph(original);
        
        System.out.println("\nCloned Graph:");
        printGraph(cloned, new HashSet<>());
        
        System.out.println("\nOriginal and cloned are different objects: " + 
                          (original != cloned));
    }
}

