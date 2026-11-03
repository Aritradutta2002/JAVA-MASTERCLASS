package dsa.techniques.hashing;
import java.util.HashMap;
import java.util.*;

public class TwoSum {
    static public int[] twoSum(int[] nums, int target) {
            HashMap<Integer, Integer> map = new HashMap<>();


            for(int i = 0; i < nums.length; i++){
                int prefixSum = target - nums[i];
                if(map.containsKey(prefixSum)){
                    return new int []{map.get(prefixSum) , i};
                }
                map.put(nums[i] , i);
            }

            return new int[] {-1,-1};
        }
    public static void main(String[] args) {

        int[] nums = new int [6];
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the number of elements in the array: ");
        for (int i = 0; i < nums.length; i++) {
            nums[i] = sc.nextInt();
        }
        System.out.println("Enter your target element: ");
        int target = sc.nextInt();
        System.out.println(Arrays.toString(twoSum(nums, target)));
    }
}

