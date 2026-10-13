public class AlternatePrint100 {

    private static final Object lock=new Object();
    private static  int count=1;

//    private static void run(boolean isOdd){
//        while(count<=100){
//            synchronized (lock){
//                while (!(isOdd==(count%2==1))){
//                    try {
//                        lock.wait();
//                    } catch (InterruptedException e) {
//                        return;
//                    }
//                }
//
//                if(count<=100) System.out.println(Thread.currentThread().getName()+":"+count++);
//
//                lock.notifyAll();
//            }
//        }
//    }

    private static void run(boolean isOdd) {
        while (true) {
            synchronized (lock) {
                while (count <= 100 && ((count % 2 == 1) != isOdd)) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (count > 100) {
                    lock.notifyAll(); // 唤醒可能仍在等待的另一个线程
                    return;
                }

                System.out.println(Thread.currentThread().getName() + ": " + count++);
                lock.notifyAll();
            }
        }
    }



    public static void main(String[] args) {
        new Thread(()->run(true),"奇数线程").start();
        new Thread(()->run(false),"偶数线程").start();
    }
}
