package dsa.algorithms.sorting.theory;
import java.lang.*;

public class MergeSort {

    static void displayArray(int []  arr){
        for(int num : arr){
            System.out.print(num + " ");
        }
    }
    static void mergeArray(int []arr, int l, int mid,  int r){

        int n1 = mid - l + 1;
        int n2 = r - mid;

        int[] left = new int[n1];
        int[] right = new int[n2];

        // Copy data to temp arrays
        for (int i = 0; i < n1; ++i) {
            left[i] = arr[l + i];
        }
        for (int j = 0; j < n2; ++j) {
            right[j] = arr[mid + 1 + j];
        }
        // Merge the temp arrays
        int i = 0, j = 0, k = l;
        while (i < n1 && j < n2) {
            if (left[i] <= right[j]) {
                arr[k++] = left[i++];
            } else {
                arr[k++] = right[j++];
            }
        }
        // Copy remaining elements of left[] if any
        while (i < n1) {
            arr[k++] = left[i++];
        }

        // Copy remaining elements of right[] if any
        while (j < n2) {
            arr[k++] = right[j++];
        }
    }

    static void  mergeSort( int [] nums , int l , int r ){
        if(l>=r){
            return;
        }
        int mid = l+(r-l)/2;
        mergeSort(nums,l,mid);
        mergeSort(nums,mid+1,r);
        mergeArray(nums, l, mid, r);
    }

    public static void main(String[] args) {

        System.out.println("Array before sorting: ");
        int nums [] = {9,25,1,5,3,4,6,5};
        displayArray(nums);

        mergeSort(nums,0,nums.length-1);
        System.out.println("\nSorted Array is : " );
        displayArray(nums);

    }
}

