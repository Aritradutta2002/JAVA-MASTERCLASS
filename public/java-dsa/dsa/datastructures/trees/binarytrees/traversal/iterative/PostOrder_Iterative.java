package dsa.datastructures.trees.binarytrees.traversal.iterative;
import java.util.*;
/*
 *   Author  : Aritra Dutta
 *   Created : Thursday, 30.01.2025  09:28 am
 */

public class PostOrder_Iterative {
    public static void main(String[] args) {
        BinaryTreeStructure root = new BinaryTreeStructure(1);
        root.left = new BinaryTreeStructure(4);
        root.left.left = new BinaryTreeStructure(4);
        root.left.right = new BinaryTreeStructure(2);

        System.out.println(postorder(root));
    }

    static List < Integer > postorder(BinaryTreeStructure root) {

        List < Integer > result = new ArrayList < > ();
        if (root == null) {
            return result;
        }
        Stack <BinaryTreeStructure> stack = new Stack < > ();
        stack.push(root);
        BinaryTreeStructure prev = null;

        while (!stack.isEmpty()) {
            BinaryTreeStructure current = stack.peek();
            if (prev == null || prev.left == current || prev.right == current) {
                if (current.left != null)
                    stack.push(current.left);
                else if (current.right != null)
                    stack.push(current.right);
                else {
                    stack.pop();
                    result.add(current.data);
                }

        /* go up the tree from left node, if the
        child is right push it onto stack otherwise
        process parent and pop stack */

            } else if (current.left == prev) {
                if (current.right != null)
                    stack.push(current.right);
                else {
                    stack.pop();
                    result.add(current.data);
                }

        /* go up the tree from right node and after
        coming back from right node process parent
        and pop stack */

            } else if (current.right == prev) {
                stack.pop();
                result.add(current.data);
            }

            prev = current;
        }
        return result;
    }
}
