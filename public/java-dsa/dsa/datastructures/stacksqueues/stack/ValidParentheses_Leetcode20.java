package dsa.datastructures.stacksqueues.stack;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Monday, 03.03.2025  11:17 pm
*/
public class ValidParentheses_Leetcode20 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        String s = sc.nextLine();
        System.out.println(isValid(s));
    }
    public static boolean isValid(String s){
        Stack <Character> st = new Stack<>();
        for(var ch : s.toCharArray()){
            if(ch == '(' || ch == '{' || ch == '['){
                st.push(ch);
            }
            else{
                if(st.isEmpty() || !isMatch(st.pop(), ch)) {
                return false;}
            }
        }
        return st.isEmpty();
    }

    private static boolean isMatch(char open, char close ){
        return (open == '(' && close == ')') ||
                (open == '{' && close == '}') ||
                (open == '[' && close == ']');
    }
}

