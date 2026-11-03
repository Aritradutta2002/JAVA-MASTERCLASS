package dsa.fundamentals.mathematics;
import java.util.*;

public class GCD_Itrative {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int num1 = sc.nextInt();
        int num2 = sc.nextInt();
        int remainder = 1;

        while(num1 % num2 != 0){
            remainder = num1 % num2;
            num1 = num2;
            num2 = remainder;
        }
        System.out.println(remainder);
    }
}

