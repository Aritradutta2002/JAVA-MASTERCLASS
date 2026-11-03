package JAVA_DSA.MixedCodes;
import java.util.*;
import java.lang.*;
public class prob4{
    static boolean checkVowel(char ch){
        ch = Character.toUpperCase(ch);
        return(ch=='A'||ch=='E'||ch=='I'||ch=='O'||ch=='U');
    }
    static int countVowels(String str)
    {
        int count = 0;
        for (int i = 0; i < str.length(); i++)
            if (checkVowel(str.charAt(i))) // Check for vowel
                ++count;
        return count;
    }
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
       // char ch [] = new char[10];
        System.out.print("Enter the String: ");
        char[] ch = sc.next().toCharArray();
        System.out.print("String is: ");
        for(int i=0;i<ch.length;i++) {
            System.out.print(ch[i]);
        }

        String str =new String();
        String string = str.valueOf(ch);
        System.out.println("\nNum of Vowels are: "+countVowels(string));
        System.out.println("Reverse String is : ");
        for(int j=ch.length-1;j>=0;j--){
            System.out.print(ch[j]);
        }
    }
}
