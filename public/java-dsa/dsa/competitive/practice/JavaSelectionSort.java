package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;
public class JavaSelectionSort {
    static void selectionSort(int[]arr){
        int len = arr.length;
        /* Here 'i' represents the current index for the
        outer "for" loop and 'i' will run from 0 to (array length-2) */
        for(int i=0;i<(len-1);i++){
    /* find the max element in the array
    and swap it to the correct index */
            int last = (arr.length-i-1);
            int maxIndex = getMaxIndex(arr,0,last);
            swap(arr,maxIndex,last);
        }
    }
    private static int getMaxIndex(int arr[],int start,int end){
        int max = start;
        for(int i=start;i<=end;i++){
            if(arr[max]<arr[i])
                max = i;
        }
        return max;
    }
    public static void swap(int[]arr,int first,int second){
       int temp = arr[first];
       arr[first] = arr[second];
        arr[second] = temp;
    }

    public static void main(String[] args){
        Scanner in = new Scanner(System.in);
        System.out.println("Enter the array size: ");
        int size = in.nextInt();
        int arr[] = new int [size];
        System.out.println("Enter the array elements: ");
        for(int i=0;i<arr.length;i++){
         arr[i] = in.nextInt();
     }
        System.out.println("Array Before Sorting: \n"+Arrays.toString(arr));
        System.out.println("Array After Sorting: ");
        selectionSort(arr);
        System.out.println(Arrays.toString(arr));
    }
}
