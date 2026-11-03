package dsa.techniques.hashing;
import java.util.*;
/*
 *   Author  : Aritra Dutta
 *   Created : Saturday, 08.02.2025  10:51 pm
 */
public class SecondHighestOccurringElement {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = sc.nextInt();
        }

        System.out.println("Second Highest Occurring Element is " + secondMostFrequentElement(arr));
    }

    static public int secondMostFrequentElement(int[] nums) {
        int largestFrequency = Integer.MIN_VALUE;
        int secondLargestFrequency = Integer.MIN_VALUE;
        int elementWithSecondLargestFrequency = -1;

        HashMap < Integer, Integer > map = new HashMap < > ();
        for (int i = 0; i < nums.length; i++) {
            map.put(nums[i], map.getOrDefault(nums[i], 0) + 1);
        }

        for (Map.Entry < Integer, Integer > entry: map.entrySet()) {
            largestFrequency = Math.max(largestFrequency, entry.getValue());
        }

        for (Map.Entry < Integer, Integer > entry: map.entrySet()) {
            if (entry.getValue() != largestFrequency) {
                if (entry.getValue() > secondLargestFrequency) {
                    secondLargestFrequency = entry.getValue();
                    elementWithSecondLargestFrequency = entry.getKey();
                }
            }
        }

        return elementWithSecondLargestFrequency;

    }
}

