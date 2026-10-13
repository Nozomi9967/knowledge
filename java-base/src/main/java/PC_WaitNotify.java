import java.util.LinkedList;
import java.util.Queue;

public class PC_WaitNotify {

    private static final Queue<Integer> queue=new LinkedList<>();
    private static final int CAPACITY=10;
    private static final Object lock=new Object();

    public static void main(String[] args) throws InterruptedException {
        Thread producer = new Thread(()->{
            for (int i = 1; i <= 20; i++) {
                synchronized (lock){
                    while(queue.size()==CAPACITY){
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

                    queue.offer(i);
                    System.out.println("生产:"+i);
                    lock.notifyAll();
                }
            }
        });

        Thread consumer = new Thread(()->{
            for(int i=1;i<=20;i++){
                synchronized (lock){
                    while(queue.isEmpty()){
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

                    System.out.println("消费:"+queue.poll());
                    lock.notifyAll();
                }
            }
        });


        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println("end");
    }

}
