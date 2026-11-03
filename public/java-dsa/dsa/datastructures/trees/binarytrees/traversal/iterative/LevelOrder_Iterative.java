package dsa.datastructures.trees.binarytrees.traversal.iterative;
import java.util.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Thursday, 30.01.2025  09:28 am
 */

public class LevelOrder_Iterative {
    public static void main(String[] args) {
        BinaryTreeStructure root = new BinaryTreeStructure(3);

        root.left = new BinaryTreeStructure(9);
        root.right = new BinaryTreeStructure(20);

        root.right.left = new BinaryTreeStructure(15);
        root.right.right = new BinaryTreeStructure(7);

        System.out.println(levelOrderIterative(root));
    }

    public static List<List<Integer>> levelOrderIterative(BinaryTreeStructure root) {

        List<List<Integer>> list = new ArrayList<>();
        if(root == null) { return list; }

        Queue<BinaryTreeStructure> queue = new ArrayDeque<>();
        queue.add(root);

        while(!queue.isEmpty()) {
            List<Integer> level = new ArrayList<>();
            int size = queue.size();
            for(int i = 0; i < size; i++) {
                BinaryTreeStructure current = queue.poll();
                level.add(current.data);
                if (current.left != null) {
                    queue.add(current.left);
                }
                if (current.right != null) {
                    queue.add(current.right);
                }
            }

            list.add(level);
        }

        return list;
    }
}

