package JAVA_DSA.MixedCodes;

public class Wrapper_class{
    public static void main(String[] args){
        int num =7;
        Integer num1 = Integer.valueOf(8);
        Integer num2 = 10;
        System.out.println(num2);
        System.out.println(num1);
        Demo D1 = new Demo();
        /*

        D1.name= "Aritra";
        D1.roll = 57;
        D1.marks = 9.75;
        */
        System.out.println(D1.name="Aritra");
        System.out.println(D1.roll=57);
        System.out.println(D1.marks=9.84);
        D1.Tester("AAA");
        D1.basic(7.95);

    }
}
    class Demo{
    String name;
    int roll;
    double marks;
            static void Tester(String name){
                System.out.println("This is inside Tester method: "+name);
            }
            static void basic(double marks){
                System.out.println("Khub kharap obostha kaka pass korbo na marks: "+marks);
            }
            void noob(int kaka){
                System.out.println("Valo to kaka !!");
            }
    }

