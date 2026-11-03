package dsa.datastructures.trees.binarytrees.problems.difficult;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 297: Serialize and Deserialize Binary Tree
 * Difficulty: Hard
 * Link: https://leetcode.com/problems/serialize-and-deserialize-binary-tree/
 * 
 * Problem: Design an algorithm to serialize and deserialize a binary tree.
 * 
 * Example: Input: [1,2,3,null,null,4,5] → Output: "1,2,3,null,null,4,5"
 */

public class Serialize_Deserialize_Binary_Tree_LeetCode297 {
    
    private static final String NULL_NODE = "null";
    private static final String DELIMITER = ",";
    
    // Encodes a tree to a single string (Pre-order traversal)
    public String serialize(TreeNode root) {
        StringBuilder sb = new StringBuilder();
        serializeHelper(root, sb);
        return sb.toString();
    }
    
    private void serializeHelper(TreeNode node, StringBuilder sb) {
        if (node == null) {
            sb.append(NULL_NODE).append(DELIMITER);
            return;
        }
        
        sb.append(node.val).append(DELIMITER);
        serializeHelper(node.left, sb);
        serializeHelper(node.right, sb);
    }
    
    // Decodes your encoded data to tree
    public TreeNode deserialize(String data) {
        Queue<String> queue = new LinkedList<>(Arrays.asList(data.split(DELIMITER)));
        return deserializeHelper(queue);
    }
    
    private TreeNode deserializeHelper(Queue<String> queue) {
        String val = queue.poll();
        
        if (NULL_NODE.equals(val)) {
            return null;
        }
        
        TreeNode node = new TreeNode(Integer.parseInt(val));
        node.left = deserializeHelper(queue);
        node.right = deserializeHelper(queue);
        
        return node;
    }
    
    // Alternative: Level-order (BFS) approach
    public String serializeBFS(TreeNode root) {
        if (root == null) return "";
        
        StringBuilder sb = new StringBuilder();
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            
            if (node == null) {
                sb.append(NULL_NODE).append(DELIMITER);
            } else {
                sb.append(node.val).append(DELIMITER);
                queue.offer(node.left);
                queue.offer(node.right);
            }
        }
        
        return sb.toString();
    }
    
    public TreeNode deserializeBFS(String data) {
        if (data.isEmpty()) return null;
        
        String[] values = data.split(DELIMITER);
        TreeNode root = new TreeNode(Integer.parseInt(values[0]));
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        int i = 1;
        while (!queue.isEmpty() && i < values.length) {
            TreeNode node = queue.poll();
            
            if (!NULL_NODE.equals(values[i])) {
                node.left = new TreeNode(Integer.parseInt(values[i]));
                queue.offer(node.left);
            }
            i++;
            
            if (i < values.length && !NULL_NODE.equals(values[i])) {
                node.right = new TreeNode(Integer.parseInt(values[i]));
                queue.offer(node.right);
            }
            i++;
        }
        
        return root;
    }
    
    // Helper method to print tree
    public void printInorder(TreeNode root) {
        if (root != null) {
            printInorder(root.left);
            System.out.print(root.val + " ");
            printInorder(root.right);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Serialize_Deserialize_Binary_Tree_LeetCode297 solution = new Serialize_Deserialize_Binary_Tree_LeetCode297();
        
        // Test case: [1,2,3,null,null,4,5]
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.right.left = new TreeNode(4);
        root.right.right = new TreeNode(5);
        
        System.out.print("Original tree (inorder): ");
        solution.printInorder(root);
        System.out.println();
        
        String serialized = solution.serialize(root);
        System.out.println("Serialized: " + serialized);
        
        TreeNode deserialized = solution.deserialize(serialized);
        System.out.print("Deserialized tree (inorder): ");
        solution.printInorder(deserialized);
        System.out.println();
    }
}

