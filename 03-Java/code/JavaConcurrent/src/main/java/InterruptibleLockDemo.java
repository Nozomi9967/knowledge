import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {

    private static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        // 持锁线程：先抢到锁，sleep 5 秒不释放
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[holder] 拿到锁，sleep 5s ...");
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                System.out.println("[holder] 被中断");
            } finally {
                lock.unlock();
                System.out.println("[holder] 释放锁");
            }
        }, "holder");
        holder.start();

        TimeUnit.MILLISECONDS.sleep(200); // 确保 holder 先抢到锁

        // 等待线程：用 lockInterruptibly() 抢锁 —— 等待过程可被中断
        Thread waiter = new Thread(() -> {
            try {
                System.out.println("[waiter] 调用 lockInterruptibly() 抢锁");
                lock.lockInterruptibly();   // ← 关键 API
                try {
                    System.out.println("[waiter] 拿到锁（不会执行到这里）");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("[waiter] 被中断，放弃抢锁，优雅退出");
            }
        }, "waiter");
        waiter.start();

        TimeUnit.SECONDS.sleep(1);
        System.out.println("[main]   中断 waiter 线程");
        waiter.interrupt();

        holder.join();
        waiter.join();
        System.out.println("[main]   结束");
    }
}