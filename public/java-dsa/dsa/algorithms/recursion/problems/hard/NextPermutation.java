package dsa.algorithms.recursion.problems.hard;

import static java.util.Arrays.sort;

public class NextPermutation {
    static public void nextPermutation(int[] nums) {
        int n = nums.length;
        int index = -1;
        int index2 = -1;
        for (int i = n - 2; i >= 0; i--) {
            if (nums[i] < nums[i + 1]) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            reverse(nums, 0);
        } else {
            for (int i = n - 1; i >= 0; i--) {
                if (nums[i] > nums[index]) {
                    index2 = i;
                    break;
                }
            }
            swap(nums, index, index2);
            reverse(nums, index + 1);
        }
    }


    public static void swap(int[] nums, int i, int j){
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    public static void reverse(int[] nums, int start){
        int i=start;
        int j=nums.length-1;
        while(i<j){
            swap(nums,i,j);
            i++;
            j--;
        }
    }
    static void display(int[] nums, int start , int end) {
        for(int i = start; i <= end; i++){
            System.out.print(nums[i] + " ");
        }
    }

    public static void main(String[] args) {

        int [] arr = {1,1,5};
        nextPermutation(arr);
        display(arr, 0, arr.length-1);
    }
}

