package dsa.algorithms.sorting.theory;

import java.lang.*;
import java.util.*;

public class BubbleSort {

    public static void main(String[] args) {
        int i,j,temp=0;
        Scanner sc = new Scanner(System.in);
        int[] arr = new int [5];
        System.out.println("Enter the Array Element: ");
        for(i=0;i<5;i++){
            arr[i] = sc.nextInt();
        }

        for(i=0;i<5;i++) {
            for (j=1; j<(5-i); j++) {
                if (arr[j-1] > arr[j]) {
                     temp = arr[j-1];
                    arr[j-1] = arr[j];
                    arr[j] = temp;
                }
            }
        }

        System.out.println(Arrays.toString(arr));
    }
}

