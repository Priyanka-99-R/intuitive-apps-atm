package com.intuitiveapps.atm.domain;

import com.intuitiveapps.atm.domain.exception.CustomerNotFoundException;
import com.intuitiveapps.atm.domain.exception.InsufficientFundsException;
import com.intuitiveapps.atm.domain.exception.InvalidAmountException;
import com.intuitiveapps.atm.domain.exception.SelfTransferException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.intuitiveapps.atm.domain.Obligation.Direction.OWED_FROM;
import static com.intuitiveapps.atm.domain.Obligation.Direction.OWED_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Rules, edge cases and the invariants that hold across all of them. */
class BankTest {

    private final Bank bank = new Bank();

    private static final CustomerName ALICE = CustomerName.of("Alice");
    private static final CustomerName BOB = CustomerName.of("Bob");
    private static final CustomerName CAROL = CustomerName.of("Carol");

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        void createsTheCustomerOnFirstLogin() {
            assertThat(bank.hasCustomer(ALICE)).isFalse();

            CustomerSnapshot snapshot = bank.login(ALICE);

            assertThat(bank.hasCustomer(ALICE)).isTrue();
            assertThat(snapshot.balance()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("logging in again returns the existing account rather than resetting it")
        void isIdempotent() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(50));

            assertThat(bank.login(ALICE).balance()).isEqualTo(Money.of(50));
        }

        @Test
        @DisplayName("names are case sensitive - Alice and alice are different customers")
        void treatsNamesAsCaseSensitive() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(50));

            assertThat(bank.login(CustomerName.of("alice")).balance()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("deposit and withdraw")
    class DepositAndWithdraw {

        @Test
        void depositIncreasesTheBalance() {
            bank.login(ALICE);

            assertThat(bank.deposit(ALICE, Money.of(100)).snapshot().balance()).isEqualTo(Money.of(100));
        }

        @Test
        void withdrawDecreasesTheBalance() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(100));

            assertThat(bank.withdraw(ALICE, Money.of(40)).snapshot().balance()).isEqualTo(Money.of(60));
        }

        @Test
        @DisplayName("withdrawing the exact balance is allowed")
        void allowsWithdrawingEverything() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(100));

            assertThat(bank.withdraw(ALICE, Money.of(100)).snapshot().balance()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("withdrawing more than the balance is refused, and changes nothing")
        void refusesToOverdraw() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(100));

            assertThatExceptionOfType(InsufficientFundsException.class)
                    .isThrownBy(() -> bank.withdraw(ALICE, Money.of(101)))
                    .withMessageContaining("$101")
                    .withMessageContaining("$100");

            assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(100));
        }

        @Test
        void rejectsZeroAndNegativeAmounts() {
            bank.login(ALICE);

            assertThatExceptionOfType(InvalidAmountException.class)
                    .isThrownBy(() -> bank.deposit(ALICE, Money.ZERO));
            assertThatExceptionOfType(InvalidAmountException.class)
                    .isThrownBy(() -> bank.withdraw(ALICE, Money.ZERO));
            assertThatExceptionOfType(InvalidAmountException.class)
                    .isThrownBy(() -> Money.of(-1));
        }

        @Test
        void rejectsOperationsOnUnknownCustomers() {
            assertThatExceptionOfType(CustomerNotFoundException.class)
                    .isThrownBy(() -> bank.deposit(ALICE, Money.of(10)))
                    .withMessageContaining("Alice");
        }

        @Test
        @DisplayName("cent amounts survive a round trip without floating point drift")
        void handlesFractionalAmounts() {
            bank.login(ALICE);
            for (int i = 0; i < 10; i++) {
                bank.deposit(ALICE, Money.parse("0.10"));
            }

            assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(1));
        }
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        void movesCashWhenTheSenderCanCoverIt() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.deposit(ALICE, Money.of(100));

            TransactionResult result = bank.transfer(ALICE, BOB, Money.of(40));

            assertThat(result.cashTransfers()).containsExactly(new CashTransfer(BOB, Money.of(40)));
            assertThat(result.snapshot().balance()).isEqualTo(Money.of(60));
            assertThat(bank.snapshotOf(BOB).balance()).isEqualTo(Money.of(40));
        }

        @Test
        @DisplayName("a transfer with no balance at all creates a debt and moves nothing")
        void createsDebtWhenTheSenderHasNothing() {
            bank.login(ALICE);
            bank.login(BOB);

            TransactionResult result = bank.transfer(ALICE, BOB, Money.of(40));

            assertThat(result.cashTransfers()).isEmpty();
            assertThat(result.snapshot().obligations())
                    .containsExactly(new Obligation(BOB, Money.of(40), OWED_TO));
        }

        @Test
        @DisplayName("transferring to someone who owes you nets off instead of moving cash")
        void netsOffFacingDebts() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.transfer(BOB, ALICE, Money.of(40));   // Bob owes Alice 40
            bank.deposit(ALICE, Money.of(100));

            TransactionResult result = bank.transfer(ALICE, BOB, Money.of(15));

            assertThat(result.cashTransfers()).isEmpty();
            assertThat(result.snapshot().balance()).isEqualTo(Money.of(100));
            assertThat(result.snapshot().obligations())
                    .containsExactly(new Obligation(BOB, Money.of(25), OWED_FROM));
        }

        @Test
        @DisplayName("netting off more than is owed cancels the debt and moves the remainder")
        void netsOffThenPaysTheRemainder() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.transfer(BOB, ALICE, Money.of(40));   // Bob owes Alice 40
            bank.deposit(ALICE, Money.of(100));

            TransactionResult result = bank.transfer(ALICE, BOB, Money.of(70));

            assertThat(result.cashTransfers()).containsExactly(new CashTransfer(BOB, Money.of(30)));
            assertThat(result.snapshot().balance()).isEqualTo(Money.of(70));
            assertThat(result.snapshot().obligations()).isEmpty();
            assertThat(bank.snapshotOf(BOB).balance()).isEqualTo(Money.of(30));
        }

        @Test
        void refusesTransferToYourself() {
            bank.login(ALICE);

            assertThatExceptionOfType(SelfTransferException.class)
                    .isThrownBy(() -> bank.transfer(ALICE, ALICE, Money.of(10)));
        }

        @Test
        @DisplayName("refuses a transfer to a name that has never logged in")
        void refusesUnknownTarget() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(100));

            assertThatExceptionOfType(CustomerNotFoundException.class)
                    .isThrownBy(() -> bank.transfer(ALICE, CustomerName.of("Nobody"), Money.of(10)));

            assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(100));
        }

        @Test
        @DisplayName("a rejected transfer leaves the ledger untouched")
        void isAtomicOnFailure() {
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(100));

            assertThatExceptionOfType(CustomerNotFoundException.class)
                    .isThrownBy(() -> bank.transfer(ALICE, BOB, Money.of(10)));

            assertThat(bank.snapshotOf(ALICE).obligations()).isEmpty();
            assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(100));
        }
    }

    @Nested
    @DisplayName("debt settlement")
    class Settlement {

        @Test
        @DisplayName("debts are repaid oldest first")
        void repaysInTheOrderDebtsWereIncurred() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.login(CAROL);

            bank.transfer(ALICE, BOB, Money.of(50));      // debt 1: Alice -> Bob
            bank.transfer(ALICE, CAROL, Money.of(50));    // debt 2: Alice -> Carol

            TransactionResult result = bank.deposit(ALICE, Money.of(60));

            assertThat(result.cashTransfers()).containsExactly(
                    new CashTransfer(BOB, Money.of(50)),
                    new CashTransfer(CAROL, Money.of(10)));
            assertThat(result.snapshot().obligations())
                    .containsExactly(new Obligation(CAROL, Money.of(40), OWED_TO));
        }

        @Test
        @DisplayName("an incoming transfer settles the recipient's own debts too")
        void cascadesThroughTheChain() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.login(CAROL);

            bank.transfer(BOB, CAROL, Money.of(30));      // Bob owes Carol 30
            bank.login(ALICE);
            bank.deposit(ALICE, Money.of(100));

            bank.transfer(ALICE, BOB, Money.of(30));      // Alice pays Bob, who owes Carol

            assertThat(bank.snapshotOf(BOB).balance()).isEqualTo(Money.ZERO);
            assertThat(bank.snapshotOf(BOB).obligations()).isEmpty();
            assertThat(bank.snapshotOf(CAROL).balance()).isEqualTo(Money.of(30));
        }

        @Test
        @DisplayName("a cycle of three debtors settles without recursing forever")
        void terminatesOnACycle() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.login(CAROL);

            bank.transfer(ALICE, BOB, Money.of(100));
            bank.transfer(BOB, CAROL, Money.of(100));
            bank.transfer(CAROL, ALICE, Money.of(100));

            bank.deposit(ALICE, Money.of(100));

            // The $100 chases itself round the ring, clearing every debt and returning home.
            assertThat(bank.snapshotOf(ALICE).obligations()).isEmpty();
            assertThat(bank.snapshotOf(BOB).obligations()).isEmpty();
            assertThat(bank.snapshotOf(CAROL).obligations()).isEmpty();
            assertThat(bank.snapshotOf(ALICE).balance()).isEqualTo(Money.of(100));
        }

        @Test
        @DisplayName("invariant: a customer holding cash owes nobody")
        void positiveBalanceImpliesNoDebt() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.transfer(ALICE, BOB, Money.of(75));
            bank.deposit(ALICE, Money.of(200));

            CustomerSnapshot alice = bank.snapshotOf(ALICE);
            assertThat(alice.balance()).isEqualTo(Money.of(125));
            assertThat(alice.debtsOwedByMe()).isEmpty();
        }

        @Test
        @DisplayName("invariant: cash is conserved - deposits in equal balances out")
        void conservesCash() {
            bank.login(ALICE);
            bank.login(BOB);
            bank.login(CAROL);

            bank.deposit(ALICE, Money.of(100));
            bank.deposit(BOB, Money.of(50));
            bank.transfer(ALICE, CAROL, Money.of(500));   // mostly debt
            bank.deposit(ALICE, Money.of(120));
            bank.transfer(CAROL, BOB, Money.of(30));
            bank.withdraw(BOB, Money.of(20));

            Money totalHeld = bank.allCustomers().stream()
                    .map(CustomerSnapshot::balance)
                    .reduce(Money.ZERO, Money::plus);

            assertThat(totalHeld).isEqualTo(Money.of(250));   // 100 + 50 + 120 deposited, 20 withdrawn
        }
    }
}
