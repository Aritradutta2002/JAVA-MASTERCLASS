# Graph Problems - Level 4 (Organized by Difficulty)

This folder contains interview-ready graph problems organized by difficulty level. Each problem includes multiple solution approaches with detailed explanations and comprehensive test cases.

## 📁 Folder Structure

### Easy Problems
- **NumberOfIslands.java** - LeetCode 200: DFS/BFS to count connected components
- **CloneGraph.java** - LeetCode 133: Deep copy of graph using DFS/BFS
- **NumberOfProvinces.java** - LeetCode 547: Find connected components using DFS/BFS/Union-Find
- **ValidPath.java** - LeetCode 1971: Check if path exists using DFS/BFS/Union-Find
- **FloodFill.java** - LeetCode 733: Fill connected pixels using DFS/BFS

### Medium Problems
- **WordLadder.java** - LeetCode 127: Shortest transformation using BFS/Bidirectional BFS
- **CourseSchedule.java** - LeetCode 207: Cycle detection using DFS/BFS (Topological Sort)
- **CourseScheduleII.java** - LeetCode 210: Return topological order using Kahn's algorithm/DFS
- **NetworkDelayTime.java** - LeetCode 743: Single-source shortest path using Dijkstra/Bellman-Ford
- **RottingOranges.java** - LeetCode 994: Multi-source BFS simulation
- **PacificAtlanticWaterFlow.java** - LeetCode 417: Reverse thinking with DFS/BFS from borders
- **MinimumSpanningTreeTheory.java** - MST algorithms: Kruskal's, Prim's, Borůvka's with Union-Find

### Hard Problems
- **WordLadderII.java** - LeetCode 126: Find all shortest transformation paths using BFS + DFS
- **AlienDictionary.java** - LeetCode 269: Topological sort with cycle detection
- **CheapestFlightsWithinKStops.java** - LeetCode 787: Modified Dijkstra/Bellman-Ford with constraints

## 🔑 Key Concepts Covered

### Graph Traversal
- **DFS (Depth-First Search)**: Recursive and iterative implementations
- **BFS (Breadth-First Search)**: Level-by-level traversal, shortest path in unweighted graphs
- **Multi-source BFS**: Starting from multiple sources simultaneously

### Shortest Path Algorithms
- **Dijkstra's Algorithm**: Single-source shortest path for non-negative weights
- **Bellman-Ford Algorithm**: Single-source shortest path, handles negative weights
- **Floyd-Warshall Algorithm**: All-pairs shortest path
- **BFS for unweighted graphs**: Optimal for unweighted shortest path

### Topological Sorting
- **Kahn's Algorithm**: BFS-based topological sort using in-degrees
- **DFS-based**: Using finish times and cycle detection
- **Applications**: Course scheduling, dependency resolution

### Minimum Spanning Tree (MST)
- **Kruskal's Algorithm**: Edge-based greedy approach with Union-Find
- **Prim's Algorithm**: Vertex-based greedy approach with priority queue
- **Borůvka's Algorithm**: Parallel-friendly MST algorithm

### Advanced Techniques
- **Union-Find (Disjoint Set)**: Path compression and union by rank
- **Cycle Detection**: In directed and undirected graphs
- **Connected Components**: Finding and counting components
- **Bidirectional Search**: Meeting in the middle for optimization

### Problem-Solving Patterns
- **Grid Traversal**: 2D matrix problems using DFS/BFS
- **Graph Coloring**: 3-color approach for cycle detection
- **Reverse Thinking**: Starting from destination/borders
- **State Space Search**: Treating problem states as graph nodes

## 💡 Interview Tips

1. **Always clarify the graph representation**: Adjacency list vs matrix
2. **Consider edge cases**: Empty graph, single node, disconnected components
3. **Time/Space complexity analysis**: Be ready to explain Big O notation
4. **Multiple approaches**: Know both DFS and BFS solutions where applicable
5. **Edge weights**: Understand when to use different shortest path algorithms
6. **Cycle detection**: Essential for many graph problems
7. **Union-Find optimization**: Path compression and union by rank

## 🚀 Practice Strategy

### Easy Level (Master First)
- Focus on basic DFS/BFS traversal patterns
- Understand when to use each approach
- Practice grid-based problems

### Medium Level (Core Interview Topics)
- Master topological sorting
- Understand shortest path algorithms
- Learn MST algorithms thoroughly

### Hard Level (Advanced Concepts)
- Complex state space problems
- Multiple constraint optimization
- Advanced graph theory applications

## 📊 Time Complexities Quick Reference

| Algorithm | Time Complexity | Space Complexity | Use Case |
|-----------|----------------|------------------|----------|
| DFS | O(V + E) | O(V) | Cycle detection, pathfinding |
| BFS | O(V + E) | O(V) | Shortest path (unweighted) |
| Dijkstra | O((V + E) log V) | O(V) | Shortest path (non-negative weights) |
| Bellman-Ford | O(V × E) | O(V) | Shortest path (negative weights) |
| Kruskal's MST | O(E log E) | O(V) | Minimum spanning tree |
| Prim's MST | O(E log V) | O(V + E) | Minimum spanning tree |
| Union-Find | O(α(V)) | O(V) | Dynamic connectivity |

Happy coding and good luck with your interviews! 🎯