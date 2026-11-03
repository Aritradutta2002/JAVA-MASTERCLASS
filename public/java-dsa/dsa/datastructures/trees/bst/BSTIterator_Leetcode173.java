package dsa.algorithms.searching.binary.basics_TREES;

import java.util.Stack;

/*
 *   Author  : Aritra Dutta
 *   Problem : Binary Search Tree Iterator (Leetcode 173)
 *   Difficulty: Medium
 *   Companies: Facebook, Google, Microsoft, Amazon, LinkedIn
 */
public class BSTIterator_Leetcode173 {
    private Stack<TreeNode> stack;
    
    public BSTIterator_Leetcode173(TreeNode root) {
        stack = new Stack<>();
        pushAll(root);
    }
    
    public int next() {
        TreeNode tmpNode = stack.pop();
        pushAll(tmpNode.right);
        return tmpNode.val;
    }
    
    public boolean hasNext() {
        return !stack.isEmpty();
    }
    
    private void pushAll(TreeNode node) {
        while (node != null) {
            stack.push(node);
            node = node.left;
        }
    }
    
    public static void main(String[] args) {
        TreeNode root = new TreeNode(7);
        root.left = new TreeNode(3);
        root.right = new TreeNode(15);
        root.right.left = new TreeNode(9);
        root.right.right = new TreeNode(20);
        
        BSTIterator_Leetcode173 iterator = new BSTIterator_Leetcode173(root);
        System.out.println("Iterator values:");
        while (iterator.hasNext()) {
            System.out.print(iterator.next() + " ");
        }
    }
}

