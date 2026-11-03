package dsa.datastructures.trees.binarytrees.traversal.iterative;

import java.util.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Thursday, 30.01.2025  09:28 am
 */
public class PreOrder_Iterative {

    public static void main(String[] args) {
        BinaryTreeStructure root = new BinaryTreeStructure(1);
        root.left = new BinaryTreeStructure(4);
        root.left.left = new BinaryTreeStructure(4);
        root.left.right = new BinaryTreeStructure(2);

        System.out.println(preOrder_Loop(root));
    }

    static List<Integer> preOrder_Loop(BinaryTreeStructure root) {
        List<Integer> list = new ArrayList<>();

        if (root == null) {
            return list;
        }

        Stack<BinaryTreeStructure> stack = new Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            root = stack.pop();
            list.add(root.data);

            if (root.right != null) {
                stack.push(root.right);
            }

            if (root.left != null) {
                stack.push(root.left);
            }
        }

        return list;
    }
}

