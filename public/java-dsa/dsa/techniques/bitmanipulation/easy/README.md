# Easy - Bit Manipulation Problems

## 🎯 Overview
This section contains entry-level bit manipulation problems that are perfect for building confidence and understanding fundamental concepts. These problems frequently appear as warm-up questions in technical interviews.

## 📝 Problem Categories

### **1. Single Number Variants**
Problems involving finding unique elements using XOR properties.

**🔥 NumberOf1Bits_Leetcode191.java**
- **Problem**: Count set bits (Hamming weight)
- **Key Technique**: Brian Kernighan's algorithm `n & (n-1)`
- **Time**: O(number of set bits), Space: O(1)
- **Interview Frequency**: Very High ⭐⭐⭐⭐⭐

**🔥 SingleNumber_Leetcode136.java**
- **Problem**: Find element appearing once when others appear twice
- **Key Technique**: XOR all elements (`a ^ a = 0`, `a ^ 0 = a`)
- **Time**: O(n), Space: O(1)
- **Interview Frequency**: Very High ⭐⭐⭐⭐⭐

**🔥 MissingNumber_Leetcode268.java**
- **Problem**: Find missing number in range [0, n]
- **Key Technique**: XOR expected vs actual numbers
- **Time**: O(n), Space: O(1)
- **Interview Frequency**: High ⭐⭐⭐⭐

### **2. Power Checks**
Problems involving powers of 2 and similar number properties.

**🔥 PowerOfTwo_Leetcode231.java**
- **Problem**: Check if number is power of 2
- **Key Technique**: `n > 0 && (n & (n-1)) == 0`
- **Time**: O(1), Space: O(1)
- **Interview Frequency**: High ⭐⭐⭐⭐

### **3. Bit Reversal**
Problems involving bit manipulation at the bit level.

**ReverseBits_Leetcode190.java**
- **Problem**: Reverse bits of 32-bit unsigned integer
- **Key Techniques**: Multiple approaches from simple to divide-and-conquer
- **Time**: O(1), Space: O(1)
- **Interview Frequency**: Medium ⭐⭐⭐

## 🎓 Learning Path

### Step 1: Master Basic Concepts
Start with these problems in order:
1. **PowerOfTwo_Leetcode231** - Learn the classic `n & (n-1)` trick
2. **NumberOf1Bits_Leetcode191** - Understand bit counting
3. **SingleNumber_Leetcode136** - Master XOR properties

### Step 2: Apply XOR Patterns
4. **MissingNumber_Leetcode268** - Extend XOR to finding missing elements
5. **ReverseBits_Leetcode190** - Practice bit manipulation techniques

### Step 3: Practice Variations
- Try to solve each problem with multiple approaches
- Understand time/space trade-offs
- Practice explaining your solutions

## 🔥 Must-Know Patterns

### **Pattern 1: XOR for Unique Elements**
```java
// Template for finding unique elements
int unique = 0;
for (int num : nums) {
    unique ^= num;  // XOR cancels out duplicates
}
return unique;
```

**When to use**: Finding elements that appear odd number of times

### **Pattern 2: Power of 2 Check**
```java
// Template for power of 2 operations
boolean isPowerOf2(int n) {
    return n > 0 && (n & (n-1)) == 0;
}
```

**When to use**: Optimization problems, array sizing, bit indexing

### **Pattern 3: Bit Counting**
```java
// Template for counting set bits
int countBits(int n) {
    int count = 0;
    while (n > 0) {
        n &= (n-1);  // Remove rightmost set bit
        count++;
    }
    return count;
}
```

**When to use**: Hamming distance, population count, bit analysis

### **Pattern 4: Missing Element Detection**
```java
// Template for finding missing elements using XOR
int findMissing(int[] nums, int n) {
    int xor = 0;
    
    // XOR all expected numbers
    for (int i = 0; i <= n; i++) xor ^= i;
    
    // XOR all actual numbers  
    for (int num : nums) xor ^= num;
    
    return xor;  // Missing number remains
}
```

**When to use**: Array problems with missing/duplicate elements

## 💡 Interview Tips

### **1. Start with Brute Force**
Always explain the brute force approach first, then optimize:
```java
// Brute force for Single Number
public int singleNumber(int[] nums) {
    for (int i = 0; i < nums.length; i++) {
        int count = 0;
        for (int j = 0; j < nums.length; j++) {
            if (nums[i] == nums[j]) count++;
        }
        if (count == 1) return nums[i];
    }
    return -1;
}

// Optimized using XOR
public int singleNumber(int[] nums) {
    int result = 0;
    for (int num : nums) result ^= num;
    return result;
}
```

### **2. Know Multiple Solutions**
For each problem, be ready to discuss:
- Time/Space complexity of each approach
- Trade-offs between approaches
- When to use each approach

### **3. Practice Bit Visualization**
Draw out bit patterns for small examples:
```
Example: PowerOfTwo check for n = 8
n = 8:     1000
n-1 = 7:   0111
n & (n-1): 0000  ← Result is 0, so 8 is power of 2
```

### **4. Common Follow-ups**
Be ready for these variations:
- What if numbers appear 3 times instead of 2?
- What if there are multiple missing numbers?
- How would you handle negative numbers?
- Can you solve without using XOR?

## 🚀 Quick Solutions Reference

```java
// Power of Two
return n > 0 && (n & (n-1)) == 0;

// Count Set Bits  
int count = 0;
while (n > 0) { n &= (n-1); count++; }
return count;

// Single Number (appears once, others twice)
int result = 0;
for (int num : nums) result ^= num;
return result;

// Missing Number in [0,n]
int missing = nums.length;
for (int i = 0; i < nums.length; i++) {
    missing ^= i ^ nums[i];
}
return missing;

// Reverse Bits (simple approach)
int result = 0;
for (int i = 0; i < 32; i++) {
    result = (result << 1) | (n & 1);
    n >>= 1;
}
return result;
```

## 📊 Complexity Summary

| Problem | Time | Space | Difficulty | Frequency |
|---------|------|-------|------------|-----------|
| Power of Two | O(1) | O(1) | ⭐ | ⭐⭐⭐⭐ |
| Count 1 Bits | O(k) | O(1) | ⭐ | ⭐⭐⭐⭐⭐ |
| Single Number | O(n) | O(1) | ⭐ | ⭐⭐⭐⭐⭐ |
| Missing Number | O(n) | O(1) | ⭐ | ⭐⭐⭐⭐ |
| Reverse Bits | O(1) | O(1) | ⭐⭐ | ⭐⭐⭐ |

*k = number of set bits*

## 🔗 Next Steps
Once comfortable with these easy problems:
1. Move to **MEDIUM/** for more challenging applications
2. Study **INTERVIEW_PATTERNS/** for advanced techniques
3. Practice explaining solutions clearly and concisely
4. Time yourself solving these problems (aim for < 10 minutes each)

---
*These easy problems form the foundation for all advanced bit manipulation techniques. Master them completely before moving forward!*