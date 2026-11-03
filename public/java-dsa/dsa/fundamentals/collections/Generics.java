package dsa.fundamentals.collections;

import java.util.*;

/**
 * Basic Generics Demonstration
 * 
 * TOPICS COVERED:
 * 1. Generic classes and methods
 * 2. Type safety benefits
 * 3. Generic collections usage
 * 4. Basic wildcard usage
 * 5. Generic interfaces
 * 
 * Note: For advanced generics topics, see GenericsAdvanced.java
 */
public class Generics {
    
    public static void main(String[] args) {
        demonstrateBasicGenerics();
        demonstrateGenericCollections();
        demonstrateGenericMethods();
        demonstrateWildcardBasics();
        demonstrateTypeSafety();
    }
    
    /**
     * Basic generic class usage
     */
    public static void demonstrateBasicGenerics() {
        System.out.println("=== BASIC GENERICS ===\n");
        
        // Generic pair class
        Pair<String, String> stringPair = new Pair<>();
        stringPair.setFirst("Hello");
        stringPair.setSecond("World");
        
        System.out.println("String pair: " + stringPair.getFirst() + " " + stringPair.getSecond());
        
        // Different type parameters
        Pair<String, Integer> nameAge = new Pair<>();
        nameAge.setFirst("Alice");
        nameAge.setSecond(25);
        
        System.out.println("Name-Age pair: " + nameAge.getFirst() + " is " + nameAge.getSecond() + " years old");
        
        // Generic box
        Box<Integer> intBox = new Box<>(42);
        Box<String> stringBox = new Box<>("Generic String");
        
        System.out.println("Integer box: " + intBox.getContent());
        System.out.println("String box: " + stringBox.getContent());
        
        System.out.println();
    }
    
    /**
     * Generic collections demonstration
     */
    public static void demonstrateGenericCollections() {
        System.out.println("=== GENERIC COLLECTIONS ===\n");
        
        // Type-safe collections
        List<String> stringList = new ArrayList<>();
        stringList.add("Apple");
        stringList.add("Banana");
        stringList.add("Cherry");
        
        System.out.println("String list: " + stringList);
        
        // No casting required
        for (String fruit : stringList) {
            System.out.println("Fruit: " + fruit.toUpperCase());
        }
        
        // Generic Map
        Map<String, Integer> scores = new HashMap<>();
        scores.put("Alice", 95);
        scores.put("Bob", 87);
        scores.put("Charlie", 92);
        
        System.out.println("Scores: " + scores);
        
        // Type-safe iteration
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        
        System.out.println();
    }
    
    /**
     * Generic methods demonstration
     */
    public static void demonstrateGenericMethods() {
        System.out.println("=== GENERIC METHODS ===\n");
        
        // Generic method usage
        String[] names = {"Alice", "Bob", "Charlie"};
        Integer[] numbers = {1, 2, 3, 4, 5};
        
        System.out.println("First name: " + getFirst(names));
        System.out.println("First number: " + getFirst(numbers));
        
        // Swapping elements
        String[] fruits = {"Apple", "Banana"};
        System.out.println("Before swap: " + Arrays.toString(fruits));
        swap(fruits, 0, 1);
        System.out.println("After swap: " + Arrays.toString(fruits));
        
        // Generic utility methods
        List<String> list1 = Arrays.asList("A", "B", "C");
        List<String> list2 = Arrays.asList("D", "E", "F");
        
        List<String> combined = combine(list1, list2);
        System.out.println("Combined lists: " + combined);
        
        System.out.println();
    }
    
    /**
     * Basic wildcard usage
     */
    public static void demonstrateWildcardBasics() {
        System.out.println("=== BASIC WILDCARDS ===\n");
        
        List<String> stringList = Arrays.asList("Hello", "World");
        List<Integer> intList = Arrays.asList(1, 2, 3);
        List<Double> doubleList = Arrays.asList(1.1, 2.2, 3.3);
        
        // Unbounded wildcard
        printList(stringList);
        printList(intList);
        printList(doubleList);
        
        // Upper bounded wildcard
        System.out.println("Sum of integers: " + sumNumbers(intList));
        System.out.println("Sum of doubles: " + sumNumbers(doubleList));
        
        System.out.println();
    }
    
    /**
     * Type safety demonstration
     */
    public static void demonstrateTypeSafety() {
        System.out.println("=== TYPE SAFETY BENEFITS ===\n");
        
        System.out.println("WITHOUT GENERICS (Raw types - avoid in modern code):");
        @SuppressWarnings("rawtypes")
        List rawList = new ArrayList();
        rawList.add("String");
        rawList.add(42); // Different types mixed!
        
        // This would cause ClassCastException at runtime
        try {
            @SuppressWarnings("unchecked")
            String item = (String) rawList.get(1); // Runtime error!
        } catch (ClassCastException e) {
            System.out.println("Runtime error with raw types: " + e.getMessage());
        }
        
        System.out.println("\nWITH GENERICS (Type safe):");
        List<String> typeSafeList = new ArrayList<>();
        typeSafeList.add("String1");
        typeSafeList.add("String2");
        // typeSafeList.add(42); // Compile-time error!
        
        String safeItem = typeSafeList.get(0); // No casting needed
        System.out.println("Safe retrieval: " + safeItem);
        
        System.out.println("\nBENEFITS OF GENERICS:");
        System.out.println("✓ Compile-time type checking");
        System.out.println("✓ No casting required");
        System.out.println("✓ Better code readability");
        System.out.println("✓ Enhanced IDE support");
        
        System.out.println();
    }
    
    // Generic methods
    
    /**
     * Generic method to get first element
     */
    public static <T> T getFirst(T[] array) {
        return array.length > 0 ? array[0] : null;
    }
    
    /**
     * Generic method to swap elements
     */
    public static <T> void swap(T[] array, int i, int j) {
        if (i >= 0 && i < array.length && j >= 0 && j < array.length) {
            T temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }
    
    /**
     * Generic method to combine two lists
     */
    public static <T> List<T> combine(List<T> list1, List<T> list2) {
        List<T> result = new ArrayList<>(list1);
        result.addAll(list2);
        return result;
    }
    
    /**
     * Method with unbounded wildcard
     */
    public static void printList(List<?> list) {
        System.out.print("List contents: ");
        for (Object item : list) {
            System.out.print(item + " ");
        }
        System.out.println();
    }
    
    /**
     * Method with upper bounded wildcard
     */
    public static double sumNumbers(List<? extends Number> numbers) {
        double sum = 0.0;
        for (Number num : numbers) {
            sum += num.doubleValue();
        }
        return sum;
    }
}

/**
 * Generic pair class
 */
class Pair<T, U> {
    private T first;
    private U second;
    
    public Pair() {}
    
    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }
    
    public T getFirst() { return first; }
    public void setFirst(T first) { this.first = first; }
    
    public U getSecond() { return second; }
    public void setSecond(U second) { this.second = second; }
    
    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }
}

/**
 * Generic box class
 */
class Box<T> {
    private T content;
    
    public Box(T content) {
        this.content = content;
    }
    
    public T getContent() { return content; }
    public void setContent(T content) { this.content = content; }
    
    @Override
    public String toString() {
        return "Box[" + content + "]";
    }
}

