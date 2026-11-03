package dsa.algorithms.recursion.problems.medium;

public class BinarySearchRecursive {

    static int BinarySearch(int [] arr, int target, int low, int high){

        if(low > high){
            return -1;
        }

        int mid = low + (high-low)/2;

        if(arr[mid] > target){
           return BinarySearch(arr, target, low, mid);
        }
        if(arr[mid] < target){
            return BinarySearch(arr, target, mid+1, high);
        }

        return mid;
    }

    public static void main(String[] args) {

        int [] arr = {1,2,3,4,5,6,7,8,9};
        int target = 1;
        System.out.println(BinarySearch(arr, target, 0, arr.length-1));
    }
}

