# DSU (Disjoint Set Union) - Union-Find

## 📁 Folder Structure

```
GRAPHS/DSU/
├── theory/
│   ├── DisjointSetUnion.java    # Core DSU implementation
│   └── README.md                  # Complete theory & guide
│
└── problems/
    ├── RedundantConnection.java   # LeetCode #684
    ├── NumberOfProvinces.java     # LeetCode #547
    └── KruskalMST.java            # MST + 3 problems
```

---

## 🎯 Quick Start

### 1. Learn Theory First
```bash
cd GRAPHS/DSU/theory
java DisjointSetUnion.java
```
**Read:** `README.md` for complete DSU guide

### 2. Practice Problems
```bash
cd GRAPHS/DSU/problems
java RedundantConnection.java
java NumberOfProvinces.java
java KruskalMST.java
```

---

## 📚 What's Inside

### **theory/** - Core Implementation & Concepts
- **DisjointSetUnion.java** - Complete DSU with:
  - Path Compression
  - Union by Rank
  - Cycle detection
  - Valid tree checking
  - Time: O(α(n)) ≈ O(1)

- **README.md** - Complete guide with:
  - When to use DSU
  - DSU vs DFS/BFS
  - Company-specific tips
  - Practice checklist

### **problems/** - LeetCode Problems
- **RedundantConnection.java** (#684) - Cycle detection
- **NumberOfProvinces.java** (#547) - Connected components
- **KruskalMST.java** - Minimum Spanning Tree:
  - Basic Kruskal's algorithm
  - Min Cost to Connect Points (#1584)
  - Connecting Cities (#1135)
  - Critical Edges (#1489)

---

## 🎓 Study Path

1. **Read** `theory/README.md`
2. **Understand** `theory/DisjointSetUnion.java`
3. **Solve** `problems/RedundantConnection.java`
4. **Solve** `problems/NumberOfProvinces.java`
5. **Master** `problems/KruskalMST.java`

---

## ⚡ Key Concepts

**DSU in 3 Operations:**
- `find(x)` - Which component is x in?
- `union(x, y)` - Connect x and y
- `connected(x, y)` - Are x and y in same component?

**When to Use:**
- ✅ Dynamic connectivity
- ✅ Cycle detection (undirected)
- ✅ MST (Kruskal's)
- ❌ Directed graphs → use DFS/BFS

---

**Start with theory folder, then practice problems!** 🚀
