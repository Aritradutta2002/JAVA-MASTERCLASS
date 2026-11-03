package dsa.techniques.hashing;
import java.sql.SQLOutput;
import java.util.*;
import java.lang.*;

public class Anagram {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        String str1,str2;
        System.out.println("Enter the first string: ");
        str1 = sc.nextLine();
        System.out.println("Enter the second string: ");
        str2 = sc.nextLine();
        str1 = str1.toLowerCase();
        str2 = str2.toLowerCase();
        char[] arr1 = str1.toCharArray();
        char[] arr2 = str2.toCharArray();
        /*
        System.out.println(Arrays.toString(arr1));
        System.out.println(Arrays.toString(arr2));
         */
        int len1 = arr1.length;
        int len2 = arr2.length;

            Arrays.sort(arr1);
            Arrays.sort(arr2);

            boolean result = Arrays.equals(arr1,arr2);

            if (result)
                System.out.println("They are anagram");
             else
                System.out.println("Not Anagram");

    }
}

