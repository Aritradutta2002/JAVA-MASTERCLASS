package dsa.systemdesign.datastructures;

import java.util.*;

/**
 * Peeking Iterator (LeetCode 284) - MEDIUM
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Problem: Iterator with peek() method
 */
public class PeekingIterator implements Iterator<Integer> {
    
    private Iterator<Integer> iterator;
    private Integer peeked;
    
    public PeekingIterator(Iterator<Integer> iterator) {
        this.iterator = iterator;
        this.peeked = null;
    }
    
    public Integer peek() {
        if (peeked == null) {
            peeked = iterator.next();
        }
        return peeked;
    }
    
    @Override
    public Integer next() {
        if (peeked != null) {
            Integer result = peeked;
            peeked = null;
            return result;
        }
        return iterator.next();
    }
    
    @Override
    public boolean hasNext() {
        return peeked != null || iterator.hasNext();
    }
    
    public static void main(String[] args) {
        List<Integer> list = Arrays.asList(1, 2, 3);
        PeekingIterator it = new PeekingIterator(list.iterator());
        
        System.out.println(it.next()); // 1
        System.out.println(it.peek()); // 2
        System.out.println(it.next()); // 2
        System.out.println(it.next()); // 3
        System.out.println(it.hasNext()); // false
    }
}

