package dsa.datastructures.trees.binarytrees.traversal.recursive;
import com.sun.source.tree.BinaryTree;
import java.util.*;

public class LevelOrder_Recursive {
    public static void main(String[] args) {
        BinaryTreeNode root = new BinaryTreeNode(3);

        root.left = new BinaryTreeNode(9);
        root.right = new BinaryTreeNode(20);

        root.right.left = new BinaryTreeNode(15);
        root.right.right = new BinaryTreeNode(7);

        System.out.println(levelOrder(root));
    }

    /*    LEVEL ORDER TRAVERSAL STARTS    */
    public static List<List<Integer>> levelOrder(BinaryTreeNode root) {
        List<List<Integer>> ans = new ArrayList<>();
        traverse(root, 0, ans);
        return ans;
    }

    private static void traverse(BinaryTreeNode node, int level, List<List<Integer>> ans) {
        if (node == null) return;

        if (ans.size() == level) {
            ans.add(new ArrayList<>());
        }

        ans.get(level).add(node.data);

        traverse(node.left, level + 1, ans);
        traverse(node.right, level + 1, ans);
    }
}
