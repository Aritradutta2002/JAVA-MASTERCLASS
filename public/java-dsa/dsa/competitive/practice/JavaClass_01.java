package JAVA_DSA.MixedCodes;
import java.util.*;
public class JavaClass_01 {
    public static void main(String[] args) {
        int length,breadth,area;
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the length : ");
        length =sc.nextInt();
        System.out.println("Enter the breadth : ");
        breadth = sc.nextInt();
        System.out.println("The area is : "+IntroClass.area(length,breadth));
    }
    class IntroClass{
        static int area(int length,int breadth){
            int result = length * breadth ;
           return  result;
        }
    }
}
