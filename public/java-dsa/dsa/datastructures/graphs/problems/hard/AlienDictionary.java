package dsa.datastructures.graphs.problems.hard;
import java.util.*;

public class AlienDictionary {
    public static String alienOrder(String[] words) {
        Map<Character, Set<Character>> adj = new HashMap<>();
        int[] indeg = new int[26];
        boolean[] seen = new boolean[26];
        Arrays.fill(indeg, -1);
        for (String w : words)
            for (char c : w.toCharArray())
                if (indeg[c - 'a'] == -1) {
                    indeg[c - 'a'] = 0;
                    seen[c - 'a'] = true;
                }
        for (int i = 0; i + 1 < words.length; i++) {
            String a = words[i], b = words[i + 1];
            int min = Math.min(a.length(), b.length());
            int j = 0;
            while (j < min && a.charAt(j) == b.charAt(j)) j++;
            if (j == min && a.length() > b.length()) return "";
            if (j < min) {
                char u = a.charAt(j), v = b.charAt(j);
                adj.computeIfAbsent(u, k -> new HashSet<>());
                if (adj.get(u).add(v)) indeg[v - 'a']++;
            }
        }
        Queue<Character> q = new ArrayDeque<>();
        for (int i = 0; i < 26; i++) if (indeg[i] == 0) q.add((char) ('a' + i));
        StringBuilder sb = new StringBuilder();
        while (!q.isEmpty()) {
            char u = q.poll();
            sb.append(u);
            if (adj.containsKey(u)) for (char v : adj.get(u)) if (--indeg[v - 'a'] == 0) q.add(v);
        }
        for (int i = 0; i < 26; i++) if (seen[i] && indeg[i] > 0) return "";
        return sb.toString();
    }
}

