package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 2050: Parallel Courses III
 * 
 * Problem: You are given an integer n, which indicates that there are n courses labeled from 1 to n. 
 * You are also given a 2D integer array relations where relations[j] = [prevCoursej, nextCoursej] 
 * denotes that course prevCoursej has to be completed before course nextCoursej (prerequisite relationship).
 * Furthermore, you are given a 0-indexed integer array time where time[i] denotes how many months 
 * it takes to complete the (i+1)th course.
 * 
 * You must find the minimum number of months needed to complete all the courses following these rules:
 * - You may start taking a course at any time if the prerequisites are met.
 * - Any number of courses can be taken at the same time.
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V + E)
 */
public class ParallelCoursesIII {
    
    /**
     * Topological sort with dynamic programming
     */
    public int minimumTime(int n, int[][] relations, int[] time) {
        // Build graph and in-degree array
        List<List<Integer>> graph = new ArrayList<>();
        int[] indegree = new int[n + 1];
        
        for (int i = 0; i <= n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] relation : relations) {
            int prev = relation[0];
            int next = relation[1];
            graph.get(prev).add(next);
            indegree[next]++;
        }
        
        // dp[i] = minimum time to complete course i (including its time)
        int[] dp = new int[n + 1];
        Queue<Integer> queue = new LinkedList<>();
        
        // Initialize courses with no prerequisites
        for (int i = 1; i <= n; i++) {
            if (indegree[i] == 0) {
                dp[i] = time[i - 1]; // time array is 0-indexed
                queue.offer(i);
            }
        }
        
        while (!queue.isEmpty()) {
            int course = queue.poll();
            
            for (int nextCourse : graph.get(course)) {
                // Update the minimum time to complete nextCourse
                dp[nextCourse] = Math.max(dp[nextCourse], dp[course] + time[nextCourse - 1]);
                
                indegree[nextCourse]--;
                if (indegree[nextCourse] == 0) {
                    queue.offer(nextCourse);
                }
            }
        }
        
        // Return the maximum time among all courses
        int maxTime = 0;
        for (int i = 1; i <= n; i++) {
            maxTime = Math.max(maxTime, dp[i]);
        }
        
        return maxTime;
    }
    
    /**
     * DFS with memoization solution
     */
    public int minimumTimeDFS(int n, int[][] relations, int[] time) {
        // Build graph
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] relation : relations) {
            graph.get(relation[0]).add(relation[1]);
        }
        
        int[] memo = new int[n + 1];
        int maxTime = 0;
        
        for (int i = 1; i <= n; i++) {
            maxTime = Math.max(maxTime, dfs(graph, i, time, memo));
        }
        
        return maxTime;
    }
    
    private int dfs(List<List<Integer>> graph, int course, int[] time, int[] memo) {
        if (memo[course] != 0) {
            return memo[course];
        }
        
        int maxPrereqTime = 0;
        
        // Find the maximum time among all prerequisites
        for (int prereq : graph.get(course)) {
            maxPrereqTime = Math.max(maxPrereqTime, dfs(graph, prereq, time, memo));
        }
        
        memo[course] = maxPrereqTime + time[course - 1];
        return memo[course];
    }
    
    /**
     * Alternative topological sort implementation
     */
    public int minimumTimeAlternative(int n, int[][] relations, int[] time) {
        Map<Integer, List<Integer>> graph = new HashMap<>();
        Map<Integer, Integer> indegree = new HashMap<>();
        
        // Initialize
        for (int i = 1; i <= n; i++) {
            graph.put(i, new ArrayList<>());
            indegree.put(i, 0);
        }
        
        // Build graph
        for (int[] relation : relations) {
            graph.get(relation[0]).add(relation[1]);
            indegree.put(relation[1], indegree.get(relation[1]) + 1);
        }
        
        // Track completion times
        Map<Integer, Integer> completionTime = new HashMap<>();
        Queue<Integer> queue = new LinkedList<>();
        
        // Start with courses having no prerequisites
        for (int i = 1; i <= n; i++) {
            if (indegree.get(i) == 0) {
                completionTime.put(i, time[i - 1]);
                queue.offer(i);
            }
        }
        
        while (!queue.isEmpty()) {
            int course = queue.poll();
            
            for (int dependent : graph.get(course)) {
                // Update completion time for dependent course
                int newTime = completionTime.get(course) + time[dependent - 1];
                completionTime.put(dependent, Math.max(completionTime.getOrDefault(dependent, 0), newTime));
                
                indegree.put(dependent, indegree.get(dependent) - 1);
                if (indegree.get(dependent) == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        return Collections.max(completionTime.values());
    }
    
    // Test method
    public static void main(String[] args) {
        ParallelCoursesIII solution = new ParallelCoursesIII();
        
        // Test case 1: n = 3, relations = [[1,3],[2,3]], time = [3,2,5]
        int[][] relations1 = {{1,3},{2,3}};
        int[] time1 = {3,2,5};
        System.out.println("Minimum time (Topological): " + solution.minimumTime(3, relations1, time1)); // 8
        
        // Test case 2: n = 5, relations = [[1,5],[2,5],[3,5],[3,4],[4,5]], time = [1,2,3,4,5]
        int[][] relations2 = {{1,5},{2,5},{3,5},{3,4},{4,5}};
        int[] time2 = {1,2,3,4,5};
        System.out.println("Minimum time (DFS): " + solution.minimumTimeDFS(5, relations2, time2)); // 12
        
        System.out.println("Minimum time (Alternative): " + solution.minimumTimeAlternative(3, relations1, time1)); // 8
    }
}

