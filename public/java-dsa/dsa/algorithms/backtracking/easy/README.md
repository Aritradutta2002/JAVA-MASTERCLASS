# BACKTRACKING - EASY PROBLEMS

## 📋 Problem List

| Problem | LeetCode | Difficulty | Importance | Key Concept |
|---------|----------|------------|------------|-------------|
| Subsets | 78 | Easy | ⭐⭐⭐⭐⭐ | Basic backtracking pattern |
| Generate Parentheses | 22 | Easy/Medium | ⭐⭐⭐⭐⭐ | Constraint-based backtracking |
| Letter Case Permutation | 784 | Easy | ⭐⭐⭐⭐ | Choose/don't choose pattern |

## 🎯 Learning Objectives

After completing these problems, you should understand:

1. **Basic Backtracking Pattern**
   - Choose → Recurse → Backtrack
   - When to add solutions to result
   - How to avoid duplicate solutions

2. **Decision Trees**
   - How to visualize backtracking as a tree
   - Understanding branching factors
   - Pruning invalid branches

3. **Constraint Handling**
   - Checking constraints before making choices
   - Early termination for efficiency

## 🚀 Recommended Study Order

### 1. Start with Subsets (LeetCode 78)
- **Why first?** Most fundamental backtracking problem
- **Key concepts:** Include/exclude pattern, avoiding duplicates
- **Practice:** Draw the recursion tree, understand time complexity

### 2. Letter Case Permutation (LeetCode 784)
- **Why second?** Similar pattern but with character manipulation
- **Key concepts:** Conditional branching, string manipulation
- **Practice:** Compare iterative vs recursive approaches

### 3. Generate Parentheses (LeetCode 22)
- **Why third?** Introduces constraint-based backtracking
- **Key concepts:** Invalid state detection, constraint satisfaction
- **Practice:** Understand why brute force is inefficient

## 📊 Complexity Analysis

| Problem | Time Complexity | Space Complexity | Notes |
|---------|----------------|------------------|-------|
| Subsets | O(2^N × N) | O(N) | 2^N subsets, N to copy each |
| Generate Parentheses | O(4^N / √N) | O(4^N / √N) | Catalan number |
| Letter Case Permutation | O(2^L × N) | O(2^L × N) | L = letters, N = string length |

## 🎨 Common Patterns

### Pattern 1: Include/Exclude (Subsets)
```java
void backtrack(result, current, nums, start) {
    result.add(new ArrayList<>(current));
    
    for (int i = start; i < nums.length; i++) {
        current.add(nums[i]);           // Choose
        backtrack(result, current, nums, i + 1);  // Recurse
        current.remove(current.size() - 1);       // Backtrack
    }
}
```

### Pattern 2: Constraint-Based (Generate Parentheses)
```java
void backtrack(result, current, open, close, max) {
    if (current.length() == max * 2) {
        result.add(current.toString());
        return;
    }
    
    if (open < max) {
        current.append('(');
        backtrack(result, current, open + 1, close, max);
        current.deleteCharAt(current.length() - 1);
    }
    
    if (close < open) {
        current.append(')');
        backtrack(result, current, open, close + 1, max);
        current.deleteCharAt(current.length() - 1);
    }
}
```

## 💡 Key Tips for Success

### 1. **Always Draw the Tree**
- Visualize the decision tree before coding
- Identify base cases and branching conditions
- Understand the depth and breadth of exploration

### 2. **Master the Template**
- Choose → Recurse → Backtrack is the core pattern
- Don't forget to backtrack (undo your choice)
- Create new copies when adding to result

### 3. **Handle Edge Cases**
- Empty input
- Single element
- Maximum constraints

### 4. **Optimize When Possible**
- Sort input for pruning opportunities
- Early termination conditions
- Avoid unnecessary work

## 🔍 Interview Tips

### What Interviewers Look For:
1. **Clear explanation** of the approach
2. **Correct implementation** of backtracking pattern
3. **Time/space complexity** analysis
4. **Edge case handling**
5. **Optimization opportunities**

### Common Questions:
- "Can you solve this iteratively?"
- "How would you optimize this solution?"
- "What if the constraints were different?"
- "How would you handle duplicates in the input?"

### Red Flags to Avoid:
- Forgetting to backtrack
- Not creating new copies for result
- Missing base cases
- Incorrect complexity analysis

## 🎯 Next Steps

Once you've mastered these EASY problems:

1. **Move to MEDIUM problems** - More complex constraints and optimizations
2. **Practice variations** - Subsets with duplicates, combination problems
3. **Time yourself** - Aim to solve EASY problems in 15-20 minutes
4. **Explain out loud** - Practice articulating your thought process

## 📚 Additional Resources

- **Visualizations:** Use recursion tree diagrams
- **Practice:** LeetCode's backtracking tag
- **Theory:** Review recursion fundamentals if needed
- **Patterns:** Study the common templates in INTERVIEW_PATTERNS folder

Remember: These problems form the foundation of backtracking. Master them completely before moving to harder problems!