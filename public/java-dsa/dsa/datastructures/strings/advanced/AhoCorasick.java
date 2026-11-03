package dsa.datastructures.strings.advanced;
/**
 * AHO-CORASICK AUTOMATON
 * Multiple pattern matching in O(text + matches + alphabet)
 * 
 * Build trie + failure links (like KMP over trie). Classic CP algorithm.
 */

import java.util.*;

public class AhoCorasick {
    static class Node {
        Map<Character, Node> next = new HashMap<>();
        Node fail;
        List<Integer> out = new ArrayList<>(); // indices of patterns ending here
    }

    private final Node root = new Node();

    // Build trie of patterns
    public void addPattern(String p, int idx) {
        Node cur = root;
        for (char c : p.toCharArray()) {
            cur = cur.next.computeIfAbsent(c, k -> new Node());
        }
        cur.out.add(idx);
    }

    // Build failure links (BFS)
    public void build() {
        Queue<Node> q = new ArrayDeque<>();
        // Root's immediate children fail to root
        for (Map.Entry<Character, Node> e : root.next.entrySet()) {
            e.getValue().fail = root;
            q.offer(e.getValue());
        }
        // Missing edges from root lead to root
        while (!q.isEmpty()) {
            Node u = q.poll();
            for (Map.Entry<Character, Node> e : u.next.entrySet()) {
                char c = e.getKey();
                Node v = e.getValue();
                Node f = u.fail;
                while (f != null && !f.next.containsKey(c)) f = f.fail;
                v.fail = (f == null) ? root : f.next.get(c);
                v.out.addAll(v.fail.out);
                q.offer(v);
            }
        }
    }

    // Search text, return mapping: patternIdx -> list of end positions
    public Map<Integer, List<Integer>> search(String text) {
        Map<Integer, List<Integer>> res = new HashMap<>();
        for (int i = 0; i < text.length(); i++) {
            // Ensure map lists exist lazily
        }
        Node cur = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (cur != root && !cur.next.containsKey(c)) cur = cur.fail;
            cur = cur.next.getOrDefault(c, root);
            for (int idx : cur.out) {
                res.computeIfAbsent(idx, k -> new ArrayList<>()).add(i);
            }
        }
        return res;
    }

    public static void main(String[] args) {
        AhoCorasick ac = new AhoCorasick();
        String[] patterns = {"he", "she", "his", "hers"};
        for (int i = 0; i < patterns.length; i++) ac.addPattern(patterns[i], i);
        ac.build();
        String text = "ahishers";
        Map<Integer, List<Integer>> res = ac.search(text);
        System.out.println("=== Aho-Corasick ===");
        for (int i = 0; i < patterns.length; i++) {
            System.out.println(patterns[i] + " -> end positions: " + res.getOrDefault(i, List.of()));
        }
    }
}

