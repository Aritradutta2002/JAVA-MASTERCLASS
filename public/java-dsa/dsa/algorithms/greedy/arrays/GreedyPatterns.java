package dsa.algorithms.greedy.arrays;
/**
 * GREEDY ALGORITHM PATTERNS
 * 
 * Companies: Google, Facebook, Microsoft, Amazon
 * 
 * Greedy Strategy: Make locally optimal choice at each step
 * 
 * When to use Greedy:
 * 1. Greedy Choice Property: Local optimum leads to global optimum
 * 2. Optimal Substructure: Problem can be broken into subproblems
 * 
 * Warning: Greedy doesn't always work! Verify with examples.
 */

import java.util.*;

public class GreedyPatterns {
    
    /**
     * JUMP GAME I
     * LeetCode #55 - Medium
     * Can you reach the last index?
     */
    public boolean canJump(int[] nums) {
        int maxReach = 0;
        
        for (int i = 0; i < nums.length; i++) {
            if (i > maxReach) return false; // Can't reach this position
            maxReach = Math.max(maxReach, i + nums[i]);
            if (maxReach >= nums.length - 1) return true;
        }
        
        return true;
    }
    
    /**
     * JUMP GAME II
     * LeetCode #45 - Medium
     * Minimum jumps to reach last index
     */
    public int jump(int[] nums) {
        int jumps = 0, currentEnd = 0, farthest = 0;
        
        for (int i = 0; i < nums.length - 1; i++) {
            farthest = Math.max(farthest, i + nums[i]);
            
            if (i == currentEnd) {
                jumps++;
                currentEnd = farthest;
            }
        }
        
        return jumps;
    }
    
    /**
     * MEETING ROOMS II
     * LeetCode #253 - Medium
     * Minimum conference rooms needed
     */
    public int minMeetingRooms(int[][] intervals) {
        int[] starts = new int[intervals.length];
        int[] ends = new int[intervals.length];
        
        for (int i = 0; i < intervals.length; i++) {
            starts[i] = intervals[i][0];
            ends[i] = intervals[i][1];
        }
        
        Arrays.sort(starts);
        Arrays.sort(ends);
        
        int rooms = 0, endPtr = 0;
        
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] < ends[endPtr]) {
                rooms++;
            } else {
                endPtr++;
            }
        }
        
        return rooms;
    }
    
    /**
     * GAS STATION
     * LeetCode #134 - Medium
     * Find starting gas station index
     */
    public int canCompleteCircuit(int[] gas, int[] cost) {
        int totalGas = 0, totalCost = 0;
        int tank = 0, start = 0;
        
        for (int i = 0; i < gas.length; i++) {
            totalGas += gas[i];
            totalCost += cost[i];
            tank += gas[i] - cost[i];
            
            // If tank becomes negative, can't start from current 'start'
            if (tank < 0) {
                start = i + 1;
                tank = 0;
            }
        }
        
        return totalGas >= totalCost ? start : -1;
    }
    
    /**
     * PARTITION LABELS
     * LeetCode #763 - Medium
     * Partition string into as many parts as possible
     */
    public List<Integer> partitionLabels(String s) {
        int[] lastIndex = new int[26];
        
        // Record last occurrence of each character
        for (int i = 0; i < s.length(); i++) {
            lastIndex[s.charAt(i) - 'a'] = i;
        }
        
        List<Integer> result = new ArrayList<>();
        int start = 0, end = 0;
        
        for (int i = 0; i < s.length(); i++) {
            end = Math.max(end, lastIndex[s.charAt(i) - 'a']);
            
            if (i == end) {
                result.add(end - start + 1);
                start = i + 1;
            }
        }
        
        return result;
    }
    
    /**
     * TASK SCHEDULER
     * LeetCode #621 - Medium
     * Minimum time to complete all tasks with cooldown
     */
    public int leastInterval(char[] tasks, int n) {
        int[] freq = new int[26];
        for (char task : tasks) {
            freq[task - 'A']++;
        }
        
        Arrays.sort(freq);
        int maxFreq = freq[25];
        int idleSlots = (maxFreq - 1) * n;
        
        // Fill idle slots with other tasks
        for (int i = 24; i >= 0 && freq[i] > 0; i--) {
            idleSlots -= Math.min(freq[i], maxFreq - 1);
        }
        
        return idleSlots > 0 ? idleSlots + tasks.length : tasks.length;
    }
    
    /**
     * NON-OVERLAPPING INTERVALS
     * LeetCode #435 - Medium
     * Minimum intervals to remove to make non-overlapping
     */
    public int eraseOverlapIntervals(int[][] intervals) {
        if (intervals.length == 0) return 0;
        
        // Sort by end time
        Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
        
        int count = 0;
        int end = intervals[0][1];
        
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < end) {
                count++; // Overlap, remove this interval
            } else {
                end = intervals[i][1];
            }
        }
        
        return count;
    }
    
    /**
     * MINIMUM ARROWS TO BURST BALLOONS
     * LeetCode #452 - Medium
     */
    public int findMinArrowShots(int[][] points) {
        if (points.length == 0) return 0;
        
        Arrays.sort(points, (a, b) -> Integer.compare(a[1], b[1]));
        
        int arrows = 1;
        int end = points[0][1];
        
        for (int i = 1; i < points.length; i++) {
            if (points[i][0] > end) {
                arrows++;
                end = points[i][1];
            }
        }
        
        return arrows;
    }
    
    /**
     * BOATS TO SAVE PEOPLE
     * LeetCode #881 - Medium
     */
    public int numRescueBoats(int[] people, int limit) {
        Arrays.sort(people);
        int left = 0, right = people.length - 1;
        int boats = 0;
        
        while (left <= right) {
            if (people[left] + people[right] <= limit) {
                left++; // Pair lightest with heaviest
            }
            right--;
            boats++;
        }
        
        return boats;
    }
    
    /**
     * REMOVE K DIGITS
     * LeetCode #402 - Medium
     */
    public String removeKdigits(String num, int k) {
        if (k >= num.length()) return "0";
        
        Stack<Character> stack = new Stack<>();
        
        for (char digit : num.toCharArray()) {
            // Remove larger digits from stack
            while (!stack.isEmpty() && k > 0 && stack.peek() > digit) {
                stack.pop();
                k--;
            }
            stack.push(digit);
        }
        
        // Remove remaining k digits from end
        while (k > 0) {
            stack.pop();
            k--;
        }
        
        // Build result and remove leading zeros
        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
        }
        sb.reverse();
        
        // Remove leading zeros
        while (sb.length() > 1 && sb.charAt(0) == '0') {
            sb.deleteCharAt(0);
        }
        
        return sb.length() == 0 ? "0" : sb.toString();
    }
    
    public static void main(String[] args) {
        GreedyPatterns solution = new GreedyPatterns();
        
        // Test 1: Jump Game
        System.out.println("=== Jump Game ===");
        int[] nums1 = {2, 3, 1, 1, 4};
        System.out.println("Can jump: " + solution.canJump(nums1)); // true
        System.out.println("Min jumps: " + solution.jump(nums1)); // 2
        
        // Test 2: Meeting Rooms
        System.out.println("\n=== Meeting Rooms II ===");
        int[][] meetings = {{0, 30}, {5, 10}, {15, 20}};
        System.out.println("Min rooms: " + solution.minMeetingRooms(meetings)); // 2
        
        // Test 3: Gas Station
        System.out.println("\n=== Gas Station ===");
        int[] gas = {1, 2, 3, 4, 5};
        int[] cost = {3, 4, 5, 1, 2};
        System.out.println("Start index: " + solution.canCompleteCircuit(gas, cost)); // 3
        
        // Test 4: Partition Labels
        System.out.println("\n=== Partition Labels ===");
        System.out.println("Partitions: " + solution.partitionLabels("ababcbacadefegdehijhklij"));
        
        // Test 5: Task Scheduler
        System.out.println("\n=== Task Scheduler ===");
        char[] tasks = {'A', 'A', 'A', 'B', 'B', 'B'};
        System.out.println("Min time (n=2): " + solution.leastInterval(tasks, 2)); // 8
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Greedy works when: local optimum = global optimum");
        System.out.println("2. Common patterns: sorting, intervals, scheduling");
        System.out.println("3. Always verify with examples (greedy can fail)");
        System.out.println("4. Interval problems: sort by end time usually");
        System.out.println("5. Think: what's the locally best choice?");
        System.out.println("6. If greedy fails, try DP");
    }
}

