# Collections Framework - Lists (In-Depth Masterclass)

## 1. List Architecture & Hierarchy

The Java `List` interface represents an **ordered collection (sequence)** that allows duplicate elements and positional access.

```
                         Iterable<E>
                             │
                       Collection<E>
                             │
                          List<E>
        ┌────────────────────┼───────────────────┬──────────────────────┐
        │                    │                   │                      │
   ArrayList<E>        LinkedList<E>          Vector<E>       CopyOnWriteArrayList<E>
  (Dynamic Array)   (Doubly-Linked List)   (Legacy Sync)       (Lock-Free Reads)
                             │                   │
                        Deque<E>              Stack<E>
```

### Core Characteristics & Complexity Comparison

| Feature | `ArrayList` | `LinkedList` | `Vector` | `CopyOnWriteArrayList` |
| :--- | :--- | :--- | :--- | :--- |
| **Underlying Data Structure** | Dynamic Array (`Object[]`) | Doubly-Linked List (`Node<E>`) | Dynamic Array (`Object[]`) | Volatile Array (`Object[]`) |
| **Random Access (`get(i)`)** | **$O(1)$** | **$O(n)$** (traverses $n/2$ nodes) | **$O(1)$** | **$O(1)$** |
| **Insert / Remove at End** | Amortized **$O(1)$**; worst $O(n)$ resize | **$O(1)$** (tail pointer) | Amortized **$O(1)$** | **$O(n)$** (full array copy) |
| **Insert / Remove at Beginning** | **$O(n)$** (shifts all elements) | **$O(1)$** (head pointer) | **$O(n)$** | **$O(n)$** (full array copy) |
| **Insert / Remove in Middle** | **$O(n)$** (element shift) | **$O(n)$** search + **$O(1)$** link | **$O(n)$** | **$O(n)$** (full array copy) |
| **Thread Safety** | ❌ No | ❌ No | ✅ Yes (method-level `synchronized`) | ✅ Yes (COW + ReentrantLock) |
| **Iterator Behavior** | Fail-Fast (`ConcurrentModException`) | Fail-Fast (`ConcurrentModException`) | Fail-Fast (Enumerator is fail-safe) | Fail-Safe / Snapshot (Never throws CME) |
| **Memory Footprint** | Low (contiguous array) | High (~24–32 bytes per node overhead) | Low | High on writes |
| **CPU Cache Friendliness** | **High** (contiguous RAM, pre-fetching) | **Poor** (pointer chasing, cache misses) | High | High |
| **Null Elements Allowed?** | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes |

---

## 2. ArrayList Deep Dive Internals

### Source Code Anatomy (OpenJDK 8–21)

```java
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable {

    // Default initial capacity when first element is added
    private static final int DEFAULT_CAPACITY = 10;

    // Shared empty array instance used for empty instances
    private static final Object[] EMPTY_ELEMENTDATA = {};

    // Shared empty array instance for default-sized instances
    // Used to distinguish between empty ArrayList() and ArrayList(0)
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    // The backing array buffer
    transient Object[] elementData; // non-private to simplify nested class access

    // Number of elements in the list
    private int size;

    // Modification count for fail-fast iteration
    protected transient int modCount = 0;
}
```

### Lazy Initialization Mechanics
Why are there **two distinct empty array constants**?
- `new ArrayList()` sets `elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA`. No array of size 10 is allocated yet! It waits until the very first `.add()` call. On first add, it calculates:
  $$\text{capacity} = \max(\text{DEFAULT\_CAPACITY}, \text{minCapacity}) = \max(10, 1) = 10$$
  This saves heap memory if thousands of lists are initialized empty.
- `new ArrayList(0)` sets `elementData = EMPTY_ELEMENTDATA`. When elements are added, it expands strictly starting from capacity 1 (not 10).

### Growth Algorithm & Resizing (1.5x)
When `size + 1 > elementData.length`, ArrayList resizes:

```java
private Object[] grow(int minCapacity) {
    int oldCapacity = elementData.length;
    if (oldCapacity > 0 || elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
        // Bit shift: oldCapacity >> 1 is equivalent to oldCapacity / 2
        int newCapacity = ArraysSupport.newLength(oldCapacity,
                minCapacity - oldCapacity, /* minimum growth */
                oldCapacity >> 1           /* preferred growth: 50% */);
        return elementData = Arrays.copyOf(elementData, newCapacity);
    } else {
        return elementData = new Object[Math.max(DEFAULT_CAPACITY, minCapacity)];
    }
}
```

```
Growth Formula:
newCapacity = oldCapacity + (oldCapacity >> 1)  // 1.5x current capacity
Example: 10 -> 15 -> 22 -> 33 -> 49 -> 73 ...
```

**Why 1.5x instead of 2.0x (like Vector)?**
- A growth factor of $2.0$ guarantees that the newly allocated memory block is strictly greater than the sum of all previously allocated and deallocated blocks. The memory allocator can **never reuse previously freed memory slots**.
- A growth factor of $1.5$ (or $\approx 1.618$, golden ratio) allows the memory allocator to reclaim previously freed chunks in later resizes, significantly reducing memory fragmentation.

**Maximum Array Size:**
```java
private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
// Subtract 8 because some VMs store object header words inside the array boundary
```

### Insertion & Deletion: `System.arraycopy`
When inserting at index $i$ or removing from index $i$:
```java
public void add(int index, E element) {
    rangeCheckForAdd(index);
    modCount++;
    final int s = size;
    if (s == elementData.length)
        elementData = grow();
    // Shift elements to the right by 1
    System.arraycopy(elementData, index, elementData, index + 1, s - index);
    elementData[index] = element;
    size = s + 1;
}

public E remove(int index) {
    rangeCheck(index);
    modCount++;
    E oldValue = (E) elementData[index];
    int numMoved = size - index - 1;
    if (numMoved > 0)
        // Shift elements to the left by 1
        System.arraycopy(elementData, index + 1, elementData, index, numMoved);
    elementData[--size] = null; // CRUCIAL: Clear reference to allow GC (avoid memory leak!)
    return oldValue;
}
```

### Memory Footprint & Cache Locality
- An empty `ArrayList` object occupies 24 bytes (with 8-byte compressed OOPs: mark word 8B + class word 4B + `elementData` ref 4B + `size` int 4B + padding 4B).
- Backing array: $16 \text{ bytes (array header)} + (\text{capacity} \times 4 \text{ bytes ref}) + \text{padding}$.
- **CPU Cache Locality:** Because elements sit in contiguous memory locations, modern CPU hardware prefetchers load adjacent array entries into L1/L2 cache lines (typically 64 bytes). Iterating over an `ArrayList` is up to **50x to 100x faster** than traversing a `LinkedList` of the same size.

---

## 3. LinkedList Deep Dive Internals

### Doubly-Linked Node Structure
```java
public class LinkedList<E> extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable {

    transient int size = 0;
    transient Node<E> first;
    transient Node<E> last;

    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
}
```

### Positional Access Optimization: $n/2$ Traversal
`LinkedList.get(index)` is not purely $O(n)$ from the beginning; it checks whether the index is closer to the head or the tail:
```java
Node<E> node(int index) {
    // If index is in first half, traverse forward from head
    if (index < (size >> 1)) {
        Node<E> x = first;
        for (int i = 0; i < index; i++)
            x = x.next;
        return x;
    } else { // If in second half, traverse backward from tail
        Node<E> x = last;
        for (int i = size - 1; i > index; i--)
            x = x.prev;
        return x;
    }
}
```
*Complexity:* $\frac{N}{2}$ iterations, still asymptotically **$O(n)$**.

### Memory Overhead of LinkedList
Each element in `LinkedList` allocates an independent `Node` instance on the heap:
- 16 bytes: `Node` object header
- 4 bytes: `prev` reference (with Compressed OOPs)
- 4 bytes: `next` reference
- 4 bytes: `item` reference
- 4 bytes: JVM padding
- **Total per element:** **24 to 32 bytes of pure pointer overhead**, plus the element object itself!
For 1,000,000 elements, `LinkedList` consumes ~32 MB of pointer overhead, whereas `ArrayList` takes ~4 MB for reference pointers.

---

## 4. CopyOnWriteArrayList Internals

### How It Works
`CopyOnWriteArrayList` is a thread-safe variant of `List` where **all mutative operations (`add`, `set`, `remove`) are implemented by creating a fresh copy of the underlying array**.

```java
public class CopyOnWriteArrayList<E> implements List<E>, RandomAccess, Cloneable, Serializable {
    final transient Object lock = new Object(); // Synchronizes writes
    private transient volatile Object[] array;  // Volatile ensures visibility of new array

    public E get(int index) {
        return elementAt(getArray(), index); // Lock-free read!
    }

    public boolean add(E e) {
        synchronized (lock) { // Mutex lock on writes
            Object[] es = getArray();
            int len = es.length;
            Object[] newElements = Arrays.copyOf(es, len + 1); // Copy entire array
            newElements[len] = e;
            setArray(newElements); // Volatile write publishes changes atomically
            return true;
        }
    }
}
```

### Snapshot Iterators (Fail-Safe)
The iterator retains a reference to the array state at the moment the iterator was constructed:
- It **never** throws `ConcurrentModificationException`.
- It reflects the state at iterator creation and does not see subsequent modifications.
- Calling `it.remove()` throws `UnsupportedOperationException`.

**Ideal Use Cases:**
1. Event listeners and observer registries (registered once, notified constantly).
2. Read-heavy caches where writes occur infrequently (99% reads, 1% writes).

---

## 5. Fail-Fast vs Fail-Safe Mechanics & `modCount`

### How Fail-Fast Works
```java
// Inside ArrayList.Itr
int expectedModCount = modCount;

final void checkForComodification() {
    if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
}
```

1. Every structural change (`add`, `remove`, `clear`, `trimToSize`) increments `modCount++`.
2. When creating an `Iterator`, `expectedModCount` is initialized to the current `modCount`.
3. On every `next()` or `remove()` invocation, the iterator verifies:
   `if (modCount != expectedModCount) throw new ConcurrentModificationException();`
4. If code modifies the list structurally outside the iterator's own `.remove()` method, `modCount` diverges from `expectedModCount`, triggering the exception immediately.

> **Crucial Interview Point:** Fail-fast behavior is **best-effort**. It is not guaranteed in concurrent, multi-threaded scenarios without synchronization. It is a bug detector, not a concurrency safety mechanism!

---

## 6. SubList Deep Dive & Memory Leak Hazards

`list.subList(fromIndex, toIndex)` returns a **view** backed by the original list, implemented as an inner class `java.util.ArrayList.SubList`.

```java
List<String> original = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
List<String> sub = original.subList(1, 3); // ["B", "C"]

// Modifying subList updates original:
sub.add("X"); 
// original is now: ["A", "B", "C", "X", "D", "E"]

// But modifying original directly invalidates the subList!
original.add("Z");
sub.get(0); // ❌ Throws ConcurrentModificationException!
```

### The SubList Memory Leak
```java
public List<String> getSubset() {
    List<String> largeList = loadMillionRecords(); // 1,000,000 objects
    return largeList.subList(0, 10); // ❌ DANGER: SubList holds reference to largeList!
}
```
Because `SubList` holds a reference to `ArrayList.this`, the entire million-element backing array cannot be garbage collected!
**Fix:**
```java
return new ArrayList<>(largeList.subList(0, 10)); // Decouples from the large array
```

---

## 7. List Creation APIs Compared

| Method | Return Type | Modifiability | Allows `null`? | Backed by Array? | JVM Version |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `new ArrayList<>()` | `ArrayList<E>` | Fully mutable | ✅ Yes | No | 1.2+ |
| `Arrays.asList(T...)` | `Arrays$ArrayList<E>` | Fixed size (elements can be mutated via `set()`, but cannot `add`/`remove`) | ✅ Yes | ✅ Yes (changing array changes list!) | 1.2+ |
| `Collections.unmodifiableList(list)` | `UnmodifiableRandomAccessList` | Read-only view (mutating throws UOE) | ✅ (if source has) | Delegates to backing list (underlying changes reflect!) | 1.2+ |
| `List.of(e1, e2...)` | `ImmutableCollections.ListN` | Strictly immutable (deep immutable structure) | ❌ Throws NPE | No | 9+ |
| `List.copyOf(collection)` | `ImmutableCollections.ListN` | Strictly immutable snapshot copy | ❌ Throws NPE | No | 10+ |

```java
// Arrays.asList Trap:
String[] arr = {"One", "Two"};
List<String> list = Arrays.asList(arr);
arr[0] = "Modified";
System.out.println(list.get(0)); // Prints "Modified"! Backed by arr!
list.add("Three"); // ❌ UnsupportedOperationException (fixed-size)

// List.of Trap:
List<String> immutableList = List.of("A", null); // ❌ NullPointerException!
```

---

## 8. Common Traps & Antipatterns

### ❌ Trap 1: Removing Elements Inside an Enhanced For-Loop
```java
// ❌ WRONG: Throws ConcurrentModificationException
for (String item : list) {
    if (item.equals("deleteMe")) {
        list.remove(item); // Modifies modCount without updating iterator!
    }
}

// ✅ CORRECT Option 1: Java 8+ removeIf (Fastest & cleanest)
list.removeIf(item -> item.equals("deleteMe"));

// ✅ CORRECT Option 2: Iterator.remove()
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("deleteMe")) {
        it.remove(); // Updates both modCount and expectedModCount!
    }
}
```

### ❌ Trap 2: `remove(int index)` vs `remove(Object o)` with Integers
```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30, 40));
numbers.remove(1); // Removes index 1 (value 20), NOT the number 1!
// To remove the integer value 10:
numbers.remove(Integer.valueOf(10)); // Calls remove(Object)
```

### ❌ Trap 3: Calling `Arrays.asList()` on Primitive Arrays
```java
int[] nums = {1, 2, 3};
List<int[]> list = Arrays.asList(nums); // ❌ List of int[] array, size is 1, not 3!
// Primitive arrays cannot be generic type arguments (T[]).
// ✅ Correct:
List<Integer> list = Arrays.stream(nums).boxed().toList();
```

---

## 9. Senior / FAANG Interview Questions & Answers

### Q1: Why doesn't `ArrayList` shrink its capacity automatically when you remove elements?
**Answer:**
Shrinking during removal would cause "capacity thrashing". If an array was repeatedly resized down on remove and resized up on add, every operation would require an $O(n)$ allocation and array copy. To reclaim unused space intentionally, the developer must explicitly invoke `list.trimToSize()`.

### Q2: What happens internally when `elementData[--size] = null` is called in `remove()`?
**Answer:**
It avoids a "loitering object" / memory leak. If the reference is not nulled out, the backing array retains a strong reference to the deleted object even though `size` was decremented. As long as the `ArrayList` is reachable, the deleted object can never be garbage collected.

### Q3: When is `LinkedList` actually faster than `ArrayList` in modern hardware?
**Answer:**
Practically **almost never**. While `LinkedList` offers theoretical $O(1)$ insertions at the head or tail, the memory allocation overhead of creating a `Node` object and the CPU cache penalties from pointer chasing across scattered heap memory dwarf the time `ArrayList` takes to perform `System.arraycopy`. `ArrayList` consistently outperforms `LinkedList` in real-world benchmarks, even for additions at the start for lists up to tens of thousands of elements. If you need efficient FIFO queue or Deque operations, prefer `ArrayDeque` over `LinkedList`.

### Q4: Why is `RandomAccess` a marker interface with no methods?
**Answer:**
It signals to generic algorithms (such as `Collections.binarySearch`) that positional lookup is $O(1)$ rather than $O(n)$.
```java
if (list instanceof RandomAccess) {
    for (int i=0; i<list.size(); i++) list.get(i); // O(1) indexed
} else {
    for (E e : list) ... // Use iterator to avoid O(n^2) loop
}
```

### Q5: How does `Collections.synchronizedList(list)` differ from `CopyOnWriteArrayList`?
**Answer:**
- `Collections.synchronizedList()` wraps all methods with a `synchronized (mutex)` block. Both reads and writes acquire the monitor. Iteration is NOT synchronized automatically; you must manually wrap iterations in `synchronized (syncList) { ... }` or risk `ConcurrentModificationException`.
- `CopyOnWriteArrayList` permits concurrent, lock-free reads and snapshot iterators that never block writes. Writes clone the array. Ideal for 95%+ read workloads.

---

## 10. Quick Reference Cheat Sheet

```
┌──────────────────────────┬─────────────────────────────┬──────────────────────────────┐
│ Criteria                 │ ArrayList                   │ LinkedList                   │
├──────────────────────────┼─────────────────────────────┼──────────────────────────────┤
│ Default Capacity         │ 10 (on first add)           │ N/A (no internal array)      │
│ Growth Factor            │ 1.5x (oldCap + oldCap >> 1) │ None                         │
│ Random Access            │ O(1)                        │ O(n)                         │
│ Insertion at End         │ Amortized O(1)              │ O(1)                         │
│ Insertion at Front       │ O(n)                        │ O(1)                         │
│ Insertion in Middle      │ O(n)                        │ O(n) search + O(1) link      │
│ Cache Efficiency         │ High (L1/L2 cache locality) │ Low (pointer chasing)        │
│ Memory Overhead          │ Low (~4B per element ref)   │ High (~24-32B node overhead) │
│ Queue/Deque Suitability  │ Poor (use ArrayDeque)       │ Yes (implements Deque)       │
└──────────────────────────┴─────────────────────────────┴──────────────────────────────┘
```
