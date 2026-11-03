package dsa.fundamentals.collections;

import java.util.*;

/**
 * Comparable vs Comparator - Interview Essential
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. Comparable interface and natural ordering
 * 2. Comparator interface and custom ordering
 * 3. When to use which approach
 * 4. Multiple sorting criteria
 * 5. Lambda expressions with Comparator (Java 8+)
 * 6. Method references and chaining
 */
public class ComparableComparator {
    
    public static void main(String[] args) {
        demonstrateComparable();
        demonstrateComparator();
        demonstrateLambdaComparators();
        demonstrateAdvancedSorting();
        showBestPractices();
    }
    
    /**
     * Comparable interface demonstration
     */
    public static void demonstrateComparable() {
        System.out.println("=== COMPARABLE INTERFACE ===\n");
        
        // Natural ordering for built-in types
        List<Integer> numbers = Arrays.asList(5, 2, 8, 1, 9);
        List<String> names = Arrays.asList("Charlie", "Alice", "Bob");
        
        Collections.sort(numbers);
        Collections.sort(names);
        
        System.out.println("Sorted numbers: " + numbers);
        System.out.println("Sorted names: " + names);
        
        // Custom class implementing Comparable
        List<Student> students = Arrays.asList(
            new Student("Alice", 85),
            new Student("Bob", 92),
            new Student("Charlie", 78)
        );
        
        Collections.sort(students); // Uses natural ordering (by grade)
        System.out.println("Students sorted by grade:");
        students.forEach(System.out::println);
        
        // TreeSet automatically uses Comparable
        Set<Student> studentSet = new TreeSet<>(students);
        System.out.println("TreeSet (automatically sorted): " + studentSet);
    }
    
    /**
     * Comparator interface demonstration
     */
    public static void demonstrateComparator() {
        System.out.println("\n=== COMPARATOR INTERFACE ===\n");
        
        List<Student> students = Arrays.asList(
            new Student("Alice", 85),
            new Student("Bob", 92),
            new Student("Charlie", 78),
            new Student("Alice", 90) // Same name, different grade
        );
        
        // Anonymous class comparator
        Comparator<Student> nameComparator = new Comparator<Student>() {
            @Override
            public int compare(Student s1, Student s2) {
                return s1.getName().compareTo(s2.getName());
            }
        };
        
        Collections.sort(students, nameComparator);
        System.out.println("Sorted by name (anonymous class):");
        students.forEach(System.out::println);
        
        // Reverse order
        Collections.sort(students, Collections.reverseOrder());
        System.out.println("\nSorted by grade (reverse order):");
        students.forEach(System.out::println);
        
        // Custom comparator with null handling
        List<String> namesWithNull = Arrays.asList("Alice", null, "Bob", "Charlie", null);
        Collections.sort(namesWithNull, Comparator.nullsFirst(String::compareTo));
        System.out.println("\nWith null handling: " + namesWithNull);
    }
    
    /**
     * Lambda expressions and method references with Comparator
     */
    public static void demonstrateLambdaComparators() {
        System.out.println("\n=== LAMBDA COMPARATORS (Java 8+) ===\n");
        
        List<Employee> employees = Arrays.asList(
            new Employee("John", "Engineering", 75000),
            new Employee("Alice", "Marketing", 65000),
            new Employee("Bob", "Engineering", 80000),
            new Employee("Carol", "HR", 60000)
        );
        
        // Lambda expression
        employees.sort((e1, e2) -> e1.getName().compareTo(e2.getName()));
        System.out.println("Sorted by name (lambda):");
        employees.forEach(System.out::println);
        
        // Method reference
        employees.sort(Comparator.comparing(Employee::getSalary));
        System.out.println("\nSorted by salary (method reference):");
        employees.forEach(System.out::println);
        
        // Reverse order with method reference
        employees.sort(Comparator.comparing(Employee::getSalary).reversed());
        System.out.println("\nSorted by salary (descending):");
        employees.forEach(System.out::println);
        
        // Multiple criteria with thenComparing
        employees.sort(Comparator.comparing(Employee::getDepartment)
                                .thenComparing(Employee::getSalary));
        System.out.println("\nSorted by department, then salary:");
        employees.forEach(System.out::println);
        
        // Complex chaining
        employees.sort(Comparator.comparing(Employee::getDepartment)
                                .thenComparing(Employee::getName)
                                .thenComparing(Employee::getSalary, Comparator.reverseOrder()));
        System.out.println("\nComplex sorting (dept, name, salary desc):");
        employees.forEach(System.out::println);
    }
    
    /**
     * Advanced sorting scenarios
     */
    public static void demonstrateAdvancedSorting() {
        System.out.println("\n=== ADVANCED SORTING SCENARIOS ===\n");
        
        List<Product> products = Arrays.asList(
            new Product("Laptop", 999.99, "Electronics"),
            new Product("Book", 29.99, "Education"),
            new Product("Phone", 799.99, "Electronics"),
            new Product("Desk", 299.99, "Furniture"),
            new Product("Chair", 199.99, "Furniture")
        );
        
        // 1. Custom comparator with multiple conditions
        Comparator<Product> categoryPriceComparator = (p1, p2) -> {
            int categoryCompare = p1.getCategory().compareTo(p2.getCategory());
            if (categoryCompare != 0) {
                return categoryCompare;
            }
            return Double.compare(p2.getPrice(), p1.getPrice()); // Descending price
        };
        
        products.sort(categoryPriceComparator);
        System.out.println("Sorted by category, then price (desc):");
        products.forEach(System.out::println);
        
        // 2. Sorting with null-safe comparators
        List<String> items = Arrays.asList("apple", null, "banana", "cherry", null);
        items.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("\nNull-safe sorting: " + items);
        
        // 3. Case-insensitive sorting
        List<String> words = Arrays.asList("Apple", "banana", "Cherry", "date");
        words.sort(String.CASE_INSENSITIVE_ORDER);
        System.out.println("Case-insensitive: " + words);
        
        // 4. Sorting by length, then alphabetically
        words.sort(Comparator.comparing(String::length)
                            .thenComparing(String.CASE_INSENSITIVE_ORDER));
        System.out.println("By length, then alphabetically: " + words);
        
        // 5. Priority Queue with custom comparator
        PriorityQueue<Task> taskQueue = new PriorityQueue<>(
            Comparator.comparing(Task::getPriority).reversed()
                     .thenComparing(Task::getDeadline)
        );
        
        taskQueue.addAll(Arrays.asList(
            new Task("Review code", 2, "2024-01-15"),
            new Task("Fix bug", 1, "2024-01-10"),
            new Task("Write tests", 2, "2024-01-12"),
            new Task("Deploy", 3, "2024-01-20")
        ));
        
        System.out.println("\nPriority queue (priority desc, deadline asc):");
        while (!taskQueue.isEmpty()) {
            System.out.println(taskQueue.poll());
        }
    }
    
    /**
     * Best practices and common patterns
     */
    public static void showBestPractices() {
        System.out.println("\n=== BEST PRACTICES ===\n");
        
        System.out.println("WHEN TO USE COMPARABLE:");
        System.out.println("✓ When there's a single, natural way to order objects");
        System.out.println("✓ When you control the class definition");
        System.out.println("✓ For fundamental ordering (e.g., Person by ID)");
        
        System.out.println("\nWHEN TO USE COMPARATOR:");
        System.out.println("✓ When you need multiple sorting strategies");
        System.out.println("✓ When you don't control the class");
        System.out.println("✓ For context-specific ordering");
        
        System.out.println("\nCOMPARATOR BEST PRACTICES:");
        System.out.println("✓ Use method references when possible");
        System.out.println("✓ Chain comparators with thenComparing()");
        System.out.println("✓ Handle nulls explicitly");
        System.out.println("✓ Use static factory methods");
        System.out.println("✗ Avoid complex logic in comparators");
        
        // Demonstrate static factory methods
        System.out.println("\nSTATIC FACTORY METHODS:");
        
        List<Integer> numbers = Arrays.asList(3, 1, 4, 1, 5, 9);
        
        // Natural order
        numbers.sort(Comparator.naturalOrder());
        System.out.println("Natural order: " + numbers);
        
        // Reverse order
        numbers.sort(Comparator.reverseOrder());
        System.out.println("Reverse order: " + numbers);
        
        // Comparing by key
        List<String> words = Arrays.asList("apple", "pie", "a", "cherry");
        words.sort(Comparator.comparing(String::length));
        System.out.println("By length: " + words);
        
        // Common pitfalls
        System.out.println("\nCOMMON PITFALLS:");
        System.out.println("✗ Inconsistent with equals()");
        System.out.println("✗ Not handling edge cases (nulls, equal values)");
        System.out.println("✗ Performance issues in complex comparisons");
        System.out.println("✗ Modifying objects during sorting");
        
        demonstrateCommonMistakes();
    }
    
    /**
     * Common mistakes demonstration
     */
    private static void demonstrateCommonMistakes() {
        System.out.println("\nCOMMON MISTAKES DEMO:");
        
        // Mistake 1: Inconsistent with equals
        class BadComparable implements Comparable<BadComparable> {
            private String name;
            
            public BadComparable(String name) { this.name = name; }
            
            @Override
            public int compareTo(BadComparable other) {
                return this.name.compareToIgnoreCase(other.name); // Case insensitive
            }
            
            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof BadComparable)) return false;
                return this.name.equals(((BadComparable) obj).name); // Case sensitive!
            }
            
            @Override
            public String toString() { return name; }
        }
        
        BadComparable b1 = new BadComparable("Apple");
        BadComparable b2 = new BadComparable("apple");
        
        System.out.println("compareTo result: " + b1.compareTo(b2)); // 0 (equal)
        System.out.println("equals result: " + b1.equals(b2));       // false (not equal)
        System.out.println("This violates the contract!");
        
        // Mistake 2: Not handling nulls
        try {
            List<String> listWithNulls = Arrays.asList("a", null, "b");
            listWithNulls.sort(String::compareTo); // Will throw NPE
        } catch (NullPointerException e) {
            System.out.println("NPE when sorting with nulls without proper handling");
        }
        
        // Correct way
        List<String> listWithNulls = Arrays.asList("a", null, "b");
        listWithNulls.sort(Comparator.nullsFirst(String::compareTo));
        System.out.println("Properly handled nulls: " + listWithNulls);
    }
}

// Supporting classes

/**
 * Student class implementing Comparable
 */
class Student implements Comparable<Student> {
    private String name;
    private int grade;
    
    public Student(String name, int grade) {
        this.name = name;
        this.grade = grade;
    }
    
    public String getName() { return name; }
    public int getGrade() { return grade; }
    
    @Override
    public int compareTo(Student other) {
        return Integer.compare(this.grade, other.grade); // Natural ordering by grade
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Student)) return false;
        Student student = (Student) obj;
        return grade == student.grade && Objects.equals(name, student.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, grade);
    }
    
    @Override
    public String toString() {
        return name + "(" + grade + ")";
    }
}

/**
 * Employee class for demonstrating multiple sorting criteria
 */
class Employee {
    private String name;
    private String department;
    private double salary;
    
    public Employee(String name, String department, double salary) {
        this.name = name;
        this.department = department;
        this.salary = salary;
    }
    
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public double getSalary() { return salary; }
    
    @Override
    public String toString() {
        return String.format("%s (%s, $%.0f)", name, department, salary);
    }
}

/**
 * Product class for advanced sorting examples
 */
class Product {
    private String name;
    private double price;
    private String category;
    
    public Product(String name, double price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }
    
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }
    
    @Override
    public String toString() {
        return String.format("%s - $%.2f (%s)", name, price, category);
    }
}

/**
 * Task class for priority queue example
 */
class Task {
    private String description;
    private int priority; // 1 = high, 2 = medium, 3 = low
    private String deadline;
    
    public Task(String description, int priority, String deadline) {
        this.description = description;
        this.priority = priority;
        this.deadline = deadline;
    }
    
    public String getDescription() { return description; }
    public int getPriority() { return priority; }
    public String getDeadline() { return deadline; }
    
    @Override
    public String toString() {
        return String.format("%s (Priority: %d, Deadline: %s)", 
                           description, priority, deadline);
    }
}
