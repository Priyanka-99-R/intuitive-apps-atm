package com.intuitiveapps.atm.web;

import com.intuitiveapps.atm.domain.Bank;
import com.intuitiveapps.atm.domain.CustomerName;
import com.intuitiveapps.atm.domain.CustomerSnapshot;
import com.intuitiveapps.atm.domain.Money;
import com.intuitiveapps.atm.domain.TransactionResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serialises access to the {@link Bank}.
 *
 * <p><strong>Why this class exists.</strong> {@code Bank} is not thread safe, and a Spring MVC
 * controller is called on a different request thread for every request. Two concurrent transfers
 * touching the same accounts would interleave a read of a balance with somebody else's write and
 * quietly lose money. A Spring bean is a singleton shared by every request thread, so this had to
 * be handled somewhere.
 *
 * <p><strong>Why the lock is here rather than inside {@code Bank}.</strong> The CLI is a single
 * conversation at a single terminal and has no contention at all; making the domain synchronise
 * would charge it for a problem it does not have, and would bury a deployment concern inside the
 * business rules. The adapter that introduces concurrency is the adapter that pays for it.
 *
 * <p><strong>Why one coarse lock rather than a lock per account.</strong> Every operation here is
 * a handful of map lookups - microseconds - so contention is not the bottleneck, and correctness
 * is worth more than throughput at this scale. Per-account locking would mean acquiring two locks
 * for a transfer, which is precisely the lock-ordering deadlock that every ATM example is famous
 * for. If throughput ever mattered, the answer would be a database with row-level locking, not a
 * cleverer arrangement of monitors.
 */
@Service
public class BankService {

    private final Bank bank;
    private final ReentrantLock lock = new ReentrantLock();

    public BankService(Bank bank) {
        this.bank = bank;
    }

    public CustomerSnapshot login(String name) {
        return withLock(() -> bank.login(CustomerName.of(name)));
    }

    public CustomerSnapshot snapshot(String name) {
        return withLock(() -> bank.snapshotOf(CustomerName.of(name)));
    }

    public List<CustomerSnapshot> allCustomers() {
        return withLock(bank::allCustomers);
    }

    public TransactionResult deposit(String name, String amount) {
        return withLock(() -> bank.deposit(CustomerName.of(name), Money.parse(amount)));
    }

    public TransactionResult withdraw(String name, String amount) {
        return withLock(() -> bank.withdraw(CustomerName.of(name), Money.parse(amount)));
    }

    public TransactionResult transfer(String from, String to, String amount) {
        return withLock(() ->
                bank.transfer(CustomerName.of(from), CustomerName.of(to), Money.parse(amount)));
    }

    /**
     * Runs {@code action} holding the lock, releasing it in a {@code finally} so that a rejected
     * operation - which throws - cannot leave the bank locked for every subsequent request.
     */
    private <T> T withLock(java.util.function.Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
