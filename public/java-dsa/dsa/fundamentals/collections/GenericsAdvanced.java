package dsa.fundamentals.collections;

import java.util.*;

/**
 * Advanced Generics Demonstration for Interview Preparation
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. Generic classes, interfaces, and methods
 * 2. Wildcards (?, extends, super)
 * 3. Type erasure and its implications
 * 4. Bounded type parameters
 * 5. Generic restrictions and limitations
 * 6. PECS principle (Producer Extends, Consumer Super)
 */
public class GenericsAdvanced {
    
    public static void main(String[] args) {
        demonstrateBasicGenerics();
        demonstrateWildcards();
        explainPECSPrinciple();
        demonstrateBoundedTypeParameters();
        explainTypeErasure();
        showGenericRestrictions();
        demonstrateAdvancedPatterns();
    }
    
    /**
     * Basic generics demonstration
     */
    public static void demonstrateBasicGenerics() {
        System.out.println("=== BASIC GENERICS ===\n");
        
        // Generic class
        GenericBox<String> stringBox = new GenericBox<>("Hello Generics");
        GenericBox<Integer> intBox = new GenericBox<>(42);
        
        System.out.println("String box: " + stringBox.get());
        System.out.println("Integer box: " + intBox.get());
        
        // Generic method
        String[] names = {"Alice", "Bob", "Charlie"};
        Integer[] numbers = {1, 2, 3, 4, 5};
        
        System.out.println("First name: " + getFirst(names));
        System.out.println("First number: " + getFirst(numbers));
        
        // Multiple type parameters
        GenericPair<String, Integer> nameAge = new GenericPair<>("John", 25);
        System.out.println("Name-Age pair: " + nameAge);
        
        // Diamond operator (Java 7+)
        List<String> list = new ArrayList<>(); // Type inferred
        Map<String, List<Integer>> complexMap = new HashMap<>();
    }
    
    /**
     * Wildcards demonstration
     */
    public static void demonstrateWildcards() {
        System.out.println("\n=== WILDCARDS ===\n");
        
        List<Integer> integers = Arrays.asList(1, 2, 3, 4, 5);
        List<Double> doubles = Arrays.asList(1.1, 2.2, 3.3);
        List<Number> numbers = Arrays.asList(10, 20.5, 30);
        
        // 1. Unbounded wildcard (?)
        printList(integers);
        printList(doubles);
        
        // 2. Upper bounded wildcard (? extends)
        System.out.println("Sum of integers: " + sumNumbers(integers));
        System.out.println("Sum of doubles: " + sumNumbers(doubles));
        System.out.println("Sum of numbers: " + sumNumbers(numbers));
        
        // 3. Lower bounded wildcard (? super)
        List<Number> numberList = new ArrayList<>();
        addNumbers(numberList);
        System.out.println("Added numbers: " + numberList);
        
        List<Object> objectList = new ArrayList<>();
        addNumbers(objectList);
        System.out.println("Added to object list: " + objectList);
        
        // Comparison
        System.out.println("\nWILDCARD TYPES:");
        System.out.println("List<?>          - Can read as Object, cannot add (except null)");
        System.out.println("List<? extends T> - Can read as T, cannot add (except null)");
        System.out.println("List<? super T>   - Can add T and subtypes, read as Object");
    }
    
    /**
     * PECS Principle explanation
     */
    public static void explainPECSPrinciple() {
        System.out.println("\n=== PECS PRINCIPLE ===\n");
        System.out.println("PECS: Producer Extends, Consumer Super");
        System.out.println("- Use 'extends' when you only READ from a structure (Producer)");
        System.out.println("- Use 'super' when you only WRITE to a structure (Consumer)");
        System.out.println("- Use exact type when you both read and write");
        
        // Producer example - reading data
        List<Integer> intList = Arrays.asList(1, 2, 3);
        List<Double> doubleList = Arrays.asList(1.1, 2.2, 3.3);
        
        // We're reading (producing) numbers from the list
        copyNumbers(intList, new ArrayList<>());
        copyNumbers(doubleList, new ArrayList<>());
        
        // Consumer example - writing data
        List<Number> targetList = new ArrayList<>();
        List<Object> objectTargetList = new ArrayList<>();
        
        // We're writing (consuming) numbers to the list
        fillWithNumbers(targetList);
        fillWithNumbers(objectTargetList);
        
        System.out.println("Filled number list: " + targetList);
        System.out.println("Filled object list: " + objectTargetList);
    }
    
    /**
     * Bounded type parameters
     */
    public static void demonstrateBoundedTypeParameters() {
        System.out.println("\n=== BOUNDED TYPE PARAMETERS ===\n");
        
        // Single bound
        NumberBox<Integer> intBox = new NumberBox<>(42);
        NumberBox<Double> doubleBox = new NumberBox<>(3.14);
        
        System.out.println("Integer box value: " + intBox.getValue());
        System.out.println("Double box value: " + doubleBox.getValue());
        
        // Multiple bounds
        ComparableBox<Integer> intComparableBox = new ComparableBox<>(100);
        ComparableBox<Double> doubleComparableBox = new ComparableBox<>(3.14);
        
        System.out.println("Integer comparison result: " + 
                          intComparableBox.compareTo(new ComparableBox<>(50)));
        
        // Method with bounded type parameter
        Integer[] intArray = {3, 1, 4, 1, 5, 9, 2, 6};
        String[] stringArray = {"banana", "apple", "cherry"};
        
        System.out.println("Max integer: " + findMax(intArray));
        System.out.println("Max string: " + findMax(stringArray));
    }
    
    /**
     * Type erasure explanation
     */
    public static void explainTypeErasure() {
        System.out.println("\n=== TYPE ERASURE ===\n");
        
        System.out.println("TYPE ERASURE RULES:");
        System.out.println("1. Generic type parameters are erased at runtime");
        System.out.println("2. Unbounded types become Object");
        System.out.println("3. Bounded types become their bound");
        System.out.println("4. Bridge methods are created for polymorphism");
        
        List<String> stringList = new ArrayList<>();
        List<Integer> integerList = new ArrayList<>();
        
        // At runtime, both are just List
        System.out.println("String list class: " + stringList.getClass());
        System.out.println("Integer list class: " + integerList.getClass());
        System.out.println("Are classes equal? " + 
                          (stringList.getClass() == integerList.getClass()));
        
        // Type erasure examples
        System.out.println("\nCOMPILE-TIME vs RUNTIME:");
        System.out.println("Compile-time: List<String>, List<Integer>");
        System.out.println("Runtime: List, List (types erased)");
        
        // Cannot check generic type at runtime
        try {
            // This won't compile:
            // if (stringList instanceof List<String>) { ... }
            
            // This is what you can do:
            if (stringList instanceof List) {
                System.out.println("Can only check raw type at runtime");
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Generic restrictions and limitations
     */
    public static void showGenericRestrictions() {
        System.out.println("\n=== GENERIC RESTRICTIONS ===\n");
        
        System.out.println("THINGS YOU CANNOT DO WITH GENERICS:");
        System.out.println("1. Create instances of type parameters");
        System.out.println("2. Create arrays of parameterized types");
        System.out.println("3. Use primitives as type arguments");
        System.out.println("4. Create static fields of type parameters");
        System.out.println("5. Cast or instanceof with parameterized types");
        System.out.println("6. Overload with different generic types");
        
        // Demonstrate some restrictions
        
        // 1. Cannot create generic array directly
        // List<String>[] arrayOfLists = new List<String>[10]; // Compile error
        
        // Workaround for generic arrays
        @SuppressWarnings("unchecked")
        List<String>[] arrayOfLists = (List<String>[]) new List[10];
        arrayOfLists[0] = new ArrayList<>();
        arrayOfLists[0].add("First list");
        
        System.out.println("Generic array workaround: " + arrayOfLists[0]);
        
        // 2. Must use wrapper classes, not primitives
        List<Integer> intList = new ArrayList<>(); // Not List<int>
        List<Boolean> boolList = new ArrayList<>(); // Not List<boolean>
        
        System.out.println("Must use wrapper classes for primitives");
        
        // 3. Heap pollution example
        demonstrateHeapPollution();
    }
    
    /**
     * Advanced generic patterns
     */
    public static void demonstrateAdvancedPatterns() {
        System.out.println("\n=== ADVANCED PATTERNS ===\n");
        
        // 1. Self-bounded generics (F-bounded polymorphism)
        System.out.println("1. SELF-BOUNDED GENERICS:");
        Circle circle = new Circle(5.0);
        System.out.println("Circle area: " + circle.area());
        System.out.println("Circle comparison: " + circle.compareTo(new Circle(3.0)));
        
        // 2. Generic factory pattern
        System.out.println("\n2. GENERIC FACTORY PATTERN:");
        Factory<String> stringFactory = () -> "New String";
        Factory<List<Integer>> listFactory = ArrayList::new;
        
        String product1 = stringFactory.create();
        List<Integer> product2 = listFactory.create();
        
        System.out.println("Factory created: " + product1);
        System.out.println("Factory created list: " + product2.getClass().getSimpleName());
        
        // 3. Generic builder pattern
        System.out.println("\n3. GENERIC BUILDER PATTERN:");
        GenericPerson person = new GenericPersonBuilder()
                .name("John Doe")
                .age(30)
                .email("john@example.com")
                .build();
        
        System.out.println("Built person: " + person);
        
        // 4. Type witness pattern
        List<String> witnessedList = Collections.<String>emptyList();
        System.out.println("Type witness example: " + witnessedList.getClass());
    }
    
    // Helper methods and classes
    
    /**
     * Generic method example
     */
    public static <T> T getFirst(T[] array) {
        return array.length > 0 ? array[0] : null;
    }
    
    /**
     * Unbounded wildcard method
     */
    public static void printList(List<?> list) {
        for (Object item : list) {
            System.out.print(item + " ");
        }
        System.out.println();
    }
    
    /**
     * Upper bounded wildcard method (Producer)
     */
    public static double sumNumbers(List<? extends Number> numbers) {
        double sum = 0.0;
        for (Number num : numbers) {
            sum += num.doubleValue();
        }
        return sum;
    }
    
    /**
     * Lower bounded wildcard method (Consumer)
     */
    public static void addNumbers(List<? super Integer> list) {
        list.add(10);
        list.add(20);
        list.add(30);
    }
    
    /**
     * PECS Producer example
     */
    public static <T> void copyNumbers(List<? extends T> source, List<T> dest) {
        for (T item : source) {
            dest.add(item);
        }
    }
    
    /**
     * PECS Consumer example
     */
    public static void fillWithNumbers(List<? super Integer> list) {
        list.add(1);
        list.add(2);
        list.add(3);
    }
    
    /**
     * Bounded type parameter method
     */
    public static <T extends Comparable<T>> T findMax(T[] array) {
        if (array.length == 0) return null;
        
        T max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i].compareTo(max) > 0) {
                max = array[i];
            }
        }
        return max;
    }
    
    /**
     * Heap pollution demonstration
     */
    @SuppressWarnings({"unchecked", "varargs"})
    public static void demonstrateHeapPollution() {
        System.out.println("\nHEAP POLLUTION EXAMPLE:");
        
        // This method has heap pollution
        List<String>[] arrays = createArrays("Hello", "World");
        
        // This works fine
        String first = arrays[0].get(0);
        System.out.println("Retrieved safely: " + first);
        
        // But the underlying array can contain any type
        System.out.println("Heap pollution can occur with generic varargs");
    }
    
    @SafeVarargs
    public static <T> List<T>[] createArrays(T... elements) {
        @SuppressWarnings("unchecked")
        List<T>[] arrays = (List<T>[]) new List[elements.length];
        
        for (int i = 0; i < elements.length; i++) {
            arrays[i] = new ArrayList<>();
            arrays[i].add(elements[i]);
        }
        
        return arrays;
    }
}

// Supporting classes

/**
 * Basic generic class
 */
class GenericBox<T> {
    private T content;
    
    public GenericBox(T content) {
        this.content = content;
    }
    
    public T get() {
        return content;
    }
    
    public void set(T content) {
        this.content = content;
    }
}

/**
 * Generic class with multiple type parameters
 */
class GenericPair<T, U> {
    private T first;
    private U second;
    
    public GenericPair(T first, U second) {
        this.first = first;
        this.second = second;
    }
    
    public T getFirst() { return first; }
    public U getSecond() { return second; }
    
    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }
}

/**
 * Bounded generic class
 */
class NumberBox<T extends Number> {
    private T value;
    
    public NumberBox(T value) {
        this.value = value;
    }
    
    public T getValue() {
        return value;
    }
    
    public double getDoubleValue() {
        return value.doubleValue();
    }
}

/**
 * Multiple bounds example
 */
class ComparableBox<T extends Number & Comparable<T>> {
    private T value;
    
    public ComparableBox(T value) {
        this.value = value;
    }
    
    public T getValue() {
        return value;
    }
    
    public int compareTo(ComparableBox<T> other) {
        return this.value.compareTo(other.value);
    }
}

/**
 * Self-bounded generic
 */
abstract class Shape<T extends Shape<T>> implements Comparable<T> {
    public abstract double area();
}

class Circle extends Shape<Circle> {
    private double radius;
    
    public Circle(double radius) {
        this.radius = radius;
    }
    
    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
    
    @Override
    public int compareTo(Circle other) {
        return Double.compare(this.area(), other.area());
    }
}

/**
 * Generic functional interface
 */
@FunctionalInterface
interface Factory<T> {
    T create();
}

/**
 * Generic builder pattern
 */
class GenericPerson {
    private String name;
    private int age;
    private String email;
    
    GenericPerson(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
    }
    
    @Override
    public String toString() {
        return "Person{name='" + name + "', age=" + age + ", email='" + email + "'}";
    }
}

class GenericPersonBuilder {
    private String name;
    private int age;
    private String email;
    
    public GenericPersonBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    public GenericPersonBuilder age(int age) {
        this.age = age;
        return this;
    }
    
    public GenericPersonBuilder email(String email) {
        this.email = email;
        return this;
    }
    
    public GenericPerson build() {
        return new GenericPerson(name, age, email);
    }
}
