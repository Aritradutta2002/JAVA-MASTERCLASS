package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 1569: Number of Ways to Reorder Array to Get Same BST
 * 
 * Problem: Given an array nums that represents a permutation of integers from 1 to n. We are going to construct a
 * binary search tree (BST) by inserting the elements of nums in order into an initially empty BST. Find
 * the number of different ways to reorder nums so that the constructed BST is identical to that formed
 * from the original array nums.
 * Return the number of ways to reorder nums such that the BST formed is identical to the original BST
 * formed from nums. Since the answer may be very large, return it modulo 10^9 + 7.
 * 
 * Time Complexity: O(n^2)
 * Space Complexity: O(n^2)
 */
public class NumberOfWaysReorderBST {
    
    private static final int MOD = 1000000007;
    private long[][] comb;
    
    /**
     * Main solution using combinatorics
     */
    public int numOfWays(int[] nums) {
        int n = nums.length;
        
        // Precompute Pascal's triangle for combinations
        comb = new long[n][n];
        for (int i = 0; i < n; i++) {
            comb[i][0] = 1;
            for (int j = 1; j <= i; j++) {
                comb[i][j] = (comb[i-1][j-1] + comb[i-1][j]) % MOD;
            }
        }
        
        List<Integer> list = new ArrayList<>();
        for (int num : nums) {
            list.add(num);
        }
        
        // Subtract 1 because we don't count the original arrangement
        return (int) ((solve(list) - 1 + MOD) % MOD);
    }
    
    private long solve(List<Integer> nums) {
        if (nums.size() <= 2) {
            return 1;
        }
        
        int root = nums.get(0);
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        
        // Partition into left and right subtrees
        for (int i = 1; i < nums.size(); i++) {
            if (nums.get(i) < root) {
                left.add(nums.get(i));
            } else {
                right.add(nums.get(i));
            }
        }
        
        int leftSize = left.size();
        int rightSize = right.size();
        
        // Ways to arrange left subtree * ways to arrange right subtree
        // * ways to interleave left and right sequences
        long leftWays = solve(left);
        long rightWays = solve(right);
        long combWays = comb[leftSize + rightSize][leftSize];
        
        return (leftWays * rightWays % MOD) * combWays % MOD;
    }
    
    /**
     * Alternative solution using memoization
     */
    public int numOfWaysMemo(int[] nums) {
        int n = nums.length;
        
        // Precompute combinations
        long[][] C = new long[n][n];
        for (int i = 0; i < n; i++) {
            C[i][0] = 1;
            for (int j = 1; j <= i; j++) {
                C[i][j] = (C[i-1][j-1] + C[i-1][j]) % MOD;
            }
        }
        
        Map<String, Long> memo = new HashMap<>();
        List<Integer> list = new ArrayList<>();
        for (int num : nums) {
            list.add(num);
        }
        
        return (int) ((solveMemo(list, C, memo) - 1 + MOD) % MOD);
    }
    
    private long solveMemo(List<Integer> nums, long[][] C, Map<String, Long> memo) {
        if (nums.size() <= 2) {
            return 1;
        }
        
        String key = nums.toString();
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        
        int root = nums.get(0);
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        
        for (int i = 1; i < nums.size(); i++) {
            if (nums.get(i) < root) {
                left.add(nums.get(i));
            } else {
                right.add(nums.get(i));
            }
        }
        
        long result = (solveMemo(left, C, memo) * solveMemo(right, C, memo) % MOD) 
                     * C[left.size() + right.size()][left.size()] % MOD;
        
        memo.put(key, result);
        return result;
    }
    
    /**
     * Iterative solution using tree structure
     */
    public int numOfWaysIterative(int[] nums) {
        if (nums.length <= 1) return 1;
        
        int n = nums.length;
        
        // Build Pascal's triangle
        long[][] comb = new long[n][n];
        for (int i = 0; i < n; i++) {
            comb[i][0] = 1;
            for (int j = 1; j <= i; j++) {
                comb[i][j] = (comb[i-1][j-1] + comb[i-1][j]) % MOD;
            }
        }
        
        // Build BST structure
        TreeNode root = buildBST(nums);
        
        // Calculate ways
        return (int) ((countWays(root, comb) - 1 + MOD) % MOD);
    }
    
    private TreeNode buildBST(int[] nums) {
        TreeNode root = new TreeNode(nums[0]);
        for (int i = 1; i < nums.length; i++) {
            insertBST(root, nums[i]);
        }
        return root;
    }
    
    private void insertBST(TreeNode root, int val) {
        if (val < root.val) {
            if (root.left == null) {
                root.left = new TreeNode(val);
            } else {
                insertBST(root.left, val);
            }
        } else {
            if (root.right == null) {
                root.right = new TreeNode(val);
            } else {
                insertBST(root.right, val);
            }
        }
    }
    
    private long countWays(TreeNode root, long[][] comb) {
        if (root == null) return 1;
        
        int leftSize = getSize(root.left);
        int rightSize = getSize(root.right);
        
        long leftWays = countWays(root.left, comb);
        long rightWays = countWays(root.right, comb);
        
        return (leftWays * rightWays % MOD) * comb[leftSize + rightSize][leftSize] % MOD;
    }
    
    private int getSize(TreeNode root) {
        if (root == null) return 0;
        return 1 + getSize(root.left) + getSize(root.right);
    }
    
    // TreeNode class
    class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;
        TreeNode(int val) { this.val = val; }
    }
    
    // Test method
    public static void main(String[] args) {
        NumberOfWaysReorderBST solution = new NumberOfWaysReorderBST();
        
        // Test case 1: [2,1,3]
        int[] nums1 = {2,1,3};
        System.out.println("Number of ways: " + solution.numOfWays(nums1)); // 1
        
        // Test case 2: [3,4,5,1,2]
        int[] nums2 = {3,4,5,1,2};
        System.out.println("Number of ways (Memo): " + solution.numOfWaysMemo(nums2)); // 5
        
        // Test case 3: [1,2,3]
        int[] nums3 = {1,2,3};
        System.out.println("Number of ways (Iterative): " + solution.numOfWaysIterative(nums3)); // 0
    }
}

