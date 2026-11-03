# System Design - FAANG Interview Problems

## Folder Structure
```
SYSTEM_DESIGN/
├── cache/              # Cache implementations (LRU, LFU)
├── data_structures/    # Design data structures (Stack, Queue variants)
├── rate_limiter/       # Rate limiting algorithms
├── distributed/        # Distributed system designs
└── real_world/         # Real-world system designs
```

## Pattern Overview
System design problems test your ability to design scalable, efficient data structures and systems.

## Problem Categories

### 1. Cache Systems (Very Important)
- **LRUCache.java** - LeetCode 146 (MEDIUM) ⭐⭐⭐
- **LFUCache.java** - LeetCode 460 (HARD) ⭐⭐⭐
- **TimeBasedKeyValueStore.java** - LeetCode 981 (MEDIUM) ⭐⭐

### 2. Data Structure Design
- **MinStack.java** - LeetCode 155 (MEDIUM) ⭐⭐⭐
- **MaxStack.java** - LeetCode 716 (HARD) ⭐⭐
- **SnapshotArray.java** - LeetCode 1146 (MEDIUM) ⭐⭐

### 3. Iterator & Stream
- **PeekingIterator.java** - LeetCode 284 (MEDIUM) ⭐⭐
- **FlattenNestedListIterator.java** - LeetCode 341 (MEDIUM) ⭐⭐
- **ZigzagIterator.java** - LeetCode 281 (MEDIUM) ⭐

### 4. Rate Limiter
- **TokenBucket.java** - Token bucket algorithm ⭐⭐⭐
- **SlidingWindowRateLimiter.java** - Sliding window ⭐⭐⭐
- **LeakyBucket.java** - Leaky bucket algorithm ⭐⭐

### 5. Real-World Systems
- **TinyURL.java** - LeetCode 535 (MEDIUM) ⭐⭐
- **FileSystem.java** - LeetCode 588 (MEDIUM) ⭐⭐
- **LoggerRateLimiter.java** - LeetCode 359 (EASY) ⭐

## FAANG Interview Frequency
- **Amazon**: Very High (LRU Cache, Rate Limiter)
- **Google**: Very High (All categories)
- **Facebook/Meta**: Very High (Cache, Data structures)
- **Microsoft**: High (Cache, Iterator patterns)
- **Apple**: Medium-High

## Key Concepts

### Cache Eviction Policies
1. **LRU**: Least Recently Used
2. **LFU**: Least Frequently Used
3. **FIFO**: First In First Out
4. **Random**: Random eviction

### Rate Limiting Algorithms
1. **Token Bucket**: Smooth traffic, allows bursts
2. **Leaky Bucket**: Constant output rate
3. **Fixed Window**: Simple but has boundary issues
4. **Sliding Window**: More accurate than fixed window

## Common Patterns

### Doubly Linked List + HashMap (LRU)
```java
class LRUCache {
    class Node {
        int key, value;
        Node prev, next;
    }
    
    Map<Integer, Node> map;
    Node head, tail;
    int capacity;
}
```

### TreeMap for Time-based
```java
class TimeBasedKV {
    Map<String, TreeMap<Integer, String>> map;
}
```

## Design Principles
1. **Clarify requirements**: Ask about scale, constraints
2. **API design**: Define clear interfaces
3. **Data structures**: Choose appropriate structures
4. **Trade-offs**: Discuss time vs space
5. **Scalability**: Consider distributed scenarios

## Practice Strategy
1. Master LRU Cache (most important)
2. Solve LFU Cache (harder variant)
3. Practice iterator patterns
4. Learn rate limiting algorithms
5. Study real-world system designs

## Interview Tips
- Always clarify requirements first
- Discuss trade-offs explicitly
- Consider edge cases
- Think about scalability
- Code clean, modular solutions
