package JAVA_DSA.MixedCodes;
class Greek{
    int roll;
    String name ;
    float marks;

    Greek(int roll,String name ,float marks){
        System.out.println("Constructor call!!");
        this.roll=roll;
        this.name=name;
        this.marks=marks;
    }
}
public class JavaConstructors_01 {
    JavaConstructors_01(){
        System.out.println("This is a Default Constructor!!");
    }
    public static void main(String[] args) {

        Greek geek = new Greek(57,"Aritra ",89.63f);
        System.out.println(geek.name);
        System.out.println(geek.roll);
        System.out.println(geek.marks);

        JavaConstructors_01 hello = new JavaConstructors_01();
    }
}
