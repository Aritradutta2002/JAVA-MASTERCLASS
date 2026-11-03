package JAVA_DSA.MixedCodes;

import java.util.*;
import java.lang.*;

public class weirdAlgorithm {
    static void printSeq(int num){
        int i = 0;
        int j = 0;
        while(num != 1){
            if(num % 2 == 0){
                System.out.print(num + " ");
                num = num/2;
            }
            else if (num % 2 != 0){
                System.out.print(num + " ");
                num = num*3 + 1;
            }
        }
        System.out.println("1");
    }
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int num =  sc.nextInt();
        printSeq(num);
    }
}
