package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;
public class JavaBinarySearch_Iterative {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int size;
        System.out.println("Enter the size of the array : ");
        size =sc.nextInt();
        int arr[] = new int [size];
        System.out.print("Enter the array elements : ");
        for(int i=0;i<size;i++){
            arr[i] = sc.nextInt();
        }

        //int length = arr.length;
        System.out.println("Enter the target : ");

        int target = sc.nextInt();
        System.out.println("The target element at : "+binarySearch(arr,target, size,0));
    }

    static int binarySearch(int arr[], int target,int high,int low) {
        int i;
        while (low <= high) {
            int mid = (low + high) / 2;

            if (arr[mid] == target)
                return mid;
            else if (arr[mid] < target)
                low = mid + 1;
            else
                high = mid - 1;
        }
        return -1;
    }

    }



