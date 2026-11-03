package dsa.datastructures.stacksqueues.queue;
/**
 * Your MyStack object will be instantiated and called as such:
 * MyStack obj = new MyStack();
 * obj.push(x);
 * int param_2 = obj.pop();
 * int param_3 = obj.top();
 * boolean param_4 = obj.empty();
 */



import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   Created On: Friday,16.05.2025 07:51 pm
 */
public class Implement_Stack_using_Queues {
    public static void main(String[] args) {
        MyStack stack = new MyStack();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        System.out.println(stack.pop()); // 3
        System.out.println(stack.top()); // 2
        System.out.println(stack.empty()); // false
    }

    static class MyStack {

        private Queue<Integer> q;

        public MyStack() {
            q = new LinkedList<>();
        }

        public void push(int x) {
            q.add(x);

            for(int i = 1; i < q.size(); i++){
                q.add(q.remove());
            }
        }

        public int pop() {
            return q.remove();
        }

        public int top() {
            return q.peek();
        }

        public boolean empty() {
            return q.isEmpty();
        }
    }


}

