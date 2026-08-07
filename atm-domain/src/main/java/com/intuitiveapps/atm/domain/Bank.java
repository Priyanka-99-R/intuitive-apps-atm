package com.intuitiveapps.atm.domain;

import com.intuitiveapps.atm.domain.exception.CustomerNotFoundException;
import com.intuitiveapps.atm.domain.exception.InsufficientFundsException;
import com.intuitiveapps.atm.domain.exception.InvalidAmountException;
import com.intuitiveapps.atm.domain.exception.SelfTransferException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bank: every account, every obligation, and the rules that connect them.
 *
 * <p>This is the aggregate root. It is the only type that can move money, and it is the only
 * type an adapter needs to know about. It holds no reference to a terminal, a socket or an HTTP
 * request, which is why the same instance backs both the CLI and the REST API.
 *
 * <h2>The two rules that are not obvious from the specification</h2>
 *
 * <ol>
 *   <li><strong>A transfer nets off before it moves cash.</strong> Transferring to somebody who
 *       already owes you reduces their debt instead of moving money. See {@link ObligationLedger}.
 *   <li><strong>Any credit settles debts immediately.</strong> The moment a customer's balance
 *       increases - by deposit or by an incoming transfer - it is applied to whatever they owe,
 *       before it is theirs to keep. The specification shows this for {@code deposit}; applying
 *       it to incoming transfers too is the consistent reading, and it yields a useful invariant:
 *       <em>a customer with a positive balance owes nobody</em>. That in turn is why
 *       {@code withdraw} needs no special handling for debt - there is never cash sitting in an
 *       account that has already been promised away.
 * </ol>
 *
 * <h2>Concurrency</h2>
 * <p>Not thread safe, and deliberately so - a single terminal is a single conversation. The web
 * adapter serialises access rather than making this class defend itself, so that the locking
 * policy lives with the deployment that needs it instead of being paid for by everybody. See
 * the README.
 */
public final class Bank {

    private final Map<CustomerName, Account> accounts = new LinkedHashMap<>();
    private final ObligationLedger ledger = new ObligationLedger();

    /**
     * Returns the customer's position, opening an account first if this is a new name.
     *
     * <p>Account creation lives here rather than in the adapters because "logging in creates the
     * customer if they do not exist" is a rule of this bank, not a convenience of one interface.
     */
    public CustomerSnapshot login(CustomerName name) {
        accounts.computeIfAbsent(name, Account::new);
        return snapshotOf(name);
    }

    public boolean hasCustomer(CustomerName name) {
        return accounts.containsKey(name);
    }

    public TransactionResult deposit(CustomerName name, Money amount) {
        requirePositive(amount);
        Account account = require(name);
        account.credit(amount);
        return new TransactionResult(settleDebtsOf(name), snapshotOf(name));
    }

    /**
     * Withdraws cash.
     *
     * <p>Unlike {@code transfer}, this refuses to overdraw. A transfer has a counterparty who can
     * be owed the shortfall; a withdrawal hands notes to a customer and there is nobody to owe.
     */
    public TransactionResult withdraw(CustomerName name, Money amount) {
        requirePositive(amount);
        Account account = require(name);
        if (amount.isGreaterThan(account.balance())) {
            throw new InsufficientFundsException(amount, account.balance());
        }
        account.debit(amount);
        return TransactionResult.noCashMovement(snapshotOf(name));
    }

    /**
     * Transfers to another customer, netting off any existing debt between the two and covering
     * whatever remains from the sender's balance. Any shortfall becomes an obligation rather
     * than an error.
     *
     * @throws CustomerNotFoundException if either party has no account. In particular the target
     *                                   must have logged in at least once; see the README for why
     *                                   this refuses rather than opening an account on their behalf.
     */
    public TransactionResult transfer(CustomerName from, CustomerName to, Money amount) {
        requirePositive(amount);
        if (from.equals(to)) {
            throw new SelfTransferException(from);
        }
        require(from);
        require(to);

        ledger.add(from, to, amount);
        return new TransactionResult(settleDebtsOf(from), snapshotOf(from));
    }

    public CustomerSnapshot snapshotOf(CustomerName name) {
        Account account = require(name);
        return new CustomerSnapshot(name, account.balance(), ledger.obligationsOf(name));
    }

    /** Every customer, in the order they first logged in. Used by the web UI and by tests. */
    public List<CustomerSnapshot> allCustomers() {
        return accounts.keySet().stream().map(this::snapshotOf).toList();
    }

    /**
     * Pays down debts using available cash, starting with {@code origin} and cascading.
     *
     * <p>The cascade matters: paying a creditor increases <em>their</em> balance, which by the
     * "any credit settles debts immediately" rule may let them pay their own creditors. Handing
     * Alice $40 when she owes Charlie $40 should not leave the money resting with Alice.
     *
     * <p>Implemented with an explicit work queue rather than recursion. The obligation graph can
     * contain cycles across three or more customers - netting only removes two-party ones - and
     * recursion there would be a stack overflow. Termination is guaranteed because a customer is
     * only enqueued after a strictly positive payment, and every payment strictly reduces
     * {@link ObligationLedger#totalOutstanding()}, which is finite and is only ever increased by
     * {@link #transfer}.
     *
     * @return only the cash that left {@code origin}'s own balance. Payments made further down
     *         the cascade belong to other customers' statements, not to this one's receipt.
     */
    private List<CashTransfer> settleDebtsOf(CustomerName origin) {
        List<CashTransfer> transfersByOrigin = new ArrayList<>();
        Deque<CustomerName> pending = new ArrayDeque<>();
        pending.addLast(origin);

        while (!pending.isEmpty()) {
            CustomerName debtorName = pending.removeFirst();
            Account debtor = accounts.get(debtorName);

            for (Map.Entry<CustomerName, Money> debt : ledger.creditorsOf(debtorName).entrySet()) {
                if (!debtor.balance().isPositive()) {
                    break;
                }
                CustomerName creditorName = debt.getKey();
                Money payment = Money.min(debtor.balance(), debt.getValue());
                if (!payment.isPositive()) {
                    continue;
                }

                debtor.debit(payment);
                accounts.get(creditorName).credit(payment);
                ledger.reduce(debtorName, creditorName, payment);

                if (debtorName.equals(origin)) {
                    transfersByOrigin.add(new CashTransfer(creditorName, payment));
                }
                pending.addLast(creditorName);
            }
        }
        return transfersByOrigin;
    }

    private Account require(CustomerName name) {
        Account account = accounts.get(name);
        if (account == null) {
            throw new CustomerNotFoundException(name);
        }
        return account;
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }
}
