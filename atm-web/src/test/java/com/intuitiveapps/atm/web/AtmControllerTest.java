package com.intuitiveapps.atm.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the HTTP adapter: routing, JSON shape and the mapping from domain failures to status
 * codes.
 *
 * <p>The banking rules are already covered by the domain module's tests and are not re-tested
 * here - that would be the same assertions written twice, in a slower harness. What this class
 * asserts is the part only the web layer can get wrong.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AtmControllerTest {

    @Autowired
    private MockMvc mvc;

    private void login(String name) throws Exception {
        mvc.perform(post("/api/customers/{name}/login", name)).andExpect(status().isOk());
    }

    private void deposit(String name, String amount) throws Exception {
        mvc.perform(post("/api/customers/{name}/deposit", name)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("login creates the customer and reports a zero balance")
    void loginCreatesCustomer() throws Exception {
        mvc.perform(post("/api/customers/{name}/login", "Wanda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wanda"))
                .andExpect(jsonPath("$.balance").value("0"))
                .andExpect(jsonPath("$.obligations").isEmpty());
    }

    @Test
    @DisplayName("a shortfall on transfer becomes an obligation, not an error")
    void transferBeyondBalanceCreatesObligation() throws Exception {
        login("Tina");
        login("Theo");
        deposit("Tina", "30");

        mvc.perform(post("/api/customers/{name}/transfer", "Tina")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"Theo\",\"amount\":\"100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers[0].to").value("Theo"))
                .andExpect(jsonPath("$.transfers[0].amount").value("30"))
                .andExpect(jsonPath("$.customer.balance").value("0"))
                .andExpect(jsonPath("$.customer.obligations[0].amount").value("70"))
                .andExpect(jsonPath("$.customer.obligations[0].direction").value("OWED_TO"));
    }

    @Test
    @DisplayName("amounts cross the wire as strings, so a JS client cannot round them")
    void amountsAreSerialisedAsStrings() throws Exception {
        login("Sam");
        deposit("Sam", "0.10");

        mvc.perform(get("/api/customers/{name}", "Sam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("0.10"))
                .andExpect(jsonPath("$.balance").isString());
    }

    @Test
    @DisplayName("an unknown customer is 404")
    void unknownCustomerIsNotFound() throws Exception {
        mvc.perform(get("/api/customers/{name}", "Ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer not found"))
                .andExpect(jsonPath("$.detail").value("No such customer: Ghost"));
    }

    @Test
    @DisplayName("overdrawing is 409 - the request is fine, the state is not")
    void overdraftIsConflict() throws Exception {
        login("Nina");
        deposit("Nina", "20");

        mvc.perform(post("/api/customers/{name}/withdraw", "Nina")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"50\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient funds"));
    }

    @Test
    @DisplayName("a malformed amount is 400")
    void malformedAmountIsBadRequest() throws Exception {
        login("Ravi");

        mvc.perform(post("/api/customers/{name}/deposit", "Ravi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"lots\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "'lots' is not a valid amount. Expected a number such as 100 or 24.50"));
    }

    @Test
    @DisplayName("a missing field is rejected by validation before it reaches the domain")
    void missingFieldIsBadRequest() throws Exception {
        login("Priya");

        mvc.perform(post("/api/customers/{name}/deposit", "Priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    @DisplayName("transferring to yourself is 400")
    void selfTransferIsBadRequest() throws Exception {
        login("Omar");

        mvc.perform(post("/api/customers/{name}/transfer", "Omar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"Omar\",\"amount\":\"5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot transfer to yourself, Omar"));
    }
}
