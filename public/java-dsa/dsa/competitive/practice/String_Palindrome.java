package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;

public class String_Palindrome {
    public static void main(String[] args) {
        int i;
        String str,str2;
        String rev = "";
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter a String : ");
         str = sc.nextLine();

        int len = str.length();
        char arr[] = str.toCharArray();
        for(i=len-1;i>=0;i--){
            rev = rev + arr[i];
        }
        rev = rev.toLowerCase();
        str = str.toLowerCase();

        if(str.equals(rev))
            System.out.println("Yes");
        else
            System.out.println("No");
    }
}

