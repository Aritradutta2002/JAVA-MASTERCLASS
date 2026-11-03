package dsa.techniques.hashing;
import java.util.*;
import java.lang.*;
public class RemoveElement {
    public static void removeElement(int[] nums, int val) {
        int len = nums.length;
        List <Integer> list = new ArrayList <>();
        for (int i=0; i<len; i++)
        {
            if(nums[i] != val)
                list.add(nums[i]);
        }

        System.out.print(list);

    }

    public static void main(String[] args) {
        int [] arr = {0,1,2,2,3,0,4,2};
        int val = 2;
        removeElement(arr,val);
    }
}

