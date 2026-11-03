# Comprehensive Bit Manipulation Guide

This folder contains a complete implementation of all concepts from the **Bit-Manipulation-Java-Guide.pdf** with additional problems and advanced techniques. The problems are organized by difficulty and include both classic interview questions and competitive programming challenges.

## 📁 Folder Structure

### 🟢 BASICS/
Fundamental bit operations and concepts:
- `BasicBitOperations.java` - Set, clear, toggle, check bits
- `BitMaskingFundamentals.java` - Masking techniques
- `BitwiseOperators.java` - All bitwise operators with examples
- `CompleteBitOperationsGuide.java` - Comprehensive reference
- `CountSetBits.java` - Population count algorithms
- `PowerOfTwo.java` - Power of 2 detection methods

### 🟡 EASY/
Entry-level problems perfect for learning:
- `MissingNumber_Leetcode268.java` - Find missing number using XOR
- `NumberOf1Bits_Leetcode191.java` - Count set bits (Hamming weight)
- `PowerOfTwo_Leetcode231.java` - Check if number is power of 2
- `ReverseBits_Leetcode190.java` - Reverse bits in integer
- `SingleNumber_Leetcode136.java` - Find unique element using XOR
- `HammingDistance_Leetcode461.java` - Count differing bits between numbers
- `AlternatingBits_Leetcode693.java` - Check for alternating bit pattern
- `ComplementOf10Base_Leetcode1009.java` - Bitwise complement operations

### 🟠 MEDIUM/
Intermediate problems requiring deeper understanding:
- `BitwiseANDRange_Leetcode201.java` - Bitwise AND of number range
- `CountingBits_Leetcode338.java` - Count bits from 0 to n efficiently
- `MaximumXORSubarray_Leetcode421.java` - Maximum XOR using trie
- `SingleNumberII_Leetcode137.java` - Find unique when others appear thrice
- `TwoMissingNumbers_Leetcode260.java` - Find two unique numbers
- `GrayCode_Leetcode89.java` - Generate Gray code sequence
- `SwapAdjacentBits_Leetcode1722.java` - Advanced bit swapping techniques
- `SubsetGeneration_Leetcode78.java` - Generate all subsets using bitmasks

### 🔴 HARD/
Advanced problems for competitive programming:
- `CountSubarraysWithXOR_Classic.java` - Count subarrays with given XOR
- `MaximumXORQueries_Leetcode1707.java` - Complex XOR queries with tries
- `NumberOfValidWords_Leetcode1542.java` - Bitmask DP for word validation
- `BitmaskDP_TSP.java` - Traveling Salesman using bitmask DP
- `BinaryArithmetic_NoOperators.java` - Arithmetic using only bit operations

### 🎯 INTERVIEW_PATTERNS/
Common patterns and techniques:
- `BitCounting.java` - Various bit counting algorithms
- `BitMasking.java` - Advanced masking techniques
- `BitwiseArithmetic.java` - Arithmetic using bit operations
- `XORPatterns.java` - XOR-based problem patterns

### 🚀 ADVANCED_PATTERNS/
Competitive programming techniques:
- `BitmaskIterationPatterns.java` - Advanced subset iteration patterns

## 📖 Key Concepts Covered

### 1. **Basic Operations**
- Set, clear, toggle, check bits
- Bit shifting (left, right, unsigned)
- Bitwise operators (AND, OR, XOR, NOT)

### 2. **Essential Tricks** (From PDF)
- Power of 2 detection: `n & (n-1) == 0`
- Isolate rightmost set bit: `n & -n`
- Clear rightmost set bit: `n & (n-1)`
- Count set bits (Brian Kernighan's algorithm)
- Check even/odd: `n & 1`
- Swap without temp: `a ^= b; b ^= a; a ^= b;`

### 3. **XOR Properties** (Critical for interviews)
- `a ^ a = 0`
- `a ^ 0 = a`
- XOR is commutative and associative
- Applications: Find unique elements, swap variables

### 4. **Advanced Patterns**
- **Single Number Variations**: Handle duplicates appearing 2, 3, or more times
- **Hamming Distance**: Count differing bits between numbers
- **Gray Code**: Generate sequences with single-bit differences
- **Subset Generation**: Use bitmasks to represent and iterate subsets

### 5. **Bitmask Dynamic Programming**
- **TSP (Traveling Salesman)**: `dp[mask][last]` = min cost visiting cities in mask, ending at last
- **Assignment Problems**: Assign tasks to workers optimally
- **Subset Sum**: Check if subset with target sum exists
- **State Compression**: Represent complex states using bits

### 6. **Competitive Programming Techniques**
- **SOS (Sum Over Subsets) DP**: Calculate subset sums efficiently
- **Meet in the Middle**: Split problem space for exponential problems
- **Gosper's Hack**: Generate next combination with same popcount
- **Subset Iteration**: `for(int s = mask; s; s = (s-1) & mask)`

## 🔧 Bit Manipulation Utility Functions

```java
// From the implementations:

// Check if bit i is set
boolean isBitSet(int n, int i) { return (n & (1 << i)) != 0; }

// Set bit i
int setBit(int n, int i) { return n | (1 << i); }

// Clear bit i  
int clearBit(int n, int i) { return n & ~(1 << i); }

// Toggle bit i
int toggleBit(int n, int i) { return n ^ (1 << i); }

// Count set bits
int popCount(int n) { return Integer.bitCount(n); }

// Isolate rightmost set bit
int rightmostBit(int n) { return n & -n; }

// Check power of 2
boolean isPowerOf2(int n) { return n > 0 && (n & (n-1)) == 0; }
```

## 🎯 Interview Preparation Checklist

### ✅ Master These First:
1. **Single Number** (LeetCode 136) - XOR properties
2. **Missing Number** (LeetCode 268) - XOR or arithmetic
3. **Power of Two** (LeetCode 231) - Bit manipulation trick
4. **Hamming Distance** (LeetCode 461) - Basic bit comparison

### ✅ Intermediate Level:
1. **Single Number II** (LeetCode 137) - Bit counting for triplicates
2. **Single Number III** (LeetCode 260) - Two unique numbers
3. **Counting Bits** (LeetCode 338) - DP with bit manipulation
4. **Subsets** (LeetCode 78) - Bitmask for subset generation

### ✅ Advanced Level:
1. **Maximum XOR** problems - Trie-based solutions
2. **Bitmask DP** problems - TSP, assignment, subset problems
3. **SOS DP** patterns - Sum over subsets dynamic programming

## 🚀 Performance Tips

1. **Use built-in functions**: `Integer.bitCount()`, `Integer.numberOfLeadingZeros()`
2. **Avoid overflow**: Use `long` for intermediate calculations
3. **Bit shifting**: `<< 1` is faster than `* 2`, `>> 1` faster than `/ 2`
4. **Unsigned operations**: Use `>>>` for logical right shift

## 🔍 Common Pitfalls (From PDF)

1. **Shift overflow**: `1 << 31` gives `Integer.MIN_VALUE` (negative!)
2. **Shift wrapping**: `1 << 32` equals `1 << 0` = 1
3. **Sign extension**: `>>` vs `>>>` for negative numbers
4. **Operator precedence**: `&` has lower precedence than `==`
5. **Mask creation**: Handle edge case when creating masks for all 32 bits

## 📚 Additional Resources

- **PDF Reference**: Bit-Manipulation-Java-Guide.pdf (comprehensive theory)
- **Practice**: LeetCode Bit Manipulation tag
- **Competitive Programming**: Codeforces, AtCoder problems with bitmask tag
- **Advanced**: USACO Guide on Bitmask DP

## 🏆 Problem Difficulty Progression

**Week 1**: BASICS + EASY problems
**Week 2**: MEDIUM problems + XOR patterns  
**Week 3**: HARD problems + Bitmask DP
**Week 4**: ADVANCED_PATTERNS + Contest problems

Each file includes:
- ✨ Multiple approaches with complexity analysis
- 🧪 Comprehensive test cases
- 📊 Performance comparisons
- 🔍 Step-by-step explanations
- 🎯 Interview tips and variations

Start with BASICS and EASY, then progress through MEDIUM and HARD. The INTERVIEW_PATTERNS folder contains common templates you'll see repeatedly in technical interviews.

Happy bit manipulation learning! 🚀