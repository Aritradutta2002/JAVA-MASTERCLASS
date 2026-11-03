package dsa.techniques.monotonicstructures;

import java.util.*;

/**
 * Online Stock Span (LeetCode 901) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Bloomberg)
 * 
 * Problem: Calculate stock span for each day (consecutive days with price <= current)
 * Time: O(1) amortized per call, Space: O(n)
 */
public class StockSpanProblem {
    
    static class StockSpanner {
        Stack<int[]> stack; // [price, span]
        
        public StockSpanner() {
            stack = new Stack<>();
        }
        
        public int next(int price) {
            int span = 1;
            
            // Pop all smaller or equal prices and accumulate their spans
            while (!stack.isEmpty() && stack.peek()[0] <= price) {
                span += stack.pop()[1];
            }
            
            stack.push(new int[]{price, span});
            return span;
        }
    }
    
    // Alternative: Using indices
    static class StockSpannerWithIndex {
        Stack<Integer> stack; // Stores indices
        List<Integer> prices;
        
        public StockSpannerWithIndex() {
            stack = new Stack<>();
            prices = new ArrayList<>();
        }
        
        public int next(int price) {
            prices.add(price);
            int currentIndex = prices.size() - 1;
            
            // Find previous greater element
            while (!stack.isEmpty() && prices.get(stack.peek()) <= price) {
                stack.pop();
            }
            
            int span = stack.isEmpty() ? currentIndex + 1 : currentIndex - stack.peek();
            stack.push(currentIndex);
            
            return span;
        }
    }
    
    public static void main(String[] args) {
        StockSpanner stockSpanner = new StockSpanner();
        
        System.out.println("Stock Span Examples:");
        System.out.println(stockSpanner.next(100)); // 1
        System.out.println(stockSpanner.next(80));  // 1
        System.out.println(stockSpanner.next(60));  // 1
        System.out.println(stockSpanner.next(70));  // 2
        System.out.println(stockSpanner.next(60));  // 1
        System.out.println(stockSpanner.next(75));  // 4
        System.out.println(stockSpanner.next(85));  // 6
    }
}

