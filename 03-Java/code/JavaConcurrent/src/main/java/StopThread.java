import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class StopThread {

    public static void main(String[] args) {
        ExceptionStop exceptionStop = new ExceptionStop();
        SleepStop sleepStop = new SleepStop();
        exec(sleepStop);
    }

    private static void exec(Callable callable) {
        FutureTask<Integer> task = new FutureTask<>(callable);
        Thread thread = new Thread(task);
        thread.start();

        // 等待 num 变成 5
        while(SleepStop.num != 5){
            // 稍微让步或睡眠一下，防止主线程把单个 CPU 核心占满
            Thread.yield();
        }

        System.out.println("主线程发出打断信号...");
        thread.interrupt();
    }
}

class ExceptionStop implements Callable<Integer>{

    // 1. 加上 volatile 保证可见性
    public static volatile int num = 0;

    @Override
    public Integer call() throws Exception {
        // 2. 循环条件直接判断打断状态
        while (!Thread.currentThread().isInterrupted()) {
            try {
                System.out.println(num++);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // 3. sleep 期间收到打断信号会触发异常
                System.out.println("在 sleep 中被拦截，此时 num=" + num);
                // 重新设置打断标记（好习惯），或者直接 return/throw
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("子线程安全安全退出");
        return num;
    }
}

class SleepStop implements Callable<Integer>{

    public static volatile int num = 0;

    @Override
    public Integer call() throws Exception {
        while(!Thread.currentThread().isInterrupted()){
            System.out.println(num++);

            // stop
//            if(SleepStop.num == 5){
//                System.out.println("use stop");
//                Thread.currentThread().stop();
//            }

            // 正常return
            if(!Thread.currentThread().isInterrupted()){
                System.out.println("正常 return");
                return 0;
            }

            Thread.sleep(1000);

        }
        System.out.println("非sleep中断");
        return 0;
    }
}