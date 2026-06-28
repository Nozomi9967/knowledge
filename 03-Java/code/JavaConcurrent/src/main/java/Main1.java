public class Main1 {


    public static void main(String[] args) {
        TicketTask task = new TicketTask();      // 同一个 Runnable 对象
        new Thread(task, "窗口A").start();       // 三个线程共享它
        new Thread(task, "窗口B").start();
        new Thread(task, "窗口C").start();
        // 输出（交替执行）：
        // 窗口A 卖出票 5
        // 窗口C 卖出票 4
        // 窗口B 卖出票 3
        // 窗口A 卖出票 2
        // 窗口C 卖出票 1
    }
}

class TicketTask implements Runnable {
    private int tickets = 5; // 共享资源

    @Override
    public void run() {
            while (true) {

                synchronized (this) {
                    if(tickets > 0){
                        System.out.println(Thread.currentThread().getName()
                                + " 卖出票 " + tickets--);
                    }
                }
//                Thread.yield();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

    }
}
