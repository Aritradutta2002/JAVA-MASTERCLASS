package dsa.datastructures.trees.binarytrees.problems.medium;

import java.util.*;

/*
 * LeetCode 116: Populating Next Right Pointers in Each Node
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/populating-next-right-pointers-in-each-node/
 * 
 * Problem: Given perfect binary tree, populate each node's next pointer to its next right node.
 * 
 * Example: Input: [1,2,3,4,5,6,7] → Next pointers set level-wise.
 */

// Definition for a Node with next pointer
class Node {
    public int val;
    public Node left;
    public Node right;
    public Node next;

    public Node() {}
    
    public Node(int _val) {
        val = _val;
    }

    public Node(int _val, Node _left, Node _right, Node _next) {
        val = _val;
        left = _left;
        right = _right;
        next = _next;
    }
}

public class Populating_Next_Right_Pointers_LeetCode116 {
    
    // O(1) space solution using next pointers
    public Node connect(Node root) {
        if (root == null) return null;
        
        Node leftmost = root;
        
        // Iterate through each level
        while (leftmost.left != null) {
            Node head = leftmost;
            
            // Iterate through current level and connect next level
            while (head != null) {
                // Connect children of current node
                head.left.next = head.right;
                
                // Connect right child to next node's left child
                if (head.next != null) {
                    head.right.next = head.next.left;
                }
                
                head = head.next;
            }
            
            leftmost = leftmost.left; // Move to next level
        }
        
        return root;
    }
    
    // BFS approach using queue (O(n) space)
    public Node connectBFS(Node root) {
        if (root == null) return null;
        
        Queue<Node> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                Node node = queue.poll();
                
                // Connect to next node in the same level
                if (i < size - 1) {
                    node.next = queue.peek();
                }
                
                if (node.left != null) queue.offer(node.left);
                if (node.right != null) queue.offer(node.right);
            }
        }
        
        return root;
    }
    
    // Recursive approach
    public Node connectRecursive(Node root) {
        if (root == null) return null;
        
        connectTwoNodes(root.left, root.right);
        return root;
    }
    
    private void connectTwoNodes(Node node1, Node node2) {
        if (node1 == null || node2 == null) return;
        
        // Connect the two nodes
        node1.next = node2;
        
        // Connect children within same parent
        connectTwoNodes(node1.left, node1.right);
        connectTwoNodes(node2.left, node2.right);
        
        // Connect children across different parents
        connectTwoNodes(node1.right, node2.left);
    }
    
    // Helper method to print level order with next pointers
    public void printLevelOrderWithNext(Node root) {
        Node levelStart = root;
        
        while (levelStart != null) {
            Node current = levelStart;
            while (current != null) {
                System.out.print(current.val);
                if (current.next != null) {
                    System.out.print(" -> ");
                } else {
                    System.out.print(" -> null");
                }
                current = current.next;
            }
            System.out.println();
            levelStart = levelStart.left; // Move to next level
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Populating_Next_Right_Pointers_LeetCode116 solution = new Populating_Next_Right_Pointers_LeetCode116();
        
        // Test case: [1,2,3,4,5,6,7]
        Node root = new Node(1);
        root.left = new Node(2);
        root.right = new Node(3);
        root.left.left = new Node(4);
        root.left.right = new Node(5);
        root.right.left = new Node(6);
        root.right.right = new Node(7);
        
        System.out.println("Before connecting:");
        // Print structure would be complex, so we'll just show the result
        
        solution.connect(root);
        
        System.out.println("After connecting next pointers:");
        solution.printLevelOrderWithNext(root);
        
        // Expected output:
        // Level 1: 1 -> null
        // Level 2: 2 -> 3 -> null  
        // Level 3: 4 -> 5 -> 6 -> 7 -> null
    }
}
