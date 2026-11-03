package dsa.datastructures.trees.binarytrees.traversal.recursive;
import java.util.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Wednesday, 29.01.2025  08:55 pm
 */
public class PostOrder_Recursive {
    public static void main(String[] args) {
        BinaryTreeNode root = new BinaryTreeNode(1);
        root.left = new BinaryTreeNode(4);
        root.left.left = new BinaryTreeNode(4);
        root.left.right = new BinaryTreeNode(2);;

        PostorderTraversal(root);
        System.out.println();
        System.out.println(postorderTraversal(root));
    }

    /*     POST ORDER TRAVERSAL STARTS     */
    static public void PostorderTraversal(BinaryTreeNode root) {
        if(root != null) {
            PostorderTraversal(root.left);
            PostorderTraversal(root.right);
            System.out.print(root.data + " ");
        }
    }

    public static List<Integer> postorderTraversal(BinaryTreeNode root) {
        List<Integer> list = new ArrayList<>();
        if(root != null) {
            list.addAll(postorderTraversal(root.left));
            list.addAll(postorderTraversal(root.right));
            list.add(root.data);
        }
        return list;
    }
}

