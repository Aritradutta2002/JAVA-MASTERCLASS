package dsa.datastructures.arrays.basics;
import java.lang.*;
import java.util.*;

public class RemoveDuplicates {
    public static void main(String[] args) {
        Set<Integer> set = new HashSet<>();
        Scanner sc = new Scanner(System.in);
        int size = sc.nextInt();
        int[] arr = new int[size];

        for (int i = 0; i < arr.length; i++) {
            arr[i] = sc.nextInt();
        }

        List<Integer> list = new ArrayList<>();
        for (int i = arr.length - 1; i >= 0; i--) {
            if (!set.contains(arr[i])) {
                list.add(0, arr[i]); // Add to the beginning of the list to maintain order
                set.add(arr[i]);
            }
        }

        System.out.println(list.size());
        for (int num : list) {
            System.out.print(num + " ");
        }
    }
}

