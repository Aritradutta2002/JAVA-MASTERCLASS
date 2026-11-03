package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/**
 * Author: Aritra
 * Date: Wednesday, 15.10.2025 12:57 AM
 * Description: Find the Lowest Common Ancestor (LCA) of two nodes in a binary tree.
 */
public class LowestCommonAncestor_RecursiveApproach {

    /**
     * Returns the lowest common ancestor (LCA) of nodes p and q in the binary tree rooted at root.
     */
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null) return null;
        if (root == p || root == q) return root; // Use node reference equality, not value equality

        TreeNode left = lowestCommonAncestor(root.left, p, q);
        TreeNode right = lowestCommonAncestor(root.right, p, q);

        if (left != null && right != null) return root;
        return (left != null) ? left : right;
    }

    public static void main(String[] args) {
        // Sample binary tree
        TreeNode root = new TreeNode(6);
        root.left = new TreeNode(2);
        root.right = new TreeNode(8);
        root.left.left = new TreeNode(0);
        root.left.right = new TreeNode(4);
        root.right.left = new TreeNode(7);
        root.right.right = new TreeNode(9);
        root.left.right.left = new TreeNode(3);
        root.left.right.right = new TreeNode(5);


        LowestCommonAncestor_RecursiveApproach lca = new LowestCommonAncestor_RecursiveApproach();
        TreeNode p = root.left;  // Node 2
        TreeNode q = root.right; // Node 8

        TreeNode result = lca.lowestCommonAncestor(root, p, q);
        System.out.println("LCA of " + p.val + " and " + q.val + " is: " + result.val);
    }
}




