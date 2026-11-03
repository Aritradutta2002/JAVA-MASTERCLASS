package dsa.algorithms.searching.binary.basics_TREES;

import JAVA_DSA.BINARY_TREES.Traversal_Techniques.RecursiveApproach.InOrder_Recursive;

import java.util.Scanner;

/*
 *   Author  : Aritra Dutta
 *   Created : Friday, 28.02.2025 01:20 am
 */
public class ConvertSortedArrayToBST_Leetcode108 {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter the size of the array: ");
        int size = sc.nextInt();
        int[] nums = new int[size];

        System.out.println("Enter the elements of the array: ");
        for (int i = 0; i < size; i++) {
            nums[i] = sc.nextInt();
        }

        TreeNode root = sortedArrayToBST(nums);
        inorderTraversal(root);
    }

    public static TreeNode sortedArrayToBST(int[] nums) {
        return buildBST(nums, 0, nums.length - 1);
    }

    public static TreeNode buildBST(int[] nums, int start, int end) {
        if (start > end) return null;
        int midPoint = start + (end - start) / 2;
        TreeNode node = new TreeNode(nums[midPoint]);
        node.left = buildBST(nums, start, midPoint - 1);
        node.right = buildBST(nums, midPoint + 1, end);
        return node;
    }

    public static void inorderTraversal(TreeNode root) {
        if (root == null) return;
        inorderTraversal(root.left);
        System.out.println(root.val);
        inorderTraversal(root.right);
    }
}
