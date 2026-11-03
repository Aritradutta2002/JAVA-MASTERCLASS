package dsa.algorithms.sorting.theory;

import java.util.Arrays;

public class mergeTwoSortedArray {

    public static int [] merge(int [] arr1 , int [] arr2) {

        int [] result = new int[arr1.length + arr2.length];

        int i = 0;
        int j = 0;
        int k = 0;

        while(i < arr1.length && j < arr2.length) {
            if (arr1[i] <= arr2[j]) {
                result[k++] = arr1[i++];
            } else {
                result[k++] = arr2[j++];
            }
        }

        while(i < arr1.length) {
            result[k++] = arr1[i++];
        }
        while(j < arr2.length) {
            result[k++] = arr2[j++];
        }
        return result;
    }
    public static void main(String[] args) {

        int [] arr1 = new int [] {1,2,3,4,5};
        int [] arr2 = new int [] {5, 8, 9, 10};
        int [] result = merge(arr1 , arr2);
        System.out.println(Arrays.toString(result));
    }
}

