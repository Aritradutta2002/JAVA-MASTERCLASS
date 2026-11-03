package dsa.techniques.hashing;

import java.util.*;
import java.lang.*;
public class MaxNumberOf_KSumPairs {
       static  public int maxOperations(int[] nums, int k) {
           int count = 0;
           HashMap<Integer, Integer> map = new HashMap<>();

           for(int i = 0; i < nums.length; i++) {
               int remaining = k - nums[i];
               if(map.containsKey(remaining) && map.get(remaining) > 0) {
                   count++;
                   map.put(remaining, map.get(remaining) - 1);
               } else {
                   map.put(nums[i], map.getOrDefault(nums[i], 0) + 1);
               }
           }

           return count;
       }

    public static void main(String[] args) {
        int [] nums = {3,1,3,4,3};
        int k = 4;
        System.out.println(maxOperations(nums, k));
    }
}

