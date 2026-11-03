package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;

public class String_reversal {
    public static void main(String[] args) {
        String str;
        // String rev = "";
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter a string : ");
        str = sc.nextLine();
        int length_str = str.length();
        System.out.println(length_str);

        char arr[] = str.toCharArray();
        for(int i=(length_str-1);i>=0;i--) {
            System.out.print(arr[i]);

        }
    }

}
