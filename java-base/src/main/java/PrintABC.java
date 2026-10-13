//public class PrintABC {
//
//    private static int state=0;
//    private static final Object lock=new Object();
//
//    private static void run(int target, String c){
//        while(state/3<10){
//            synchronized (lock){
//                while(state%3!=target){
//                    try {
//                        lock.wait();
//                    } catch (InterruptedException e) {
//                        return;
//                    }
//                }
//                if(target==0) System.out.print(state/3+":");
//                System.out.print(c);
//                if(target==2) System.out.println();
//                state++;
//
//                lock.notifyAll();
//            }
//        }
//    }
//
//    public static void main(String[] args) {
//        new Thread(()->run(0,"A"),"A").start();
//        new Thread(()->run(1,"B"),"B").start();
//        new Thread(()->run(2,"C"),"C").start();
//    }
//}


public class PrintABC {
    private static final Object lock = new Object();
    private static int state = 0;              // 0打印A 1打印B 2打印C

    public static void main(String[] args) {
        new Thread(() -> run("A", 0), "Thread-A").start();
        new Thread(() -> run("B", 1), "Thread-B").start();
        new Thread(() -> run("C", 2), "Thread-C").start();
    }

    private static void run(String content, int target) {
        for (int i = 0; i < 10; i++) {         // 循环 10 轮
            synchronized (lock) {
                while (state % 3 != target) {  // 没轮到自己就等
                    try { lock.wait(); }
                    catch (InterruptedException e) { return; }
                }
                System.out.print(content + " ");
                state++;
                lock.notifyAll();
            }
        }
    }
}
