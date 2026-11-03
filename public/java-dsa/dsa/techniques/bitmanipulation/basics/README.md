# Basics - Bit Manipulation Fundamentals

## 📚 Overview
This section covers the fundamental concepts and operations that form the foundation of all bit manipulation techniques. Master these basics before moving to advanced problems.

## 🔧 Core Files

### **BitwiseOperators.java**
Complete guide to all bitwise operators in Java:
- **AND (&)**: Both bits must be 1
- **OR (|)**: At least one bit must be 1  
- **XOR (^)**: Exactly one bit must be 1
- **NOT (~)**: Flip all bits
- **Left Shift (<<)**: Multiply by 2^n
- **Right Shift (>>)**: Divide by 2^n (arithmetic)
- **Unsigned Right Shift (>>>)**: Divide by 2^n (logical)

### **BasicBitOperations.java**
Essential bit manipulation operations:
```java
// Check if ith bit is set
(num & (1 << i)) != 0

// Set ith bit
num | (1 << i)

// Clear ith bit  
num & ~(1 << i)

// Toggle ith bit
num ^ (1 << i)
```

### **PowerOfTwo.java**
Multiple approaches to check and work with powers of 2:
- **Classic trick**: `n > 0 && (n & (n-1)) == 0`
- **Rightmost bit**: `n > 0 && (n & (-n)) == n`
- **Applications**: Finding next/previous power of 2

### **CountSetBits.java**
Comprehensive bit counting techniques:
- **Brian Kernighan's Algorithm**: `n & (n-1)` removes rightmost set bit
- **Lookup Tables**: Precompute for optimization
- **Built-in Functions**: `Integer.bitCount(n)`
- **Range Counting**: Count bits for all numbers 1 to n

## 🎯 Key Concepts

### 1. **Two's Complement**
- Negative numbers represented as `~n + 1`
- Sign bit is the leftmost bit
- Range for 32-bit: [-2³¹, 2³¹-1]

### 2. **Bit Positions**
- Positions numbered 0 to 31 (right to left)
- Position 0 is LSB (Least Significant Bit)
- Position 31 is MSB (Most Significant Bit)

### 3. **Common Patterns**
```java
// Get rightmost set bit
int rightmost = n & (-n);

// Clear rightmost set bit
int cleared = n & (n - 1);

// Check if even/odd
boolean isEven = (n & 1) == 0;

// Check if power of 2
boolean isPowerOf2 = n > 0 && (n & (n-1)) == 0;
```

### 4. **Bit Manipulation vs Arithmetic**
```java
// Multiplication by 2^k
n << k  // Much faster than n * (2^k)

// Division by 2^k  
n >> k  // Much faster than n / (2^k)

// Modulo by 2^k
n & ((1 << k) - 1)  // Much faster than n % (2^k)
```

## 🚨 Common Pitfalls

### 1. **Integer Overflow**
```java
// Wrong: May overflow
int mask = 1 << 31;

// Correct: Use long for large shifts
long mask = 1L << 31;
```

### 2. **Negative Number Shifts**
```java
int n = -8;
System.out.println(n >> 2);   // -2 (arithmetic shift)
System.out.println(n >>> 2);  // 1073741822 (logical shift)
```

### 3. **Sign Extension**
```java
// Be careful with sign extension in right shifts
byte b = -1;
int i = b >> 1;  // i becomes -1, not 127
```

## 💡 Practice Problems

### Beginner Level
1. Check if a number is even or odd
2. Find the position of the rightmost set bit
3. Count total set bits in a number
4. Check if a number is a power of 2

### Intermediate Level
1. Toggle the kth bit of a number
2. Find the next power of 2 greater than n
3. Count set bits for all numbers from 1 to n
4. Find the only non-repeating element in an array

## 🔗 Applications

### 1. **Flags and Permissions**
```java
// File permissions example
final int READ = 1;    // 001
final int WRITE = 2;   // 010  
final int EXECUTE = 4; // 100

int permissions = READ | WRITE;  // 011
boolean canWrite = (permissions & WRITE) != 0;
```

### 2. **State Representation**
```java
// Game state using bits
int gameState = 0;
gameState |= (1 << 0);  // Player has key
gameState |= (1 << 1);  // Door is open
gameState |= (1 << 2);  // Boss defeated
```

### 3. **Memory Optimization**
```java
// Store 32 boolean values in one int
int flags = 0;
flags |= (1 << 5);      // Set flag 5
boolean flag5 = (flags & (1 << 5)) != 0;  // Check flag 5
```

## 📖 Quick Reference

### Basic Operations Cheatsheet
```java
// Bit checking and manipulation
boolean isBitSet(int n, int pos) { return (n & (1 << pos)) != 0; }
int setBit(int n, int pos) { return n | (1 << pos); }
int clearBit(int n, int pos) { return n & ~(1 << pos); }
int toggleBit(int n, int pos) { return n ^ (1 << pos); }

// Power of 2 operations
boolean isPowerOf2(int n) { return n > 0 && (n & (n-1)) == 0; }
int nextPowerOf2(int n) { /* implementation in PowerOfTwo.java */ }

// Bit counting
int countSetBits(int n) { return Integer.bitCount(n); }
int countSetBitsBK(int n) { 
    int count = 0;
    while (n > 0) { n &= (n-1); count++; }
    return count;
}
```

## 🎯 Next Steps
After mastering these basics:
1. Move to **EASY/** problems for practical application
2. Study **INTERVIEW_PATTERNS/** for common techniques
3. Practice **MEDIUM/** problems for interview preparation
4. Challenge yourself with **HARD/** problems for advanced concepts

---
*Remember: Bit manipulation might seem tricky at first, but with practice, these operations become second nature and can significantly optimize your code!*