package JAVA_DSA.MixedCodes;
import javax.sound.midi.Soundbank;
import java.sql.SQLOutput;
import java.util.*;
import java.lang.*;
public class JavaInsertionSort {
    static void InsertionSort(int []arr)
    {
        int len  = arr.length;
        for(int i=0;i<(len-1);i++){
            for (int j=i+1;j>0;j--){
                if(arr[j]<arr[j-1]){
                    int temp;
                    temp = arr[j];
                    arr[j] = arr[j-1];
                    arr[j-1] = temp;
                }
            }
        }

    }
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the array size: ");
        int size = sc.nextInt();
        int arr[] = new int [size];
        System.out.println("Enter the array elements: ");
        for(int i=0;i< arr.length;i++){
            arr[i] = sc.nextInt();
        }
        System.out.println(Arrays.toString(arr));
        System.out.println("After Insertion Sort : ");
        InsertionSort(arr);
        System.out.println(Arrays.toString(arr));

    }
}
