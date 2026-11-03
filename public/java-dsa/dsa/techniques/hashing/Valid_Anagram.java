package dsa.techniques.hashing;
/*
It is a Leetcode Problem named "Valid Anagram"
*/

import java.lang.*;
import java.util.*;

public class Valid_Anagram {

    public static void main(String[] args)
    {
        Scanner sc = new Scanner(System.in);
        String str1,str2;
        System.out.println("Enter the First String: ");
        str1 = sc.next();
        System.out.println("Enter the Second String");
        str2 = sc.next();
        if(checkAnagram(str1,str2))
            System.out.println("They are Anagram");
        else
            System.out.println("Not Anagram");
    }

    static boolean checkAnagram(String str1, String str2)
    {
        str1 = str1.toLowerCase();
        str2 = str2.toLowerCase();

        char[] arr1 = str1.toCharArray();
        char[] arr2 = str2.toCharArray();

        int len1 = arr1.length;
        int len2 = arr2.length;

        if(len1 != len2)
            return false;
        else {
            Arrays.sort(arr1);
            Arrays.sort(arr2);
            return Arrays.equals(arr1, arr2);
        }
    }
}

