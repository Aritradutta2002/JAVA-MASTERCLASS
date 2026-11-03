# Interview Patterns - Bit Manipulation

## 🎯 Overview
This directory contains the most important bit manipulation patterns frequently asked in MAANG/FAANG interviews. Master these patterns to solve 90% of bit manipulation problems.

## 📋 Essential Patterns

### 1. **XOR Patterns** (`XORPatterns.java`)
- **Single Number Problems**: Find unique elements when others appear multiple times
- **Missing Number**: Use XOR properties to find missing elements
- **Swap Without Temp**: Exchange values using XOR
- **Range XOR**: Efficiently calculate XOR of number ranges
- **Gray Code**: Binary sequence with single bit difference
- **XOR Cipher**: Simple encryption/decryption

**Key Property**: `a ^ a = 0`, `a ^ 0 = a`

### 2. **Bit Masking** (`BitMasking.java`)
- **Subset Generation**: Use bitmask to represent all subsets
- **Dynamic Programming**: State compression in DP problems
- **TSP/Hamiltonian Path**: Travel salesman and path problems
- **Game Theory**: Nim game and similar problems
- **Combinatorial Problems**: Assignment and partition problems

**Key Technique**: Use integers as compact representation of sets/states

### 3. **Bit Counting** (`BitCounting.java`)
- **Hamming Weight**: Count set bits efficiently
- **Population Count**: Various algorithms for counting 1s
- **Brian Kernighan's**: Remove rightmost set bit technique
- **Lookup Tables**: Precompute for optimization

### 4. **Arithmetic Operations** (`BitwiseArithmetic.java`)
- **Addition**: Implement addition using XOR and AND
- **Multiplication**: Fast multiplication using shifts
- **Division**: Binary search approach for division
- **Power of 2**: Check and manipulate powers of 2

## 🔥 Most Frequently Asked Problems

### Easy Level
1. **Single Number** (LeetCode 136) - XOR all elements
2. **Power of Two** (LeetCode 231) - `n & (n-1) == 0`
3. **Number of 1 Bits** (LeetCode 191) - Hamming weight
4. **Reverse Bits** (LeetCode 190) - Bit reversal techniques
5. **Missing Number** (LeetCode 268) - XOR approach

### Medium Level
1. **Single Number II** (LeetCode 137) - Digital logic approach
2. **Single Number III** (LeetCode 260) - Find two unique numbers
3. **Bitwise AND Range** (LeetCode 201) - Common prefix
4. **Maximum XOR** (LeetCode 421) - Trie-based solution
5. **Subsets** (LeetCode 78) - Bit masking

### Hard Level
1. **Maximum XOR Subarray** - Advanced XOR techniques
2. **Count Subarrays with XOR** - Prefix XOR with HashMap
3. **Longest Awesome Substring** (LeetCode 1542) - Bit state DP
4. **TSP with Bitmask** - Classic DP optimization
5. **Game Theory Problems** - Nim and similar games

## 💡 Problem-Solving Strategy

### Step 1: Identify the Pattern
- **Single/Missing elements** → XOR patterns
- **Subsets/States** → Bit masking
- **Counting bits** → Hamming weight techniques
- **Range operations** → Prefix techniques
- **Optimization** → Bit manipulation arithmetic

### Step 2: Choose the Right Technique
```java
// XOR for finding unique elements
int unique = 0;
for (int num : nums) unique ^= num;

// Bit masking for subsets
for (int mask = 0; mask < (1 << n); mask++) {
    // Process subset represented by mask
}

// Check/Set/Clear bits
boolean isSet = (num & (1 << i)) != 0;
int setBit = num | (1 << i);
int clearBit = num & ~(1 << i);
```

### Step 3: Optimize
- Use bit operations instead of arithmetic when possible
- Leverage XOR properties for space optimization
- Consider lookup tables for repeated computations
- Use bit masking for state compression in DP

## 🚀 Quick Reference

### Basic Operations
```java
// Check if i-th bit is set
(n & (1 << i)) != 0

// Set i-th bit
n | (1 << i)

// Clear i-th bit
n & ~(1 << i)

// Toggle i-th bit
n ^ (1 << i)

// Check if power of 2
n > 0 && (n & (n-1)) == 0

// Count set bits
Integer.bitCount(n)

// Rightmost set bit
n & (-n)

// Clear rightmost set bit
n & (n-1)
```

### XOR Properties
```java
// Self-inverse
a ^ b ^ b = a

// Find missing in [1,n]
missing = (1^2^...^n) ^ (array XOR)

// Swap without temp
a ^= b; b ^= a; a ^= b;
```

### Bit Masking
```java
// Generate all subsets
for (int mask = 0; mask < (1 << n); mask++) {
    for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
            // i-th element is in subset
        }
    }
}

// Iterate subsets of mask
for (int sub = mask; sub > 0; sub = (sub-1) & mask) {
    // Process subset 'sub' of 'mask'
}
```

## 📚 Advanced Topics

### 1. **Trie for XOR Problems**
- Maximum XOR pairs
- XOR queries with constraints
- Persistent trie for historical queries

### 2. **Digital Logic Simulation**
- State machines for counting
- Complex bit state transitions
- Memory-efficient implementations

### 3. **Gaussian Elimination on Bits**
- Linear basis for XOR problems
- Maximum XOR subset
- Linear independence in GF(2)

### 4. **Bit Manipulation in Competitive Programming**
- Fast I/O using bits
- Coordinate compression
- Mo's algorithm optimizations

## 🎯 Interview Tips

1. **Start Simple**: Begin with basic bit operations before complex algorithms
2. **Draw Examples**: Visualize bit patterns for small inputs
3. **Know Properties**: Master XOR and bit manipulation properties
4. **Practice Patterns**: Focus on recognizing problem patterns
5. **Optimize Gradually**: Start with correct solution, then optimize

## 🔗 Related Topics
- Number Theory (GCD using bits)
- Dynamic Programming (state compression)
- Graph Algorithms (adjacency matrix compression)
- String Algorithms (rolling hash with XOR)

---
*Master these patterns and you'll be ready for any bit manipulation question in technical interviews!*