package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 690: Employee Importance
 * 
 * Problem: You have a data structure of employee information, including the employee's unique ID, 
 * importance value, and direct subordinates' IDs.
 * Given an integer id that represents an employee's ID, return the total importance value of this 
 * employee and all their direct and indirect subordinates.
 * 
 * Time Complexity: O(n) where n is number of employees
 * Space Complexity: O(n)
 */

// Employee class definition (given in problem)
class Employee {
    public int id;
    public int importance;
    public List<Integer> subordinates;
    
    public Employee(int id, int importance, List<Integer> subordinates) {
        this.id = id;
        this.importance = importance;
        this.subordinates = subordinates;
    }
}

public class EmployeeImportance {
    
    /**
     * DFS solution using HashMap for O(1) lookup
     */
    public int getImportance(List<Employee> employees, int id) {
        // Create map for O(1) employee lookup
        Map<Integer, Employee> map = new HashMap<>();
        for (Employee emp : employees) {
            map.put(emp.id, emp);
        }
        
        return dfs(map, id);
    }
    
    private int dfs(Map<Integer, Employee> map, int id) {
        Employee emp = map.get(id);
        int totalImportance = emp.importance;
        
        // Add importance of all subordinates recursively
        for (int subId : emp.subordinates) {
            totalImportance += dfs(map, subId);
        }
        
        return totalImportance;
    }
    
    /**
     * BFS solution
     */
    public int getImportanceBFS(List<Employee> employees, int id) {
        Map<Integer, Employee> map = new HashMap<>();
        for (Employee emp : employees) {
            map.put(emp.id, emp);
        }
        
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(id);
        int totalImportance = 0;
        
        while (!queue.isEmpty()) {
            int currentId = queue.poll();
            Employee emp = map.get(currentId);
            totalImportance += emp.importance;
            
            // Add all subordinates to queue
            for (int subId : emp.subordinates) {
                queue.offer(subId);
            }
        }
        
        return totalImportance;
    }
    
    // Test method
    public static void main(String[] args) {
        EmployeeImportance solution = new EmployeeImportance();
        
        // Test case: employees = [[1,5,[2,3]],[2,3,[]],[3,3,[]]], id = 1
        List<Employee> employees = Arrays.asList(
            new Employee(1, 5, Arrays.asList(2, 3)),
            new Employee(2, 3, Arrays.asList()),
            new Employee(3, 3, Arrays.asList())
        );
        
        System.out.println("Total importance: " + solution.getImportance(employees, 1)); // Output: 11
    }
}

