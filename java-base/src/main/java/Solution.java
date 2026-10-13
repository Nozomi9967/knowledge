import java.util.concurrent.atomic.AtomicReference;

class InsufficientBalanceException extends Throwable {
    void throwException() throws InsufficientBalanceException {
        throw new InsufficientBalanceException();
    }
}

class Solution {


    private AtomicReference<Double> balance = new AtomicReference<>(0.0);

    Solution(double initialBalance) {
        balance.set(initialBalance);
    }

    public void deposit(double amount) {
        balance.set(balance.get() + amount);
    }

    public void withdraw(double amount) throws InsufficientBalanceException {
        balance.set(balance.get() - amount);
    }

    public double getBalance() {
        return balance.get();
    }
}
