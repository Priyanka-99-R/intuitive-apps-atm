package com.intuitiveapps.atm.web;

import com.intuitiveapps.atm.domain.CashTransfer;
import com.intuitiveapps.atm.domain.CustomerSnapshot;
import com.intuitiveapps.atm.domain.Obligation;
import com.intuitiveapps.atm.domain.TransactionResult;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * The wire format, and the mapping to it.
 *
 * <p>Domain types are deliberately not serialised directly. Returning {@code CustomerSnapshot}
 * would publish the internal model as a public API contract, so every future refactor of the
 * domain would become a breaking change for clients. It would also leak
 * {@code Money}'s {@code BigDecimal} into JSON as a number, where a JavaScript client would parse
 * it into a {@code double} and reintroduce exactly the rounding error the domain took care to
 * avoid - amounts therefore cross the wire as strings.
 */
final class ApiModels {

    private ApiModels() {
    }

    record AmountRequest(@NotBlank String amount) {
    }

    record TransferRequest(@NotBlank String target, @NotBlank String amount) {
    }

    record ObligationView(String counterparty, String amount, String direction) {
    }

    record CustomerView(String name, String balance, List<ObligationView> obligations) {
    }

    record CashTransferView(String to, String amount) {
    }

    record TransactionView(List<CashTransferView> transfers, CustomerView customer) {
    }

    static CustomerView toView(CustomerSnapshot snapshot) {
        return new CustomerView(
                snapshot.name().value(),
                snapshot.balance().toString(),
                snapshot.obligations().stream()
                        .map(ApiModels::toView)
                        .toList());
    }

    static TransactionView toView(TransactionResult result) {
        return new TransactionView(
                result.cashTransfers().stream().map(ApiModels::toView).toList(),
                toView(result.snapshot()));
    }

    private static ObligationView toView(Obligation obligation) {
        return new ObligationView(
                obligation.counterparty().value(),
                obligation.amount().toString(),
                obligation.direction().name());
    }

    private static CashTransferView toView(CashTransfer transfer) {
        return new CashTransferView(transfer.to().value(), transfer.amount().toString());
    }
}
