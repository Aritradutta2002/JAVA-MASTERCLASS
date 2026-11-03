package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 210: Course Schedule II
 * 
 * Problem: There are a total of numCourses courses you have to take, labeled from 0 to numCourses - 1.
 * You are given an array prerequisites where prerequisites[i] = [ai, bi] indicates that you must 
 * take course bi first if you want to take course ai.
 * 
 * Return the ordering of courses you should take to finish all courses. If there are many valid 
 * answers, return any of them. If it is impossible to finish all courses, return an empty array.
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V + E)
 */
public class CourseScheduleII {
    
    /**
     * Kahn's Algorithm (BFS-based Topological Sort)
     */
    public int[] findOrder(int numCourses, int[][] prerequisites) {
        // Build adjacency list and calculate in-degrees
        List<List<Integer>> graph = new ArrayList<>();
        int[] inDegree = new int[numCourses];
        
        for (int i = 0; i < numCourses; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] prereq : prerequisites) {
            graph.get(prereq[1]).add(prereq[0]);
            inDegree[prereq[0]]++;
        }
        
        // Start with courses that have no prerequisites
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < numCourses; i++) {
            if (inDegree[i] == 0) {
                queue.offer(i);
            }
        }
        
        int[] result = new int[numCourses];
        int index = 0;
        
        while (!queue.isEmpty()) {
            int course = queue.poll();
            result[index++] = course;
            
            // Remove this course and update in-degrees
            for (int nextCourse : graph.get(course)) {
                inDegree[nextCourse]--;
                if (inDegree[nextCourse] == 0) {
                    queue.offer(nextCourse);
                }
            }
        }
        
        // If we couldn't process all courses, there's a cycle
        return index == numCourses ? result : new int[0];
    }
    
    /**
     * DFS-based Topological Sort
     */
    public int[] findOrderDFS(int numCourses, int[][] prerequisites) {
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < numCourses; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] prereq : prerequisites) {
            graph.get(prereq[1]).add(prereq[0]);
        }
        
        int[] colors = new int[numCourses]; // 0: white, 1: gray, 2: black
        Stack<Integer> stack = new Stack<>();
        
        // Try DFS from each unvisited node
        for (int i = 0; i < numCourses; i++) {
            if (colors[i] == 0 && hasCycle(graph, colors, i, stack)) {
                return new int[0]; // Cycle detected
            }
        }
        
        // Convert stack to array (reverse order)
        int[] result = new int[numCourses];
        for (int i = 0; i < numCourses; i++) {
            result[i] = stack.pop();
        }
        
        return result;
    }
    
    private boolean hasCycle(List<List<Integer>> graph, int[] colors, int course, Stack<Integer> stack) {
        colors[course] = 1; // Mark as gray (processing)
        
        for (int neighbor : graph.get(course)) {
            if (colors[neighbor] == 1) { // Back edge - cycle detected
                return true;
            }
            if (colors[neighbor] == 0 && hasCycle(graph, colors, neighbor, stack)) {
                return true;
            }
        }
        
        colors[course] = 2; // Mark as black (processed)
        stack.push(course); // Add to result
        return false;
    }
    
    /**
     * Alternative DFS with explicit visited and recursion stack tracking
     */
    public int[] findOrderDFSAlternate(int numCourses, int[][] prerequisites) {
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < numCourses; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] prereq : prerequisites) {
            graph.get(prereq[1]).add(prereq[0]);
        }
        
        boolean[] visited = new boolean[numCourses];
        boolean[] recStack = new boolean[numCourses];
        List<Integer> result = new ArrayList<>();
        
        for (int i = 0; i < numCourses; i++) {
            if (!visited[i] && hasCycleDFS(graph, visited, recStack, i, result)) {
                return new int[0];
            }
        }
        
        // Reverse the result
        Collections.reverse(result);
        return result.stream().mapToInt(i -> i).toArray();
    }
    
    private boolean hasCycleDFS(List<List<Integer>> graph, boolean[] visited, 
                               boolean[] recStack, int course, List<Integer> result) {
        visited[course] = true;
        recStack[course] = true;
        
        for (int neighbor : graph.get(course)) {
            if (!visited[neighbor] && hasCycleDFS(graph, visited, recStack, neighbor, result)) {
                return true;
            } else if (recStack[neighbor]) {
                return true;
            }
        }
        
        recStack[course] = false;
        result.add(course);
        return false;
    }
    
    /**
     * Optimized BFS with early termination
     */
    public int[] findOrderOptimized(int numCourses, int[][] prerequisites) {
        if (prerequisites.length == 0) {
            int[] result = new int[numCourses];
            for (int i = 0; i < numCourses; i++) {
                result[i] = i;
            }
            return result;
        }
        
        List<Set<Integer>> graph = new ArrayList<>();
        int[] inDegree = new int[numCourses];
        
        for (int i = 0; i < numCourses; i++) {
            graph.add(new HashSet<>());
        }
        
        for (int[] prereq : prerequisites) {
            if (graph.get(prereq[1]).add(prereq[0])) {
                inDegree[prereq[0]]++;
            }
        }
        
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < numCourses; i++) {
            if (inDegree[i] == 0) {
                queue.offer(i);
            }
        }
        
        int[] result = new int[numCourses];
        int index = 0;
        
        while (!queue.isEmpty()) {
            int course = queue.poll();
            result[index++] = course;
            
            for (int nextCourse : graph.get(course)) {
                inDegree[nextCourse]--;
                if (inDegree[nextCourse] == 0) {
                    queue.offer(nextCourse);
                }
            }
        }
        
        return index == numCourses ? result : new int[0];
    }
    
    // Test method
    public static void main(String[] args) {
        CourseScheduleII solution = new CourseScheduleII();
        
        // Test case 1
        int numCourses1 = 2;
        int[][] prerequisites1 = {{1, 0}};
        
        System.out.println("Test 1 - BFS: " + Arrays.toString(solution.findOrder(numCourses1, prerequisites1))); // [0, 1]
        System.out.println("Test 1 - DFS: " + Arrays.toString(solution.findOrderDFS(numCourses1, prerequisites1))); // [0, 1]
        
        // Test case 2
        int numCourses2 = 4;
        int[][] prerequisites2 = {{1,0},{2,0},{3,1},{3,2}};
        
        System.out.println("Test 2 - BFS: " + Arrays.toString(solution.findOrder(numCourses2, prerequisites2))); // [0,1,2,3] or [0,2,1,3]
        
        // Test case 3 - Cycle
        int numCourses3 = 2;
        int[][] prerequisites3 = {{1,0},{0,1}};
        
        System.out.println("Test 3 - BFS: " + Arrays.toString(solution.findOrder(numCourses3, prerequisites3))); // []
    }
}

