package com.intuitiveapps.atm.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP surface for the ATM.
 *
 * <p><strong>The API is stateless: there is no server-side session and no "currently logged in"
 * customer.</strong> Every request names the customer it acts for. The CLI keeps a session
 * because a terminal <em>is</em> one conversation with one person standing at it; a web server
 * talks to many clients at once, and a single global logged-in user would be wrong the moment two
 * browsers connected. The browser page holds "who am I" on the client side instead.
 *
 * <p>Note also that this controller contains no rules. It converts HTTP to a method call and a
 * result to JSON. Everything that could be called a decision lives in the domain.
 */
@RestController
@RequestMapping("/api/customers")
class AtmController {

    private final BankService bankService;

    AtmController(BankService bankService) {
        this.bankService = bankService;
    }

    /** Logs in, creating the customer if this is a name the bank has not seen. */
    @PostMapping("/{name}/login")
    ApiModels.CustomerView login(@PathVariable("name") String name) {
        return ApiModels.toView(bankService.login(name));
    }

    @GetMapping("/{name}")
    ApiModels.CustomerView get(@PathVariable("name") String name) {
        return ApiModels.toView(bankService.snapshot(name));
    }

    /** Everybody the bank knows about - used by the browser page to offer transfer targets. */
    @GetMapping
    List<ApiModels.CustomerView> list() {
        return bankService.allCustomers().stream().map(ApiModels::toView).toList();
    }

    @PostMapping("/{name}/deposit")
    ApiModels.TransactionView deposit(@PathVariable("name") String name,
                                      @Valid @RequestBody ApiModels.AmountRequest request) {
        return ApiModels.toView(bankService.deposit(name, request.amount()));
    }

    @PostMapping("/{name}/withdraw")
    ApiModels.TransactionView withdraw(@PathVariable("name") String name,
                                       @Valid @RequestBody ApiModels.AmountRequest request) {
        return ApiModels.toView(bankService.withdraw(name, request.amount()));
    }

    @PostMapping("/{name}/transfer")
    ApiModels.TransactionView transfer(@PathVariable("name") String name,
                                       @Valid @RequestBody ApiModels.TransferRequest request) {
        return ApiModels.toView(bankService.transfer(name, request.target(), request.amount()));
    }
}
