package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 815: Bus Routes
 * 
 * Problem: You are given an array routes representing bus routes where routes[i] is a bus route that the ith bus 
 * repeats forever. For example, if routes[0] = [1, 5, 7], this means that the 0th bus travels in the sequence 
 * 1 -> 5 -> 7 -> 1 -> 5 -> 7 -> 1 -> ... forever.
 * You will start at the bus stop source (You are not on any bus initially), and you want to go to the bus stop target. 
 * You can travel between bus stops by buses only.
 * Return the least number of buses you must take to travel from source to target. Return -1 if it is not possible.
 * 
 * Time Complexity: O(N^2 + S) where N is number of routes, S is total number of stops
 * Space Complexity: O(N^2 + S)
 */
public class BusRoutes {
    
    /**
     * BFS solution - treat routes as nodes in graph
     */
    public int numBusesToDestination(int[][] routes, int source, int target) {
        if (source == target) {
            return 0;
        }
        
        int n = routes.length;
        
        // Build graph: routes[i] connects to routes[j] if they share at least one stop
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        // Build connections between routes
        for (int i = 0; i < n; i++) {
            Set<Integer> stopsI = new HashSet<>();
            for (int stop : routes[i]) {
                stopsI.add(stop);
            }
            
            for (int j = i + 1; j < n; j++) {
                // Check if routes i and j share any stop
                for (int stop : routes[j]) {
                    if (stopsI.contains(stop)) {
                        graph.get(i).add(j);
                        graph.get(j).add(i);
                        break;
                    }
                }
            }
        }
        
        // Find routes that contain source and target
        Set<Integer> sourceRoutes = new HashSet<>();
        Set<Integer> targetRoutes = new HashSet<>();
        
        for (int i = 0; i < n; i++) {
            for (int stop : routes[i]) {
                if (stop == source) {
                    sourceRoutes.add(i);
                }
                if (stop == target) {
                    targetRoutes.add(i);
                }
            }
        }
        
        // BFS from source routes to target routes
        Queue<Integer> queue = new LinkedList<>();
        boolean[] visited = new boolean[n];
        
        for (int route : sourceRoutes) {
            queue.offer(route);
            visited[route] = true;
        }
        
        int buses = 1;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int currentRoute = queue.poll();
                
                if (targetRoutes.contains(currentRoute)) {
                    return buses;
                }
                
                for (int nextRoute : graph.get(currentRoute)) {
                    if (!visited[nextRoute]) {
                        visited[nextRoute] = true;
                        queue.offer(nextRoute);
                    }
                }
            }
            
            buses++;
        }
        
        return -1;
    }
    
    /**
     * Optimized BFS solution using stop-to-routes mapping
     */
    public int numBusesToDestinationOptimized(int[][] routes, int source, int target) {
        if (source == target) {
            return 0;
        }
        
        // Map each stop to the routes that contain it
        Map<Integer, List<Integer>> stopToRoutes = new HashMap<>();
        
        for (int i = 0; i < routes.length; i++) {
            for (int stop : routes[i]) {
                stopToRoutes.computeIfAbsent(stop, k -> new ArrayList<>()).add(i);
            }
        }
        
        // BFS on stops
        Queue<Integer> queue = new LinkedList<>();
        Set<Integer> visitedStops = new HashSet<>();
        Set<Integer> visitedRoutes = new HashSet<>();
        
        queue.offer(source);
        visitedStops.add(source);
        
        int buses = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int currentStop = queue.poll();
                
                if (currentStop == target) {
                    return buses;
                }
                
                // Explore all routes that contain current stop
                for (int route : stopToRoutes.getOrDefault(currentStop, new ArrayList<>())) {
                    if (visitedRoutes.contains(route)) {
                        continue;
                    }
                    
                    visitedRoutes.add(route);
                    
                    // Add all stops of this route to queue
                    for (int stop : routes[route]) {
                        if (!visitedStops.contains(stop)) {
                            visitedStops.add(stop);
                            queue.offer(stop);
                        }
                    }
                }
            }
            
            buses++;
        }
        
        return -1;
    }
    
    /**
     * Alternative BFS implementation
     */
    public int numBusesToDestinationAlternative(int[][] routes, int source, int target) {
        if (source == target) return 0;
        
        Map<Integer, Set<Integer>> stopToRoutes = new HashMap<>();
        
        // Build stop to routes mapping
        for (int i = 0; i < routes.length; i++) {
            for (int stop : routes[i]) {
                stopToRoutes.computeIfAbsent(stop, k -> new HashSet<>()).add(i);
            }
        }
        
        Queue<Integer> queue = new LinkedList<>();
        Set<Integer> visitedRoutes = new HashSet<>();
        
        // Start with all routes that contain source
        for (int route : stopToRoutes.getOrDefault(source, new HashSet<>())) {
            queue.offer(route);
            visitedRoutes.add(route);
        }
        
        int level = 1;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int currentRoute = queue.poll();
                
                // Check if current route contains target
                for (int stop : routes[currentRoute]) {
                    if (stop == target) {
                        return level;
                    }
                }
                
                // Explore connected routes
                for (int stop : routes[currentRoute]) {
                    for (int nextRoute : stopToRoutes.getOrDefault(stop, new HashSet<>())) {
                        if (!visitedRoutes.contains(nextRoute)) {
                            visitedRoutes.add(nextRoute);
                            queue.offer(nextRoute);
                        }
                    }
                }
            }
            
            level++;
        }
        
        return -1;
    }
    
    // Test method
    public static void main(String[] args) {
        BusRoutes solution = new BusRoutes();
        
        // Test case 1: routes = [[1,2,7],[3,6,7]], source = 1, target = 6
        int[][] routes1 = {{1,2,7},{3,6,7}};
        System.out.println("Min buses: " + solution.numBusesToDestination(routes1, 1, 6)); // 2
        
        // Test case 2: routes = [[7,12],[4,5,15],[6],[15,19],[9,12,13]], source = 15, target = 12
        int[][] routes2 = {{7,12},{4,5,15},{6},{15,19},{9,12,13}};
        System.out.println("Min buses (Optimized): " + solution.numBusesToDestinationOptimized(routes2, 15, 12)); // -1
        
        System.out.println("Min buses (Alternative): " + solution.numBusesToDestinationAlternative(routes1, 1, 6)); // 2
    }
}

