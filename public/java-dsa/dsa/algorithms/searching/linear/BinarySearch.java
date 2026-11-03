package dsa.algorithms.searching.linear;
import java.util.*;
public class BinarySearch {
	public static void main(String[] args) {
		 Scanner sc = new Scanner(System.in);
	        System.out.println("Enter the size of the array: ");
	        int n = sc.nextInt();
	        int[] arr = new int[n];
	        System.out.println("Enter the elements of the array: ");
	        for (int i = 0; i < n; i++) {
	            arr[i] = sc.nextInt();
	        }
	        Arrays.sort(arr);
	        System.out.println("Enter the element to be searched: ");
	        int key = sc.nextInt();
	        System.out.println("Searched Element at Index : \n"+ binarySearch(arr, key));
	}
	
	
	public static int binarySearch(int[] arr, int target) {
        int low = 0;
        int high = arr.length - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (arr[mid] == target) {
                return mid;
            }
            else if (arr[mid] < target) {
                low = mid + 1;;
            }
            else{
                high = mid - 1;
            }
        }
        return -1;
    }
}

