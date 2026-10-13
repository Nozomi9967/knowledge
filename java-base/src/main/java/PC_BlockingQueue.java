import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class PC_BlockingQueue {

    private static final BlockingQueue<Integer> queue=new ArrayBlockingQueue<>(20);

    /*
    * 调用 Thread.sleep() 或 BlockingQueue.take()/put() 时，如果线程被中断，
    * 会抛出 InterruptedException，并且中断标志会被清除。
    * */
    public static void main(String[] args) throws InterruptedException {

        Thread producer=new Thread(()->{
           try {
               for(int i=1;i<=20;i++){
                   queue.put(i);
                   System.out.println("生产:"+i);
               }
           }catch (InterruptedException e){
               Thread.currentThread().interrupt();
           }
        });

        Thread consumer=new Thread(()->{
            try {
                for(int i=1;i<=20;i++){
                        Thread.sleep(100);
                        System.out.println("消费:"+queue.take());
                        Thread.currentThread().interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        // 让主线程等待对应线程结束后再继续执行
        producer.join();
        consumer.join();

        System.out.println("end");

    }
}
