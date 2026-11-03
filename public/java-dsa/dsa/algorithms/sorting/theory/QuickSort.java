package dsa.algorithms.sorting.theory;

import java.util.Arrays;

public class QuickSort {

    static int partition(int[] arr, int low, int high) {
        int pivot = arr[low];
        int count = 0;

        for(int k = low + 1; k <= high; k++) {
            if(arr[k] <= pivot) {
                count++;
            }
        }

        int pivotIndex = low + count;
        swap(arr, low, pivotIndex);

        int i = low;
        int j = high;

        while (i < pivotIndex && j > pivotIndex) {
            while (arr[i] <= pivot) {
                i++;
            }
            while (arr[j] > pivot) {
                j--;
            }
            if (i < pivotIndex && j > pivotIndex) {
                swap(arr, i, j);
                i++;
                j--;
            }
        }
        return pivotIndex;
    }

    static void display(int[] arr) {
        for(int num : arr){
            System.out.print(num + " ");
        }
        System.out.println();
    }

    static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    static void quickSort(int[] arr, int start, int end) {
        if (start < end) {
            int pivot = partition(arr, start, end);
            quickSort(arr, start, pivot - 1);
            quickSort(arr, pivot + 1, end);
        }
    }

    public static void main(String[] args) {
        int[] arr = {7, 8, 5, 7, 10, 13};
        System.out.println("The array before sorting is: ");
        System.out.println(Arrays.toString(arr));
        quickSort(arr, 0, arr.length - 1);
        System.out.println("The array after sorting is: ");
        System.out.println(Arrays.toString(arr));
    }
}

