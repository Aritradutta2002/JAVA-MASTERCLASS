package dsa.fundamentals.collections;

import java.util.*;

/**
 * Wrapper Classes and Autoboxing/Unboxing Demonstration
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. Primitive vs Wrapper classes
 * 2. Autoboxing and Unboxing
 * 3. Performance implications
 * 4. Wrapper class caching
 * 5. Collections with primitives
 * 6. Common pitfalls and best practices
 */
public class Wrapper {
    
    public static void main(String[] args) {
        demonstratePrimitivesVsWrappers();
        demonstrateAutoboxingUnboxing();
        demonstrateWrapperCaching();
        demonstrateCollectionsWithWrappers();
        demonstratePerformanceImpact();
        demonstrateCommonPitfalls();
        showBestPractices();
    }
    
    /**
     * Primitives vs Wrapper classes
     */
    public static void demonstratePrimitivesVsWrappers() {
        System.out.println("=== PRIMITIVES vs WRAPPERS ===\n");
        
        // Primitive types
        int primitiveInt = 42;
        double primitiveDouble = 3.14;
        boolean primitiveBoolean = true;
        char primitiveChar = 'A';
        
        // Wrapper classes
        Integer wrapperInt = Integer.valueOf(42);
        Double wrapperDouble = Double.valueOf(3.14);
        Boolean wrapperBoolean = Boolean.valueOf(true);
        Character wrapperChar = Character.valueOf('A');
        
        System.out.println("Primitive int: " + primitiveInt);
        System.out.println("Wrapper Integer: " + wrapperInt);
        
        System.out.println("\nPRIMITIVE TYPES AND THEIR WRAPPERS:");
        System.out.println("┌─────────────┬─────────────────┬─────────────┬─────────────────┐");
        System.out.println("│ Primitive   │ Wrapper Class   │ Size (bits) │ Range           │");
        System.out.println("├─────────────┼─────────────────┼─────────────┼─────────────────┤");
        System.out.println("│ byte        │ Byte            │ 8           │ -128 to 127     │");
        System.out.println("│ short       │ Short           │ 16          │ -32,768 to 32,767│");
        System.out.println("│ int         │ Integer         │ 32          │ -2³¹ to 2³¹-1   │");
        System.out.println("│ long        │ Long            │ 64          │ -2⁶³ to 2⁶³-1   │");
        System.out.println("│ float       │ Float           │ 32          │ IEEE 754        │");
        System.out.println("│ double      │ Double          │ 64          │ IEEE 754        │");
        System.out.println("│ char        │ Character       │ 16          │ 0 to 65,535     │");
        System.out.println("│ boolean     │ Boolean         │ 1           │ true/false      │");
        System.out.println("└─────────────┴─────────────────┴─────────────┴─────────────────┘");
        
        // Key differences
        System.out.println("\nKEY DIFFERENCES:");
        System.out.println("✓ Primitives: Stored on stack, faster, no methods");
        System.out.println("✓ Wrappers: Objects on heap, slower, have methods, can be null");
        System.out.println("✓ Collections only work with objects (wrapper classes)");
        
        System.out.println();
    }
    
    /**
     * Autoboxing and Unboxing demonstration
     */
    public static void demonstrateAutoboxingUnboxing() {
        System.out.println("=== AUTOBOXING AND UNBOXING ===\n");
        
        System.out.println("AUTOBOXING (primitive to wrapper):");
        
        // Autoboxing - automatic conversion from primitive to wrapper
        Integer autoboxed = 100; // int to Integer
        Double autoboxedDouble = 3.14; // double to Double
        Boolean autoboxedBoolean = true; // boolean to Boolean
        
        System.out.println("int 100 autoboxed to Integer: " + autoboxed);
        System.out.println("double 3.14 autoboxed to Double: " + autoboxedDouble);
        System.out.println("boolean true autoboxed to Boolean: " + autoboxedBoolean);
        
        System.out.println("\nUNBOXING (wrapper to primitive):");
        
        // Unboxing - automatic conversion from wrapper to primitive
        Integer wrapperValue = Integer.valueOf(200);
        int unboxed = wrapperValue; // Integer to int
        
        Double wrapperDoubleValue = Double.valueOf(2.71);
        double unboxedDouble = wrapperDoubleValue; // Double to double
        
        System.out.println("Integer 200 unboxed to int: " + unboxed);
        System.out.println("Double 2.71 unboxed to double: " + unboxedDouble);
        
        // In collections
        System.out.println("\nIN COLLECTIONS:");
        List<Integer> numbers = new ArrayList<>();
        numbers.add(1); // Autoboxing: int to Integer
        numbers.add(2);
        numbers.add(3);
        
        int first = numbers.get(0); // Unboxing: Integer to int
        System.out.println("Retrieved and unboxed: " + first);
        
        // In arithmetic operations
        Integer a = 10;
        Integer b = 20;
        Integer sum = a + b; // Unboxing for arithmetic, then autoboxing for assignment
        System.out.println("Wrapper arithmetic: " + a + " + " + b + " = " + sum);
        
        System.out.println();
    }
    
    /**
     * Wrapper class caching demonstration
     */
    public static void demonstrateWrapperCaching() {
        System.out.println("=== WRAPPER CLASS CACHING ===\n");
        
        System.out.println("INTEGER CACHING (-128 to 127):");
        
        // Integer caching for values -128 to 127
        Integer a1 = 100;
        Integer a2 = 100;
        Integer b1 = 200;
        Integer b2 = 200;
        
        System.out.println("Integer a1 = 100, a2 = 100");
        System.out.println("a1 == a2: " + (a1 == a2)); // true (cached)
        System.out.println("a1.equals(a2): " + a1.equals(a2)); // true
        
        System.out.println("\nInteger b1 = 200, b2 = 200");
        System.out.println("b1 == b2: " + (b1 == b2)); // false (not cached)
        System.out.println("b1.equals(b2): " + b1.equals(b2)); // true
        
        // Other wrapper caching
        System.out.println("\nOTHER WRAPPER CACHING:");
        
        Boolean bool1 = true;
        Boolean bool2 = true;
        System.out.println("Boolean true caching: " + (bool1 == bool2)); // true
        
        Character char1 = 'A';
        Character char2 = 'A';
        System.out.println("Character 'A' caching: " + (char1 == char2)); // true (0-127)
        
        Byte byte1 = 50;
        Byte byte2 = 50;
        System.out.println("Byte 50 caching: " + (byte1 == byte2)); // true (all bytes cached)
        
        System.out.println("\nCACHING RULES:");
        System.out.println("✓ Integer: -128 to 127");
        System.out.println("✓ Byte: All values (-128 to 127)");
        System.out.println("✓ Short: -128 to 127");
        System.out.println("✓ Long: -128 to 127");
        System.out.println("✓ Character: 0 to 127");
        System.out.println("✓ Boolean: true and false");
        System.out.println("✗ Float and Double: No caching");
        
        System.out.println();
    }
    
    /**
     * Collections with wrapper classes
     */
    public static void demonstrateCollectionsWithWrappers() {
        System.out.println("=== COLLECTIONS WITH WRAPPERS ===\n");
        
        // List with Integer wrappers
        List<Integer> integerList = new ArrayList<>();
        integerList.add(10); // Autoboxing
        integerList.add(20);
        integerList.add(30);
        
        System.out.println("Integer list: " + integerList);
        
        // Sum using streams (unboxing happens automatically)
        int sum = integerList.stream().mapToInt(Integer::intValue).sum();
        System.out.println("Sum: " + sum);
        
        // Map with wrapper keys and values
        Map<Integer, String> numberWords = new HashMap<>();
        numberWords.put(1, "one");   // Autoboxing for key
        numberWords.put(2, "two");
        numberWords.put(3, "three");
        
        System.out.println("Number words map: " + numberWords);
        
        // Set with Double wrappers
        Set<Double> prices = new TreeSet<>();
        prices.add(19.99); // Autoboxing
        prices.add(29.99);
        prices.add(9.99);
        
        System.out.println("Sorted prices: " + prices);
        
        // Working with nulls (wrapper advantage)
        List<Integer> numbersWithNulls = new ArrayList<>();
        numbersWithNulls.add(1);
        numbersWithNulls.add(null); // Possible with wrappers
        numbersWithNulls.add(3);
        
        System.out.println("List with nulls: " + numbersWithNulls);
        
        // Safe processing of nulls
        for (Integer num : numbersWithNulls) {
            if (num != null) {
                System.out.println("Processing: " + num);
            } else {
                System.out.println("Found null value");
            }
        }
        
        System.out.println();
    }
    
    /**
     * Performance impact demonstration
     */
    public static void demonstratePerformanceImpact() {
        System.out.println("=== PERFORMANCE IMPACT ===\n");
        
        final int ITERATIONS = 1_000_000;
        
        // Primitive array performance
        long startTime = System.nanoTime();
        int[] primitiveArray = new int[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            primitiveArray[i] = i;
        }
        long primitiveTime = System.nanoTime() - startTime;
        
        // Wrapper collection performance
        startTime = System.nanoTime();
        List<Integer> wrapperList = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            wrapperList.add(i); // Autoboxing overhead
        }
        long wrapperTime = System.nanoTime() - startTime;
        
        System.out.println("Performance comparison (" + ITERATIONS + " elements):");
        System.out.println("Primitive array: " + primitiveTime / 1_000_000 + " ms");
        System.out.println("Wrapper collection: " + wrapperTime / 1_000_000 + " ms");
        System.out.println("Wrapper overhead: " + (wrapperTime / (double) primitiveTime) + "x slower");
        
        // Memory usage comparison
        System.out.println("\nMEMORY USAGE:");
        System.out.println("int primitive: 4 bytes");
        System.out.println("Integer wrapper: ~16 bytes (object header + int value + padding)");
        System.out.println("Memory overhead: ~4x more memory");
        
        System.out.println();
    }
    
    /**
     * Common pitfalls and gotchas
     */
    public static void demonstrateCommonPitfalls() {
        System.out.println("=== COMMON PITFALLS ===\n");
        
        // Pitfall 1: NullPointerException during unboxing
        System.out.println("PITFALL 1: NullPointerException during unboxing");
        Integer nullInteger = null;
        try {
            int primitive = nullInteger; // NPE here!
        } catch (NullPointerException e) {
            System.out.println("NPE when unboxing null: " + e.getClass().getSimpleName());
        }
        
        // Pitfall 2: == vs equals with caching
        System.out.println("\nPITFALL 2: == vs equals() with caching");
        Integer x = 127;
        Integer y = 127;
        Integer a = 128;
        Integer b = 128;
        
        System.out.println("127 == 127: " + (x == y)); // true (cached)
        System.out.println("128 == 128: " + (a == b)); // false (not cached)
        System.out.println("Always use equals(): " + a.equals(b)); // true
        
        // Pitfall 3: Performance issues in loops
        System.out.println("\nPITFALL 3: Performance in loops");
        System.out.println("Bad: Using Integer in arithmetic-heavy loops");
        System.out.println("Good: Use primitives for arithmetic, convert when needed");
        
        // Pitfall 4: Unnecessary boxing
        System.out.println("\nPITFALL 4: Unnecessary boxing");
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        
        // Bad: unnecessary boxing/unboxing
        Integer badSum = 0;
        for (Integer num : numbers) {
            badSum += num; // Multiple box/unbox operations
        }
        
        // Better: minimize boxing
        int goodSum = 0;
        for (Integer num : numbers) {
            goodSum += num; // Only unboxing
        }
        
        System.out.println("Both approaches give same result: " + badSum + " = " + goodSum);
        
        System.out.println();
    }
    
    /**
     * Best practices and recommendations
     */
    public static void showBestPractices() {
        System.out.println("=== BEST PRACTICES ===\n");
        
        System.out.println("WHEN TO USE PRIMITIVES:");
        System.out.println("✓ Local variables and parameters");
        System.out.println("✓ Array elements for performance");
        System.out.println("✓ Arithmetic-heavy operations");
        System.out.println("✓ Loop counters and indices");
        
        System.out.println("\nWHEN TO USE WRAPPERS:");
        System.out.println("✓ Collection elements");
        System.out.println("✓ Generic type parameters");
        System.out.println("✓ When null values are needed");
        System.out.println("✓ Method return types that might be null");
        
        System.out.println("\nGENERAL BEST PRACTICES:");
        System.out.println("✓ Always use equals() for wrapper comparison");
        System.out.println("✓ Be aware of caching ranges");
        System.out.println("✓ Handle null values properly");
        System.out.println("✓ Use primitives for performance-critical code");
        System.out.println("✓ Consider primitive collection libraries for large datasets");
        
        System.out.println("\nAVOID:");
        System.out.println("✗ Using == for wrapper comparison (except cached values)");
        System.out.println("✗ Unnecessary autoboxing in loops");
        System.out.println("✗ Mixing primitives and wrappers unnecessarily");
        System.out.println("✗ Unboxing without null checks");
        
        System.out.println();
    }
}

