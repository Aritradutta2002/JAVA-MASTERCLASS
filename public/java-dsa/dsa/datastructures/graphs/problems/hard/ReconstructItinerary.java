package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode #332 - Reconstruct Itinerary
 * 
 * You are given a list of airline tickets where tickets[i] = [from_i, to_i] represents a departure and
 * arrival airport. Reconstruct the itinerary in order.
 * 
 * All of the tickets belong to a man who departs from "JFK". Thus, the itinerary must begin with "JFK".
 * 
 * If there are multiple valid itineraries, you should return the itinerary that has the smallest
 * lexical order when read as a single string.
 * 
 * Time Complexity: O(E log E)
 * Space Complexity: O(V + E)
 */
public class ReconstructItinerary {
    // Adjacency list: Map<From, MinHeap<To>>
    private Map<String, PriorityQueue<String>> adj = new HashMap<>();
    // The final itinerary, built in reverse
    private LinkedList<String> itinerary = new LinkedList<>();
    
    public List<String> findItinerary(List<List<String>> tickets) {
        // Build the graph
        for (List<String> ticket : tickets) {
            String from = ticket.get(0);
            String to = ticket.get(1);
            adj.putIfAbsent(from, new PriorityQueue<>());
            adj.get(from).add(to);
        }
        
        // Start DFS from "JFK"
        dfs("JFK");
        
        // The list is built in reverse, so just return it
        return itinerary;
    }
    
    private void dfs(String airport) {
        PriorityQueue<String> destinations = adj.get(airport);
        
        // Visit all neighbors in lexical order
        while (destinations != null && !destinations.isEmpty()) {
            // Poll() removes the edge so we don't use it again
            String next = destinations.poll();
            dfs(next);
        }
        
        // Add to the front of the list (post-order traversal)
        itinerary.addFirst(airport);
    }
}

