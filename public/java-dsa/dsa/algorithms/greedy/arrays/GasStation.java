package dsa.algorithms.greedy.arrays;

/**
 * Gas Station (LeetCode 134) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook)
 * 
 * Problem: Find starting gas station to complete circuit
 * Time: O(n), Space: O(1)
 * 
 * Key Insight: If total gas >= total cost, solution exists
 */
public class GasStation {
    
    public int canCompleteCircuit(int[] gas, int[] cost) {
        int totalGas = 0;
        int totalCost = 0;
        int currentGas = 0;
        int start = 0;
        
        for (int i = 0; i < gas.length; i++) {
            totalGas += gas[i];
            totalCost += cost[i];
            currentGas += gas[i] - cost[i];
            
            // If we can't reach next station, start from next
            if (currentGas < 0) {
                start = i + 1;
                currentGas = 0;
            }
        }
        
        return totalGas >= totalCost ? start : -1;
    }
    
    // Alternative: Brute force (for understanding)
    public int canCompleteCircuitBruteForce(int[] gas, int[] cost) {
        int n = gas.length;
        
        for (int start = 0; start < n; start++) {
            int tank = 0;
            int i = start;
            int count = 0;
            
            while (count < n) {
                tank += gas[i] - cost[i];
                if (tank < 0) break;
                i = (i + 1) % n;
                count++;
            }
            
            if (count == n) return start;
        }
        
        return -1;
    }
    
    public static void main(String[] args) {
        GasStation solution = new GasStation();
        
        // Test Case 1
        int[] gas1 = {1, 2, 3, 4, 5};
        int[] cost1 = {3, 4, 5, 1, 2};
        System.out.println(solution.canCompleteCircuit(gas1, cost1)); // 3
        
        // Test Case 2
        int[] gas2 = {2, 3, 4};
        int[] cost2 = {3, 4, 3};
        System.out.println(solution.canCompleteCircuit(gas2, cost2)); // -1
    }
}

