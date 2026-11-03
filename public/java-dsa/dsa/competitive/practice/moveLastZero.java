package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;
public class moveLastZero {
    static void moveZero(int arr[]){
        int len = arr.length;
        for(int i=0;i<len-1;i++){
            for(int j=0;j<len-i-1;j++){
                if(arr[j]==0 && arr[j+1] != 0 ){
                    int temp = arr[j];
                    arr[j] = arr[j+1];
                    arr[j+1] = temp;
                }
            }
        }
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int arr[] = new int [5];
        System.out.println("Enter the array Elements: ");
        for(int i=0;i<arr.length;i++){
            arr[i] = in.nextInt();
        }
        System.out.println("Arrays elements are: "+Arrays.toString(arr));
        moveZero(arr);
        System.out.println("After moving zeroes at last: "+Arrays.toString(arr));
    }
}
