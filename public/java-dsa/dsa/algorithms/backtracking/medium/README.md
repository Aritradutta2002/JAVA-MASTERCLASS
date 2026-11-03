# BACKTRACKING - MEDIUM PROBLEMS

## 📋 Problem List

| Problem | LeetCode | Importance | Key Concept | Companies |
|---------|----------|------------|-------------|-----------|
| Permutations | 46 | ⭐⭐⭐⭐⭐ | All permutations generation | All FAANG |
| Permutations II | 47 | ⭐⭐⭐⭐ | Handling duplicates | Google, Facebook |
| Combination Sum | 39 | ⭐⭐⭐⭐⭐ | Target sum with reuse | Amazon, Microsoft |
| Word Search | 79 | ⭐⭐⭐⭐⭐ | 2D grid backtracking | Facebook, Google |
| Palindrome Partitioning | 131 | ⭐⭐⭐⭐ | String partitioning | Amazon, Microsoft |
| Letter Tile Possibilities | 1079 | ⭐⭐⭐ | Permutations with frequency | Google |

## 🎯 Learning Objectives

Master these core backtracking concepts:

### 1. **Advanced Constraint Handling**
- Multiple constraints simultaneously
- Dynamic constraint checking
- Optimal pruning strategies

### 2. **Duplicate Handling**
- Sorting for duplicate management
- Skip duplicate branches efficiently
- Maintain lexicographic order

### 3. **Grid/Board Traversal**
- 2D backtracking patterns
- Visited state management
- Direction-based exploration

### 4. **Optimization Techniques**
- Early termination conditions
- Preprocessing for efficiency
- Space-time tradeoffs

## 🚀 Recommended Study Order

### Phase 1: Foundation Building
1. **Permutations (LeetCode 46)** - Core permutation logic
2. **Combination Sum (LeetCode 39)** - Target-based backtracking
3. **Permutations II (LeetCode 47)** - Duplicate handling

### Phase 2: Advanced Patterns  
4. **Word Search (LeetCode 79)** - 2D grid backtracking
5. **Palindrome Partitioning (LeetCode 131)** - String partitioning
6. **Letter Tile Possibilities (LeetCode 1079)** - Complex constraints

## 📊 Complexity Analysis

| Problem | Time Complexity | Space Complexity | Key Factors |
|---------|----------------|------------------|-------------|
| Permutations | O(N! × N) | O(N) | N! permutations |
| Permutations II | O(N! × N) | O(N) | Duplicate handling |
| Combination Sum | O(N^(T/M)) | O(T/M) | T=target, M=min value |
| Word Search | O(N × 4^L) | O(L) | N=cells, L=word length |
| Palindrome Partitioning | O(2^N × N) | O(N) | 2^N partitions |

## 🎨 Advanced Patterns

### Pattern 1: Permutations with Duplicates
```java
void backtrack(result, current, nums, used) {
    if (current.size() == nums.length) {
        result.add(new ArrayList<>(current));
        return;
    }
    
    for (int i = 0; i < nums.length; i++) {
        // Skip used elements
        if (used[i]) continue;
        
        // Skip duplicates: if current element equals previous 
        // and previous is not used, skip current
        if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) {
            continue;
        }
        
        used[i] = true;
        current.add(nums[i]);
        backtrack(result, current, nums, used);
        current.remove(current.size() - 1);
        used[i] = false;
    }
}
```

### Pattern 2: 2D Grid Exploration
```java
boolean dfs(board, word, row, col, index) {
    if (index == word.length()) return true;
    
    if (row < 0 || row >= board.length || col < 0 || col >= board[0].length ||
        board[row][col] != word.charAt(index)) {
        return false;
    }
    
    char temp = board[row][col];
    board[row][col] = '#'; // Mark visited
    
    boolean found = dfs(board, word, row+1, col, index+1) ||
                   dfs(board, word, row-1, col, index+1) ||
                   dfs(board, word, row, col+1, index+1) ||
                   dfs(board, word, row, col-1, index+1);
    
    board[row][col] = temp; // Backtrack
    return found;
}
```

### Pattern 3: Target Sum with Constraints
```java
void backtrack(result, current, candidates, target, start) {
    if (target == 0) {
        result.add(new ArrayList<>(current));
        return;
    }
    
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > target) break; // Pruning
        
        current.add(candidates[i]);
        // Use i (not i+1) if reuse allowed, i+1 if not
        backtrack(result, current, candidates, target - candidates[i], i);
        current.remove(current.size() - 1);
    }
}
```

## 💡 Advanced Optimization Techniques

### 1. **Sorting for Pruning**
```java
Arrays.sort(candidates); // Enable early termination
if (candidates[i] > remaining) break;
```

### 2. **Duplicate Skipping**
```java
// For combinations with duplicates
if (i > start && nums[i] == nums[i-1]) continue;

// For permutations with duplicates  
if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue;
```

### 3. **In-place Modification**
```java
// Instead of visited array, modify in place
char temp = board[row][col];
board[row][col] = '#';
// ... recursive calls ...
board[row][col] = temp;
```

### 4. **Preprocessing**
```java
// Precompute palindrome table for O(1) lookup
boolean[][] isPalindrome = new boolean[n][n];
// Fill table using DP
```

## 🔍 Interview Success Strategies

### Problem Recognition Patterns:
- **"All possible"** → Likely backtracking
- **"Generate combinations/permutations"** → Classic backtracking  
- **"Find path with constraints"** → Grid backtracking
- **"Partition with conditions"** → Partitioning backtracking

### Common Follow-ups:
1. **"Can you optimize this?"**
   - Discuss sorting, pruning, preprocessing
   - Mention space optimizations

2. **"What if input has duplicates?"**
   - Explain duplicate handling strategies
   - Show sorting + skipping technique

3. **"How would you handle very large inputs?"**
   - Discuss iterative alternatives
   - Mention memory-efficient approaches

### Time Management:
- **Analysis:** 3-5 minutes
- **Coding:** 15-20 minutes  
- **Testing:** 5-7 minutes
- **Optimization discussion:** 5 minutes

## 🎯 Mastery Checklist

### Core Skills:
- [ ] Can implement basic backtracking template from memory
- [ ] Understand when and how to handle duplicates
- [ ] Can optimize with sorting and pruning
- [ ] Comfortable with 2D grid problems
- [ ] Can explain time/space complexity accurately

### Advanced Skills:
- [ ] Can identify optimization opportunities quickly
- [ ] Comfortable with multiple constraint types
- [ ] Can handle follow-up questions confidently
- [ ] Can code efficiently under time pressure
- [ ] Can explain alternative approaches

## 🚫 Common Pitfalls

### 1. **Forgetting to Backtrack**
```java
// WRONG
current.add(nums[i]);
backtrack(...);
// Missing: current.remove(current.size() - 1);
```

### 2. **Incorrect Duplicate Handling**
```java
// WRONG - will miss some permutations
if (i > 0 && nums[i] == nums[i-1]) continue;

// CORRECT
if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue;
```

### 3. **Not Creating New Lists**
```java
// WRONG
result.add(current);

// CORRECT  
result.add(new ArrayList<>(current));
```

### 4. **Inefficient Constraint Checking**
```java
// WRONG - checking same constraints repeatedly
if (isValid(...)) { /* already computed before */ }

// CORRECT - precompute or cache results
```

## 📈 Next Steps

After mastering MEDIUM problems:

1. **Tackle HARD problems** - Complex constraint satisfaction
2. **Study game theory** - Advanced backtracking applications  
3. **Practice under time pressure** - Simulate interview conditions
4. **Explore variations** - Different constraint types
5. **Review FAANG interview experiences** - Real problem variations

## 🏆 Success Metrics

- Solve any MEDIUM backtracking problem in 25-30 minutes
- Identify and implement key optimizations
- Handle follow-up questions confidently
- Explain approach clearly and concisely
- Write bug-free code on first attempt

Remember: MEDIUM problems are where interviews are won or lost. Master these patterns completely!