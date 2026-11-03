package dsa.algorithms.sorting.theory;

public class QuickSortApproach2 {

    static void display(int[] arr) {
        for (int num : arr) {
            System.out.print(num + "  ");
        }
    }

    static void swap(int arr[], int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    static void QuickSort(int[] arr, int start, int end) {
        if (start >= end) {
            return;
        }
        int pivot = partition(arr, start, end);
        QuickSort(arr, start, pivot - 1);
        QuickSort(arr, pivot + 1, end);
    }

    static int partition(int[] arr, int start, int end) {
        int pivot = arr[start];
        int i = start + 1;
        int j = end;

        while (i <= j) {
            while ( i <= end && arr[i] <= pivot) {
                i++;
            }
            while (arr[j] > pivot) {
                j--;
            }
            if (i < j) {
                swap(arr, i, j);
            }
        }
        swap(arr, start, j);
        return j;
    }

    public static void main(String[] args) {

        System.out.println("Array before sorting: ");
        int nums[] = {2, 5, 6, 7, 8, 9, 10, 15, 1, 2, 7};
        display(nums);
        System.out.println();
        System.out.println("Array after sorting: ");
        QuickSort(nums, 0, nums.length - 1);
        display(nums);
    }
}

