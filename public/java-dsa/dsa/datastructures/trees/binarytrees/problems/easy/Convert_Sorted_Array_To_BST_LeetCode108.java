package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 108: Convert Sorted Array to Binary Search Tree
 * Difficulty: Easy
 * Link: https://leetcode.com/problems/convert-sorted-array-to-binary-search-tree/
 * 
 * Problem: Convert sorted array to height-balanced BST.
 * 
 * Example: Input: [-10,-3,0,5,9] → Output: [0,-3,9,-10,null,5]
 */

public class Convert_Sorted_Array_To_BST_LeetCode108 {
    
    public TreeNode sortedArrayToBST(int[] nums) {
        return build(nums, 0, nums.length - 1);
    }
    
    private TreeNode build(int[] a, int l, int r) {
        if (l > r) return null;
        
        int m = (l + r) / 2; // Choose middle element as root
        TreeNode root = new TreeNode(a[m]);
        
        root.left = build(a, l, m - 1);
        root.right = build(a, m + 1, r);
        
        return root;
    }
    
    // Alternative: Choose left-middle or right-middle for different balanced trees
    public TreeNode sortedArrayToBSTLeftMiddle(int[] nums) {
        return buildLeftMiddle(nums, 0, nums.length - 1);
    }
    
    private TreeNode buildLeftMiddle(int[] nums, int left, int right) {
        if (left > right) return null;
        
        // Always choose left middle node as root
        int p = (left + right) / 2;
        if ((left + right) % 2 == 1) p--;
        
        TreeNode root = new TreeNode(nums[p]);
        root.left = buildLeftMiddle(nums, left, p - 1);
        root.right = buildLeftMiddle(nums, p + 1, right);
        
        return root;
    }
    
    // Alternative: Choose right-middle
    public TreeNode sortedArrayToBSTRightMiddle(int[] nums) {
        return buildRightMiddle(nums, 0, nums.length - 1);
    }
    
    private TreeNode buildRightMiddle(int[] nums, int left, int right) {
        if (left > right) return null;
        
        // Always choose right middle node as root
        int p = (left + right) / 2;
        if ((left + right) % 2 == 0) p++;
        
        TreeNode root = new TreeNode(nums[p]);
        root.left = buildRightMiddle(nums, left, p - 1);
        root.right = buildRightMiddle(nums, p + 1, right);
        
        return root;
    }
    
    // Helper method to print inorder traversal
    public void printInorder(TreeNode root) {
        if (root != null) {
            printInorder(root.left);
            System.out.print(root.val + " ");
            printInorder(root.right);
        }
    }
    
    // Helper method to check if tree is balanced
    public boolean isBalanced(TreeNode root) {
        return checkBalance(root) != -1;
    }
    
    private int checkBalance(TreeNode node) {
        if (node == null) return 0;
        
        int left = checkBalance(node.left);
        if (left == -1) return -1;
        
        int right = checkBalance(node.right);
        if (right == -1) return -1;
        
        if (Math.abs(left - right) > 1) return -1;
        
        return Math.max(left, right) + 1;
    }
    
    // Test method
    public static void main(String[] args) {
        Convert_Sorted_Array_To_BST_LeetCode108 solution = new Convert_Sorted_Array_To_BST_LeetCode108();
        
        // Test case: [-10,-3,0,5,9]
        int[] nums = {-10, -3, 0, 5, 9};
        
        TreeNode root = solution.sortedArrayToBST(nums);
        
        System.out.print("Constructed BST (inorder): ");
        solution.printInorder(root);
        System.out.println();
        // Expected: -10 -3 0 5 9 (should match original sorted array)
        
        System.out.println("Is balanced: " + solution.isBalanced(root));
        // Expected: true
        
        // Test different middle selection strategies
        TreeNode leftMiddle = solution.sortedArrayToBSTLeftMiddle(nums);
        System.out.print("Left-middle strategy (inorder): ");
        solution.printInorder(leftMiddle);
        System.out.println();
        
        TreeNode rightMiddle = solution.sortedArrayToBSTRightMiddle(nums);
        System.out.print("Right-middle strategy (inorder): ");
        solution.printInorder(rightMiddle);
        System.out.println();
    }
}

