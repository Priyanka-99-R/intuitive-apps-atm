package com.intuitiveapps.atm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intuitiveapps.atm.domain.Obligation.Direction.OWED_FROM;
import static com.intuitiveapps.atm.domain.Obligation.Direction.OWED_TO;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the sample session from the problem statement, step by step, asserting the exact
 * balances and obligations it shows.
 *
 * <p>This is the acceptance test for the whole exercise. Every ambiguous reading of the
 * specification was settled by making this pass without special cases, so if a future change
 * breaks one of these assertions it has broken the requirement, not the test.
 */
class SpecificationExampleTest {

    private final Bank bank = new Bank();

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");

    @Test
    @DisplayName("the sample session from the problem statement, replayed exactly")
    void replaysTheSampleSession() {
        // $ login Alice  ->  Your balance is $0
        assertThat(bank.login(ALICE).balance()).isEqualTo(Money.of(0));

        // $ deposit 100  ->  Your balance is $100
        assertThat(bank.deposit(ALICE, Money.of(100)).snapshot().balance()).isEqualTo(Money.of(100));

        // $ login Bob  ->  Your balance is $0
        assertThat(bank.login(BOB).balance()).isEqualTo(Money.of(0));

        // $ deposit 80  ->  Your balance is $80
        assertThat(bank.deposit(BOB, Money.of(80)).snapshot().balance()).isEqualTo(Money.of(80));

        // $ transfer Alice 50
        //   Transferred $50 to Alice
        //   Your balance is $30
        TransactionResult firstTransfer = bank.transfer(BOB, ALICE, Money.of(50));
        assertThat(firstTransfer.cashTransfers()).containsExactly(new CashTransfer(ALICE, Money.of(50)));
        assertThat(firstTransfer.snapshot().balance()).isEqualTo(Money.of(30));
        assertThat(firstTransfer.snapshot().obligations()).isEmpty();

        // $ transfer Alice 100
        //   Transferred $30 to Alice     <- only what Bob actually had
        //   Your balance is $0
        //   Owed $70 to Alice
        TransactionResult shortTransfer = bank.transfer(BOB, ALICE, Money.of(100));
        assertThat(shortTransfer.cashTransfers()).containsExactly(new CashTransfer(ALICE, Money.of(30)));
        assertThat(shortTransfer.snapshot().balance()).isEqualTo(Money.of(0));
        assertThat(shortTransfer.snapshot().obligations())
                .containsExactly(new Obligation(ALICE, Money.of(70), OWED_TO));
        assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(180));

        // $ deposit 30
        //   Transferred $30 to Alice     <- the deposit never lands in Bob's balance
        //   Your balance is $0
        //   Owed $40 to Alice
        TransactionResult depositAgainstDebt = bank.deposit(BOB, Money.of(30));
        assertThat(depositAgainstDebt.cashTransfers()).containsExactly(new CashTransfer(ALICE, Money.of(30)));
        assertThat(depositAgainstDebt.snapshot().balance()).isEqualTo(Money.of(0));
        assertThat(depositAgainstDebt.snapshot().obligations())
                .containsExactly(new Obligation(ALICE, Money.of(40), OWED_TO));

        // $ login Alice
        //   Your balance is $210
        //   Owed $40 from Bob
        CustomerSnapshot aliceReturns = bank.login(ALICE);
        assertThat(aliceReturns.balance()).isEqualTo(Money.of(210));
        assertThat(aliceReturns.obligations()).containsExactly(new Obligation(BOB, Money.of(40), OWED_FROM));

        // $ transfer Bob 30
        //   Your balance is $210         <- unchanged: NO cash moves
        //   Owed $10 from Bob
        //
        // This is the assertion that pins down the whole design. The transfer is satisfied by
        // cancelling $30 of what Bob already owed, so Alice's balance must not move and no
        // "Transferred" line may be printed.
        TransactionResult nettedTransfer = bank.transfer(ALICE, BOB, Money.of(30));
        assertThat(nettedTransfer.cashTransfers()).isEmpty();
        assertThat(nettedTransfer.snapshot().balance()).isEqualTo(Money.of(210));
        assertThat(nettedTransfer.snapshot().obligations())
                .containsExactly(new Obligation(BOB, Money.of(10), OWED_FROM));

        // $ login Bob
        //   Your balance is $0
        //   Owed $10 to Alice
        CustomerSnapshot bobReturns = bank.login(BOB);
        assertThat(bobReturns.balance()).isEqualTo(Money.of(0));
        assertThat(bobReturns.obligations()).containsExactly(new Obligation(ALICE, Money.of(10), OWED_TO));

        // $ deposit 100
        //   Transferred $10 to Alice
        //   Your balance is $90
        TransactionResult finalDeposit = bank.deposit(BOB, Money.of(100));
        assertThat(finalDeposit.cashTransfers()).containsExactly(new CashTransfer(ALICE, Money.of(10)));
        assertThat(finalDeposit.snapshot().balance()).isEqualTo(Money.of(90));
        assertThat(finalDeposit.snapshot().obligations()).isEmpty();

        // And the books balance: $310 was deposited in total and none of it left the bank.
        assertThat(totalCashInBank()).isEqualTo(Money.of(310));
        assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(220));
    }

    private Money totalCashInBank() {
        return bank.allCustomers().stream()
                .map(CustomerSnapshot::balance)
                .reduce(Money.ZERO, Money::plus);
    }

    @Test
    @DisplayName("obligations are reported from both sides consistently")
    void reportsBothSidesOfAnObligation() {
        bank.login(ALICE);
        bank.login(BOB);
        bank.transfer(BOB, ALICE, Money.of(25));

        assertThat(bank.snapshotOf(BOB).obligations())
                .containsExactly(new Obligation(ALICE, Money.of(25), OWED_TO));
        assertThat(bank.snapshotOf(ALICE).obligations())
                .containsExactly(new Obligation(BOB, Money.of(25), OWED_FROM));

        List<Obligation> aliceReceivables = bank.snapshotOf(ALICE).debtsOwedToMe();
        assertThat(aliceReceivables).hasSize(1);
        assertThat(bank.snapshotOf(ALICE).debtsOwedByMe()).isEmpty();
    }
}
