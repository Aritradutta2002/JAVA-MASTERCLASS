package dsa.techniques.hashing;

import java.util.*;

public class Group_Anagram {
    public static List<List<String>> groupAnagrams(String[] strs) {
        // Using a HashMap to map sorted strings to lists of anagrams
        HashMap<String, List<String>> map = new HashMap<>();

        for (String str : strs) {
            // Get the sorted version of the current string
            String sortedStr = Sort(str);

            // If the sorted string is not already a key in the map, add it with a new list
            if (!map.containsKey(sortedStr)) {
                map.put(sortedStr, new ArrayList<>());
            }

            // Add the original string to the list of its anagrams
            map.get(sortedStr).add(str);
        }

        // Return a list of all the anagram groups
        return new ArrayList<>(map.values());
    }

    public static String Sort (String str){
        char [] chars = str.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    public static void main(String[] args) {
        String [] strs = {"eat","tea","tan","ate","nat","bat"};
        System.out.println(groupAnagrams(strs));
    }
}

