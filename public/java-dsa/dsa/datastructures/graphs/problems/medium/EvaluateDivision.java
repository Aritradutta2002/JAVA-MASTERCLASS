package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 399: Evaluate Division
 * 
 * Problem: You are given an array of variable pairs equations and an array of real numbers values, 
 * where equations[i] = [Ai, Bi] and values[i] represent the equation Ai / Bi = values[i]. 
 * Each Ai or Bi is a string that represents a single variable.
 * You are also given some queries, where queries[j] = [Cj, Dj] represents the jth query 
 * where you must find the answer for Cj / Dj = ?.
 * 
 * Time Complexity: O(M * N) where M is queries, N is equations
 * Space Complexity: O(N)
 */
public class EvaluateDivision {
    
    /**
     * Graph + DFS solution
     */
    public double[] calcEquation(List<List<String>> equations, double[] values, List<List<String>> queries) {
        // Build graph with weights
        Map<String, Map<String, Double>> graph = new HashMap<>();
        
        // Build the graph
        for (int i = 0; i < equations.size(); i++) {
            String a = equations.get(i).get(0);
            String b = equations.get(i).get(1);
            double value = values[i];
            
            graph.putIfAbsent(a, new HashMap<>());
            graph.putIfAbsent(b, new HashMap<>());
            
            graph.get(a).put(b, value);      // a/b = value
            graph.get(b).put(a, 1.0 / value); // b/a = 1/value
        }
        
        double[] result = new double[queries.size()];
        
        for (int i = 0; i < queries.size(); i++) {
            String start = queries.get(i).get(0);
            String end = queries.get(i).get(1);
            
            if (!graph.containsKey(start) || !graph.containsKey(end)) {
                result[i] = -1.0;
            } else if (start.equals(end)) {
                result[i] = 1.0;
            } else {
                Set<String> visited = new HashSet<>();
                result[i] = dfs(graph, start, end, visited);
            }
        }
        
        return result;
    }
    
    private double dfs(Map<String, Map<String, Double>> graph, String start, String end, Set<String> visited) {
        if (visited.contains(start)) {
            return -1.0;
        }
        
        if (graph.get(start).containsKey(end)) {
            return graph.get(start).get(end);
        }
        
        visited.add(start);
        
        for (Map.Entry<String, Double> neighbor : graph.get(start).entrySet()) {
            String nextNode = neighbor.getKey();
            double weight = neighbor.getValue();
            
            double result = dfs(graph, nextNode, end, visited);
            if (result != -1.0) {
                return weight * result;
            }
        }
        
        visited.remove(start);
        return -1.0;
    }
    
    /**
     * Union-Find solution
     */
    public double[] calcEquationUnionFind(List<List<String>> equations, double[] values, List<List<String>> queries) {
        Map<String, String> parent = new HashMap<>();
        Map<String, Double> weight = new HashMap<>();
        
        // Initialize Union-Find
        for (List<String> equation : equations) {
            String a = equation.get(0);
            String b = equation.get(1);
            parent.putIfAbsent(a, a);
            parent.putIfAbsent(b, b);
            weight.putIfAbsent(a, 1.0);
            weight.putIfAbsent(b, 1.0);
        }
        
        // Union operations
        for (int i = 0; i < equations.size(); i++) {
            String a = equations.get(i).get(0);
            String b = equations.get(i).get(1);
            double value = values[i];
            union(parent, weight, a, b, value);
        }
        
        double[] result = new double[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            String a = queries.get(i).get(0);
            String b = queries.get(i).get(1);
            
            if (!parent.containsKey(a) || !parent.containsKey(b)) {
                result[i] = -1.0;
            } else {
                String rootA = find(parent, weight, a);
                String rootB = find(parent, weight, b);
                
                if (!rootA.equals(rootB)) {
                    result[i] = -1.0;
                } else {
                    result[i] = weight.get(a) / weight.get(b);
                }
            }
        }
        
        return result;
    }
    
    private String find(Map<String, String> parent, Map<String, Double> weight, String x) {
        if (!parent.get(x).equals(x)) {
            String originalParent = parent.get(x);
            String root = find(parent, weight, originalParent);
            weight.put(x, weight.get(x) * weight.get(originalParent));
            parent.put(x, root);
        }
        return parent.get(x);
    }
    
    private void union(Map<String, String> parent, Map<String, Double> weight, String a, String b, double value) {
        String rootA = find(parent, weight, a);
        String rootB = find(parent, weight, b);
        
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
            weight.put(rootA, weight.get(b) * value / weight.get(a));
        }
    }
    
    // Test method
    public static void main(String[] args) {
        EvaluateDivision solution = new EvaluateDivision();
        
        // Test case
        List<List<String>> equations = Arrays.asList(
            Arrays.asList("a", "b"),
            Arrays.asList("b", "c")
        );
        double[] values = {2.0, 3.0};
        List<List<String>> queries = Arrays.asList(
            Arrays.asList("a", "c"),
            Arrays.asList("b", "a"),
            Arrays.asList("a", "e"),
            Arrays.asList("a", "a"),
            Arrays.asList("x", "x")
        );
        
        double[] result = solution.calcEquation(equations, values, queries);
        System.out.println("Results: " + Arrays.toString(result)); // [6.0, 0.5, -1.0, 1.0, -1.0]
    }
}

