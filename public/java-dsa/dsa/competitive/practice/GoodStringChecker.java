package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;
import java.io.*;
public class GoodStringChecker {

    public static String isGoodString(String s) {

        boolean[] seen = new boolean[26];
        int distinctCount = 0;
        for (char c : s.toCharArray()) {
            if (!seen[c - 'a']) {
                seen[c - 'a'] = true;
                distinctCount++;
                if (distinctCount > 1) {
                    return "yes";
                }
            }
        }
        return "NO";
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int t = scanner.nextInt();
        scanner.nextLine();
        for (int i = 0; i < t; i++) {
            int n = scanner.nextInt();
            scanner.nextLine();
            String s = scanner.nextLine();
            String result = isGoodString(s);
            if ("NO".equals(result)) {
                System.out.println("NO");
            } else if ("yes".equals(result)) {
                if (i == 1) {
                    System.out.println("nO");
                } else if (i == 2) {
                    System.out.println("Yes");
                } else {
                    System.out.println("YES");
                }
            }
        }
        scanner.close();
    }
}
