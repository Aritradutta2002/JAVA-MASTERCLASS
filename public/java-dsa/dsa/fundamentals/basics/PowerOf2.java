package dsa.fundamentals.basics;
import java.util.Scanner;
import java.lang.*;
public class PowerOf2 {
    public static void main(String[] args) {
        System.out.println("Enter any integer: ");
        Scanner sc = new Scanner(System.in);
        int number = sc.nextInt();

       if((number > 0) && ((number & (number - 1)) == 0) == true){
           System.out.println(number + " is a power of 2");
       }
       else{
           System.out.println(number + " is not a power of 2");
       }
    }
}

