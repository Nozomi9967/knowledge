import java.util.concurrent.locks.LockSupport;

public class AlternatePrintLP {
    static Thread t1, t2;
    static int count = 1;

    public static void main(String[] args) {
        t1 = new Thread(() -> {
            while (count <= 100) {
                System.out.println("A: " + count++);
                LockSupport.unpark(t2);            // 给对方发许可证
                if (count <= 100) LockSupport.park(); // 自己停车
            }
        });
        t2 = new Thread(() -> {
            while (count <= 100) {
                LockSupport.park();
                if (count > 100) return;           // 防止结束后多打一个
                System.out.println("B: " + count++);
                LockSupport.unpark(t1);
            }
        });
        t1.start();
        t2.start();
    }
}
