package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 997: Find the Town Judge
 * 
 * Problem: In a town, there are n people labeled from 1 to n. There is a rumor that one of these people is secretly
 * the town judge. If the town judge exists, then:
 * 1. The town judge trusts nobody.
 * 2. Everybody (except for the town judge) trusts the town judge.
 * 3. There is exactly one person that satisfies properties 1 and 2.
 * 
 * Time Complexity: O(E) where E is number of trust relationships
 * Space Complexity: O(n)
 */
public class FindTownJudge {
    
    /**
     * Optimized solution using in-degree and out-degree concept
     */
    public int findJudge(int n, int[][] trust) {
        // Count array: count[i] = in-degree - out-degree for person i
        int[] count = new int[n + 1];
        
        for (int[] relation : trust) {
            count[relation[0]]--; // person who trusts (out-degree)
            count[relation[1]]++; // person who is trusted (in-degree)
        }
        
        // Judge should have count = n-1 (trusted by all others, trusts nobody)
        for (int i = 1; i <= n; i++) {
            if (count[i] == n - 1) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * Alternative solution using separate arrays
     */
    public int findJudgeAlternative(int n, int[][] trust) {
        int[] trustCount = new int[n + 1]; // How many people trust person i
        int[] trusting = new int[n + 1];   // How many people person i trusts
        
        for (int[] relation : trust) {
            trusting[relation[0]]++;
            trustCount[relation[1]]++;
        }
        
        for (int i = 1; i <= n; i++) {
            // Judge trusts nobody and is trusted by everyone else
            if (trusting[i] == 0 && trustCount[i] == n - 1) {
                return i;
            }
        }
        
        return -1;
    }
    
    // Test method
    public static void main(String[] args) {
        FindTownJudge solution = new FindTownJudge();
        
        // Test case 1: n = 2, trust = [[1,2]]
        int[][] trust1 = {{1, 2}};
        System.out.println("Town judge: " + solution.findJudge(2, trust1)); // Output: 2
        
        // Test case 2: n = 3, trust = [[1,3],[2,3]]
        int[][] trust2 = {{1, 3}, {2, 3}};
        System.out.println("Town judge: " + solution.findJudge(3, trust2)); // Output: 3
        
        // Test case 3: n = 3, trust = [[1,3],[2,3],[3,1]]
        int[][] trust3 = {{1, 3}, {2, 3}, {3, 1}};
        System.out.println("Town judge: " + solution.findJudge(3, trust3)); // Output: -1
    }
}

