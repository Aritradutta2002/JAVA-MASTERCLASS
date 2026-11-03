package dsa.datastructures.trees.binarytrees.traversal.iterative;
import java.util.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Thursday, 30.01.2025  09:29 am
 */
public class InOrder_Iterative {
    public static void main(String[] args) {
        BinaryTreeStructure root = new BinaryTreeStructure(1);
        root.left = new BinaryTreeStructure(4);
        root.left.left = new BinaryTreeStructure(4);
        root.left.right = new BinaryTreeStructure(2);

        System.out.println(inorder(root));
    }

    public static List<Integer> inorder(BinaryTreeStructure root) {

        List<Integer> list = new ArrayList<>();
        if(root == null) { return list; }

        Stack<BinaryTreeStructure> st = new Stack<>();
        BinaryTreeStructure current = root;


        while(true) {
            if(current != null) {
                st.push(current);
                current = current.getLeft();
            }
            else {
                if(st.isEmpty()) {
                    break;
                }
                else{
                    current = st.pop();
                    list.add(current.data);
                    current = current.getRight();
                }
            }
        }

        return list;
    }
}

