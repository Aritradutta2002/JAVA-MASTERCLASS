# 🚀 Dynamic Programming - Complete Guide

Welcome to the most comprehensive Dynamic Programming collection! This folder contains **40+ problems** covering all major DP patterns with detailed explanations, multiple approaches, and beginner-friendly implementations.

## 📚 **Structure Overview**

```
DYNAMIC_PROGRAMMING/
├── 📖 docs/                     # Documentation and guides
├── ☕ java/                     # Java implementations (40+ problems)
│   ├── basics/                  # Fundamental DP concepts
│   ├── one_dimensional/         # 1D DP problems
│   ├── two_dimensional/         # 2D DP problems  
│   ├── knapsack/               # Knapsack variants
│   ├── subsequence_problems/    # LIS, LCS, etc.
│   ├── string_dp/              # String-based DP
│   ├── stock_problems/         # Stock trading
│   └── classic_problems/       # MCM, Digit DP, etc.
└── 🔧 cpp/                     # C++ implementations (80+ problems)
```

## 🎯 **Problem Coverage by Pattern**

### 🏗️ **Basic DP Patterns (5 problems)**
- **Fibonacci Series** - Multiple implementations
- **Factorial Problems** - With optimizations
- **Basic Recursion** - Foundation concepts

### 📈 **1D DP (8 problems)**
- **Climbing Stairs** - Classic introductory problem
- **House Robber I & II** - Linear and circular arrays
- **Coin Change** - Minimum coins and number of ways
- **Coin Change 2** - Unlimited coin combinations

### 🏢 **2D DP / Grid Problems (4 problems)**
- **Unique Paths** - Basic grid traversal
- **Unique Paths II** - With obstacles
- **Minimum Path Sum** - Weighted grid traversal
- **Maximal Rectangle** - Complex grid optimization

### 🎒 **Knapsack Problems (4 problems)**
- **0/1 Knapsack** - Classic optimization
- **Partition Equal Subset Sum** - Subset problems
- **Target Sum** - Assignment problems  
- **Partition K Equal Sum Subsets** - Advanced partitioning

### 📝 **Subsequence Problems (7 problems)**
- **Longest Increasing Subsequence** - O(n²) and O(n log n)
- **Longest Common Subsequence** - Classic LCS
- **Shortest Common Supersequence** - LCS extension
- **Maximum Length Repeated Subarray** - Substring variant
- **Longest Fibonacci Subsequence** - Specialized LIS
- **Advanced LIS** - Count, print, variants

### 🔤 **String DP (3 problems)**
- **Edit Distance** - Levenshtein distance
- **Longest Palindromic Substring** - Palindrome detection
- **Word Break** - Dictionary problems

### 📊 **Stock Problems (1 problem)**
- **Best Time to Buy/Sell Stock** - Trading optimization

### 🏛️ **Classic DP Problems (4 problems)**
- **Matrix Chain Multiplication** - Interval DP
- **Burst Balloons** - Advanced MCM
- **Number of Digit One** - Digit DP
- **Advanced MCM** - Parenthesization variants

## 🌟 **Key Features**

### 📖 **Beginner-Friendly**
- **Detailed comments** explaining every step
- **Multiple approaches** from brute force to optimal
- **Step-by-step visualizations** for complex problems
- **Time/Space complexity** analysis for each approach

### 🔧 **Production-Ready Code**
- **Clean, well-structured** implementations
- **Comprehensive test cases** with edge cases
- **Error handling** and input validation
- **Performance comparisons** between approaches

### 🎓 **Educational Value**
- **Pattern recognition** guides
- **When to use each approach** explanations
- **Common pitfalls** and how to avoid them
- **Interview tips** and variations

## 📋 **Problem Difficulty Breakdown**

| Difficulty | Count | Examples |
|------------|--------|----------|
| **Easy** | 8 | Fibonacci, Climbing Stairs, House Robber |
| **Medium** | 25 | Coin Change, LIS, Unique Paths, Word Break |
| **Hard** | 7 | Edit Distance, Burst Balloons, MCM, Digit DP |

## 🎯 **Learning Path**

### 🚀 **Beginner (Start here!)**
1. `basics/Fibonacci_01.java` - Learn memoization
2. `one_dimensional/ClimbingStairs_Leetcode70.java` - Basic 1D DP
3. `one_dimensional/HouseRobber_Leetcode198.java` - Decision making
4. `two_dimensional/UniquePaths_Leetcode62.java` - 2D DP introduction

### 📈 **Intermediate**
1. `knapsack/Knapsack_01.java` - Classic optimization
2. `subsequence_problems/LongestIncreasingSubsequence_Leetcode300.java` - LIS pattern
3. `string_dp/EditDistance_Leetcode72.java` - String transformations
4. `two_dimensional/MinimumPathSum_Leetcode64.java` - Weighted problems

### 🏆 **Advanced**
1. `classic_problems/MatrixChainMultiplication.java` - Interval DP
2. `classic_problems/BurstBalloons_Leetcode312.java` - Complex MCM
3. `classic_problems/NumberOfDigitOne_Leetcode233.java` - Digit DP
4. `two_dimensional/MaximalRectangle_Leetcode85.java` - Multi-pattern combination

## 🔥 **Interview Favorites**

### 💡 **Most Asked**
- Climbing Stairs ⭐⭐⭐⭐⭐
- House Robber ⭐⭐⭐⭐⭐  
- Coin Change ⭐⭐⭐⭐⭐
- Longest Increasing Subsequence ⭐⭐⭐⭐⭐
- Edit Distance ⭐⭐⭐⭐⭐

### 🎯 **Company Specific**
- **Google**: Matrix Chain Multiplication, Burst Balloons
- **Amazon**: Knapsack variants, Target Sum
- **Microsoft**: String DP, Word Break
- **Facebook**: Grid problems, Unique Paths variants
- **Apple**: LIS problems, Stock trading

## 🛠️ **Code Templates**

### 📋 **1D DP Template**
```java
public int solve1D(int[] arr) {
    int[] dp = new int[arr.length];
    dp[0] = arr[0]; // Base case
    
    for (int i = 1; i < arr.length; i++) {
        dp[i] = Math.max(dp[i-1], arr[i]); // Recurrence
    }
    
    return dp[arr.length - 1];
}
```

### 🏢 **2D DP Template**
```java
public int solve2D(int[][] grid) {
    int m = grid.length, n = grid[0].length;
    int[][] dp = new int[m][n];
    
    // Initialize base cases
    dp[0][0] = grid[0][0];
    
    for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++) {
            // Recurrence relation
            dp[i][j] = grid[i][j] + Math.min(
                i > 0 ? dp[i-1][j] : 0,
                j > 0 ? dp[i][j-1] : 0
            );
        }
    }
    
    return dp[m-1][n-1];
}
```

### 🎒 **Knapsack Template**
```java
public int knapsack(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    
    for (int i = 0; i < weights.length; i++) {
        for (int w = capacity; w >= weights[i]; w--) {
            dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
        }
    }
    
    return dp[capacity];
}
```

## 📊 **Complexity Quick Reference**

| Pattern | Time | Space | Example |
|---------|------|-------|---------|
| **1D DP** | O(n) | O(n) → O(1) | Climbing Stairs |
| **2D DP** | O(mn) | O(mn) → O(n) | Unique Paths |
| **Knapsack** | O(nW) | O(W) | 0/1 Knapsack |
| **LIS** | O(n²) → O(n log n) | O(n) | Binary Search |
| **LCS** | O(mn) | O(mn) → O(n) | Edit Distance |
| **MCM** | O(n³) | O(n²) | Matrix Chain |

## 🚀 **Getting Started**

1. **Read the documentation**: Start with `docs/README.md`
2. **Pick your language**: Choose `java/` or `cpp/`
3. **Follow the learning path**: Begin with basics
4. **Practice daily**: Solve 1-2 problems per day
5. **Understand patterns**: Focus on recognizing when to use each pattern

## 🎉 **What Makes This Special?**

- ✅ **40+ Java problems** with multiple approaches
- ✅ **80+ C++ problems** for advanced users  
- ✅ **Detailed explanations** for every solution
- ✅ **Visualization helpers** for complex problems
- ✅ **Interview-focused** problem selection
- ✅ **Performance comparisons** between approaches
- ✅ **Template code** for quick reference
- ✅ **Step-by-step tutorials** for difficult concepts

---

### 🎯 **Ready to Master Dynamic Programming?**

Start with the basics, practice consistently, and you'll soon be solving the hardest DP problems with confidence! 

**Happy Coding!** 🚀✨