package dsa.techniques.hashing;
import java.lang.*;
import java.util.*;
public class DuplicateElement {
        public static int [] removeDuplicates(int [] nums)
        {

            int index = 0;
            int len = nums.length;
            for(int i=1; i<len; i++){
                if(nums[index] != nums[i]) {
                    index++;
                    nums[index] = nums[i];
                }

            }
           int [] temp = new int[index + 1];
            for(int i=0; i<temp.length; i++){
                temp[i] = nums[i];
            }

            return temp;
        }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int arr [] = new int [8];
        System.out.println("Enter Elements in Sorted order: ");
        for(int i=0; i< arr.length; i++){
            arr[i] = sc.nextInt();
        }
        System.out.println(Arrays.toString(removeDuplicates(arr)));
    }
}

