
package dsa.techniques.prefixsum.problems.easy;

import java.util.Arrays;

/**
 * LeetCode 1732: Find the Highest Altitude
 * Difficulty: Easy
 * 
 * Problem:
 * There is a biker going on a road trip. The road trip consists of n + 1 points at different altitudes.
 * The biker starts his trip on point 0 with altitude equal to 0.
 * You are given an integer array gain of length n where gain[i] is the net gain in altitude between points i and i + 1.
 * Return the highest altitude of a point.
 * 
 * Key Insight:
 * This problem is a direct application of prefix sums. The altitude at each point is the sum of all gains up to that point, starting from an initial altitude of 0.
 * We can maintain a running sum (currentAltitude) and track the maximum value this sum reaches.
 *
 * Example:
 * Input: gain = [-5, 1, 5, 0, -7]
 * Altitudes:
 * - Start at 0
 * - Point 1: 0 + (-5) = -5
 * - Point 2: -5 + 1 = -4
 * - Point 3: -4 + 5 = 1
 * - Point 4: 1 + 0 = 1
 * - Point 5: 1 + (-7) = -6
 * The sequence of altitudes is [0, -5, -4, 1, 1, -6]. The highest altitude is 1.
 * 
 * Time Complexity: O(n), as we iterate through the gain array once.
 * Space Complexity: O(1), as we only use a few variables to store the current and max altitudes.
 */
 
public class HighestAltitude_Leetcode1732 {
    
    /**
     * Calculates the largest altitude reached during the road trip.
     * @param gain An array representing the net gain in altitude between points.
     * @return The highest altitude reached.
     */
    public static int largestAltitude(int[] gain) {
        // The biker starts at altitude 0. This is our initial maximum.
        int currentAltitude = 0;
        int maxAltitude = 0;
        
        // Iterate through each gain in altitude.
        for (int g : gain) {
            // Update the current altitude by adding the gain.
            currentAltitude += g;
            
            // Check if the new altitude is higher than the max altitude found so far.
            if (currentAltitude > maxAltitude) {
                maxAltitude = currentAltitude;
            }
        }
        
        // Return the overall maximum altitude.
        return maxAltitude;
    }
    
    public static void main(String[] args) {
        // Test case 1: A mix of positive and negative gains.
        int[] gain1 = {-5, 1, 5, 0, -7};
        System.out.println("Gain: " + Arrays.toString(gain1));
        System.out.println("Highest Altitude: " + largestAltitude(gain1)); // Expected: 1
        System.out.println("---");
        
        // Test case 2: A sequence of gains that results in the highest altitude being the start (0).
        int[] gain2 = {-4, -3, -2, -1, 4, 3, 2};
        System.out.println("Gain: " + Arrays.toString(gain2));
        System.out.println("Highest Altitude: " + largestAltitude(gain2)); // Expected: 0
        System.out.println("---");

        // Test case 3: All positive gains.
        int[] gain3 = {1, 2, 3, 4};
        System.out.println("Gain: " + Arrays.toString(gain3));
        System.out.println("Highest Altitude: " + largestAltitude(gain3)); // Expected: 10
        System.out.println("---");
    }
}

