package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;
public class JavaBubbleSort {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int arr[] = new int[6];
       System.out.print("Enter the array elements : ");

       for(int i=0;i<6;i++){
            arr[i] = sc.nextInt();
       }

        bubbleSort(arr);
        System.out.println(Arrays.toString(arr));
        Arrays.toString(arr);

    }
    static void bubbleSort(int[] arr){
        for(int i=1;i<=arr.length-1;i++)
        {    // Outer 'for' loop is for comparing a[i] and a[i+1] element
            for(int j=1;j<=arr.length-i;j++)
            {
                if(arr[j]<arr[j-1])
                {
                    int temp = arr[j];
                    arr[j] = arr[j-1];
                    arr[j-1] = temp;
                }
            }
        }

    }
}
