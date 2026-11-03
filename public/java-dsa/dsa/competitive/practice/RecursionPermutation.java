package JAVA_DSA.MixedCodes;
public class RecursionPermutation {

    static String deleteChar(String s, char c){
        if (s.isEmpty()) {
            return "";
        }

        char firstChar = s.charAt(0);
        String restOfString = s.substring(1);

        String result = deleteChar(restOfString, c);

        if (firstChar != c) {
            return firstChar + result;
        } else {
            return result;
        }
    }
    public static void main(String[] args) {
        System.out.println(deleteChar("cadviasora", 'a'));
    }
}
