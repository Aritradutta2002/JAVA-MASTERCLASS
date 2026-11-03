# 📊 Graph Algorithms - Interview Ready Collection

> **Comprehensive graph algorithms library with clear, intuitive organization**

Welcome to the most well-organized graph algorithms collection! Each algorithm includes multiple implementations (normal + optimized), detailed explanations, and working examples.

---

## 📂 Folder Structure

```
GRAPHS/
│
├── 01_Traversal/              ← Graph Traversal Algorithms
│   ├── DFSRecursive.java      (6 different DFS approaches)
│   ├── DFSIterative.java      (Iterative DFS with stack)
│   ├── BFS.java               (5 BFS variants)
│   └── BFS_IMPROVED.java      (Optimized BFS)
│
├── 02_ShortestPath/           ← Shortest Path Algorithms
│   ├── Dijkstra.java          (5 implementations ⭐)
│   ├── BellmanFord.java       (Handles negative weights)
│   ├── FloydWarshall.java     (All-pairs, 5 approaches ⭐)
│   ├── DAGShortestPath.java   (For DAGs, O(V+E))
│   ├── UnweightedShortestPath.java
│   └── ZeroOneBFS.java        (0-1 BFS optimization)
│
├── 03_MST/                    ← Minimum Spanning Tree
│   ├── KruskalMST.java        (5+ approaches ⭐)
│   └── PrimMST.java           (6 implementations ⭐)
│
├── 04_Cycles/                 ← Cycle Detection
│   ├── CycleDetectionDirected.java
│   └── CycleDetectionUndirected.java
│
├── 05_Topology/               ← Topological Sort & DAG
│   ├── TopologicalSort.java   (DFS + Kahn's algorithm)
│   └── DAGShortestPath.java
│
├── 06_Components/             ← Connected Components & Critical Elements
│   ├── ConnectedComponents.java
│   ├── BipartiteCheck.java
│   ├── KosarajuSCC.java       (Strongly Connected Components)
│   ├── TarjanSCC.java
│   ├── BridgesArticulationPoints.java
│   ├── TarjanBridgesArticulationPoints.java ⭐
│   └── EulerianPathCircuit.java
│
├── 07_Utils/                  ← Helper Classes & Data Structures
│   ├── Graph.java             (Flexible graph representation)
│   ├── UnionFind.java         (DSU with optimizations ⭐)
│   └── Pair.java
│
├── 08_Problems/               ← LeetCode/Interview Problems
│   ├── Easy/                  (15+ problems)
│   ├── Medium/                (18+ problems)
│   └── Hard/                  (27+ problems)
│
└── docs/                      ← Documentation
    ├── ALGORITHM_INDEX.md     (Quick reference guide ⭐)
    ├── ALGORITHM_COMPARISON.md
    ├── README.md
    └── graph_interview_handbook_final.pdf
```

⭐ = Enhanced with multiple implementations and detailed explanations

---

## 🎯 Quick Find

### By Problem Type

| What You Need | Go To | Algorithm |
|--------------|-------|-----------|
| **Shortest path** (unweighted) | `01_Traversal/` | BFS.java |
| **Shortest path** (weighted, non-negative) | `02_ShortestPath/` | Dijkstra.java |
| **Shortest path** (negative weights OK) | `02_ShortestPath/` | BellmanFord.java |
| **All-pairs shortest path** | `02_ShortestPath/` | FloydWarshall.java |
| **Minimum spanning tree** | `03_MST/` | KruskalMST.java or PrimMST.java |
| **Cycle detection** | `04_Cycles/` | CycleDetection*.java |
| **Task scheduling** | `05_Topology/` | TopologicalSort.java |
| **2-coloring problem** | `06_Components/` | BipartiteCheck.java |
| **Critical edges/vertices** | `06_Components/` | TarjanBridgesArticulationPoints.java |
| **Dynamic connectivity** | `07_Utils/` | UnionFind.java |

### By Company (MAANG)

#### 🔵 Amazon
- Clone Graph → `08_Problems/Medium/`
- Number of Provinces → `08_Problems/Medium/` (use UnionFind)
- Course Schedule → `08_Problems/Medium/` (use TopologicalSort)
- Network Delay Time → `08_Problems/Medium/` (use Dijkstra)

#### 🔴 Google
- Critical Connections → `08_Problems/Hard/` (use Tarjan)
- Number of Provinces → `08_Problems/Medium/`
- Course Schedule II → `08_Problems/Medium/`

#### 🟢 Meta (Facebook)
- Clone Graph → `08_Problems/Medium/`
- Course Schedule → `08_Problems/Medium/`
- Is Graph Bipartite → `08_Problems/Medium/`

#### 🔵 Microsoft
- Clone Graph → `08_Problems/Medium/`
- Number of Provinces → `08_Problems/Medium/`
- Critical Connections → `08_Problems/Hard/`

---

## 📚 Learning Path

### **Level 1: Beginner** (Start Here!)
1. **Traversal Basics**
   - `01_Traversal/DFSRecursive.java` - Start with this!
   - `01_Traversal/BFS.java` - Then learn BFS
   
2. **Basic Applications**
   - `04_Cycles/CycleDetectionUndirected.java`
   - `06_Components/ConnectedComponents.java`

### **Level 2: Intermediate**
3. **Advanced Traversal**
   - `05_Topology/TopologicalSort.java`
   - `06_Components/BipartiteCheck.java`
   
4. **Shortest Paths**
   - `02_ShortestPath/Dijkstra.java`
   - `02_ShortestPath/BellmanFord.java`
   
5. **Data Structures**
   - `07_Utils/UnionFind.java`

### **Level 3: Advanced**
6. **MST Algorithms**
   - `03_MST/KruskalMST.java`
   - `03_MST/PrimMST.java`
   
7. **All-Pairs Shortest Path**
   - `02_ShortestPath/FloydWarshall.java`
   
8. **Advanced Components**
   - `06_Components/TarjanBridgesArticulationPoints.java`
   - `06_Components/KosarajuSCC.java`

---

## 🌟 Key Features

### ✅ Multiple Implementations
Every major algorithm includes:
- **Normal version** - Easy to understand
- **Optimized version** - Interview-ready
- **Variants** - For different use cases

### ✅ Comprehensive Documentation
Each file contains:
- Clear algorithm explanation
- **When to use** this algorithm
- **Time & space complexity** analysis
- **Step-by-step** breakdown
- **Real-world applications**
- **Comparison** with alternatives

### ✅ Working Examples
- Comprehensive `main()` methods
- Multiple test cases
- Edge cases covered
- Visual examples in comments

### ✅ Production Quality
- No linting errors
- Clean code
- Proper error handling
- Well-structured

---

## 🚀 How to Use

### **For Learning**
1. Navigate to the category folder
2. Open the relevant Java file
3. Read the documentation at the top
4. Study the implementations
5. Run the `main()` method to see it work

### **For Interviews**
1. Check `docs/ALGORITHM_INDEX.md` for quick lookup
2. Find the problem type
3. Go to the suggested file
4. Review the optimized approach
5. Understand time/space complexity

### **For Problem Solving**
1. Identify the problem category
2. Navigate to appropriate folder
3. Find similar algorithm
4. Adapt the template code
5. Test with examples

---

## 📖 Documentation

- **[ALGORITHM_INDEX.md](docs/ALGORITHM_INDEX.md)** - Complete algorithm reference
- **[ALGORITHM_COMPARISON.md](docs/ALGORITHM_COMPARISON.md)** - When to use what
- **[graph_interview_handbook_final.pdf](docs/graph_interview_handbook_final.pdf)** - Comprehensive guide

---

## 🎓 Algorithm Cheat Sheet

### Time Complexity Summary

| Algorithm | Time | Space | Best For |
|-----------|------|-------|----------|
| DFS | O(V + E) | O(V) | Traversal, cycles |
| BFS | O(V + E) | O(V) | Shortest path (unweighted) |
| Dijkstra | O((V+E) log V) | O(V) | Shortest path (weighted) |
| Bellman-Ford | O(V*E) | O(V) | Negative weights |
| Floyd-Warshall | O(V³) | O(V²) | All-pairs shortest path |
| Kruskal | O(E log E) | O(V) | MST (sparse graphs) |
| Prim | O((V+E) log V) | O(V) | MST (dense graphs) |
| Topological Sort | O(V + E) | O(V) | Task scheduling |
| Union-Find | O(α(n)) | O(n) | Dynamic connectivity |
| Tarjan | O(V + E) | O(V) | Bridges, SCC |

---

## 🔥 Common Patterns

### **Pattern 1: Shortest Path**
```
Unweighted          → BFS
Weighted (non-neg)  → Dijkstra
Weighted (neg OK)   → Bellman-Ford
All pairs           → Floyd-Warshall
```

### **Pattern 2: MST**
```
Sparse graph  → Kruskal
Dense graph   → Prim
```

### **Pattern 3: Cycle Detection**
```
Undirected  → DFS with parent OR Union-Find
Directed    → DFS with recursion stack
```

### **Pattern 4: Connectivity**
```
Check connected    → DFS/BFS
Dynamic            → Union-Find
Count components   → DFS/BFS or Union-Find
```

---

## 💡 Pro Tips

### **Choosing Algorithms**
1. **Graph type** - Directed vs Undirected
2. **Edge weights** - Weighted vs Unweighted, Negative?
3. **Graph density** - Sparse (E ≈ V) vs Dense (E ≈ V²)
4. **Query type** - Single-source vs All-pairs

### **Interview Strategy**
1. Clarify graph properties (directed? weighted?)
2. Identify the problem pattern
3. State time/space complexity
4. Consider edge cases
5. Start with simple solution, then optimize

### **Practice Order**
1. Master DFS and BFS first
2. Learn shortest path algorithms
3. Understand Union-Find
4. Practice with real problems
5. Learn advanced algorithms (Tarjan, Floyd-Warshall)

---

## 🎯 Why This Structure is Better

### ❌ Old Structure
```
level1_basics/           - What's "basic"?
level2_shortest_paths/   - Why "level 2"?
level3_mst_and_advanced/ - Too vague
level4_problems/         - What's "level 4"?
```

### ✅ New Structure
```
01_Traversal/      - Instantly clear: DFS, BFS algorithms
02_ShortestPath/   - All shortest path algorithms here
03_MST/            - MST algorithms, period
04_Cycles/         - Cycle detection, obvious
05_Topology/       - Topological sort and DAG
06_Components/     - Components and critical elements
07_Utils/          - Helper classes
08_Problems/       - Interview problems
```

**Benefits:**
- 🎯 Find algorithms in seconds
- 📚 No more confusion about levels
- 🚀 Interview-friendly organization
- 💼 Professional structure
- 🔍 Easy to maintain and extend

---

## 📊 Statistics

- **Total Algorithms:** 19+ core algorithms
- **Total Implementations:** 50+ different approaches
- **Lines of Code:** 10,000+ lines
- **Problem Solutions:** 60+ LeetCode problems
- **Documentation:** 1,500+ lines of docs

---

## 🤝 Contributing

Want to add more algorithms? Here's where they go:
- **Traversal algorithm** → `01_Traversal/`
- **Shortest path variant** → `02_ShortestPath/`
- **MST algorithm** → `03_MST/`
- **Cycle detection variant** → `04_Cycles/`
- **Topological/DAG algorithm** → `05_Topology/`
- **Component/SCC algorithm** → `06_Components/`
- **Helper class** → `07_Utils/`
- **Problem solution** → `08_Problems/`

---

## 📝 Notes

- All implementations are tested and working
- Package names use underscore prefix for proper ordering: `_01_Traversal`, `_02_ShortestPath`, etc.
- Imports are updated to reflect new structure
- Documentation is kept up-to-date

---

## ✨ Getting Started

```bash
# Navigate to GRAPHS folder
cd GRAPHS

# Start with basic traversal
# Read: 01_Traversal/DFSRecursive.java

# Then learn BFS
# Read: 01_Traversal/BFS.java

# Quick reference
# Read: docs/ALGORITHM_INDEX.md
```

---

**Ready for MAANG interviews! 🚀**

*Last updated: After restructuring*

