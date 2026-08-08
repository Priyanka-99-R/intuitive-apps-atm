# ATM

A simulation of an ATM talking to a retail bank, with a command line interface and a browser
interface over the same core.

```bash
./start.sh          # command line application
./start-web.sh      # browser version at http://localhost:8080
./run-tests.sh      # the full test suite
```

Nothing else is needed. The first run downloads Maven and the dependencies and takes a minute or
two; after that it starts immediately.

---

## Contents

1. [Running it](#1-running-it)
2. [Instruction manual](#2-instruction-manual)
3. [How it is put together](#3-how-it-is-put-together)
4. [The rule that drives the whole design](#4-the-rule-that-drives-the-whole-design)
5. [Design decisions](#5-design-decisions)
6. [Special cases and how they are handled](#6-special-cases-and-how-they-are-handled)
7. [Assumptions and deviations](#7-assumptions-and-deviations)
8. [Testing](#8-testing)
9. [What I would do next](#9-what-i-would-do-next)

---

## 1. Running it

**Requirements:** a JDK (17 or newer) on the `PATH`, and internet access on the first run.

| Command | What it does |
|---|---|
| `./start.sh` | Builds and launches the CLI. This is the main deliverable. |
| `./start-web.sh` | Builds and launches the Spring Boot server; open <http://localhost:8080>. `PORT=9090 ./start-web.sh` to use another port. |
| `./run-tests.sh` | Runs all 82 tests. |

There is no build tool to install. The repository carries a **script-only Maven wrapper**
(`mvnw`), which downloads Maven itself on first use — so there is no `maven-wrapper.jar` and, as
the brief requires, **no binary of any kind is committed**.

**Every invocation starts clean.** The bank lives in memory and nothing is written to disk, so
stopping the process is the only reset the system has. Restart it and no customers exist.

Piping input works, which is how the transcripts in [SAMPLE-SESSION.md](SAMPLE-SESSION.md) were
produced:

```bash
printf 'login Alice\ndeposit 100\nlogout\n' | ./start.sh
```

---

## 2. Instruction manual

### Commands

| Command | Effect |
|---|---|
| `login [name]` | Log in as this customer, creating the account if the name is new. |
| `deposit [amount]` | Pay money in. Any debts you owe are settled first, out of the money you just paid in. |
| `withdraw [amount]` | Take money out. Refused if you do not have it. |
| `transfer [target] [amount]` | Send money to another customer. If you cannot cover it, the shortfall becomes a debt. |
| `logout` | End the session. |
| `help` | List the commands. |
| `exit` / `quit` | Quit. Ctrl-D does the same. |

Verbs may be typed in any case (`LOGIN`, `Login`, `login`). **Customer names are case sensitive** —
`Alice` and `alice` are two different people. Blank lines and lines beginning with `#` are ignored,
which makes scripted sessions readable.

### What the output means

```
$ login Bob
Hello, Bob!              <- greeting, on login only
Your balance is $0       <- cash you can withdraw right now
Owed $10 to Alice        <- you owe Alice $10
Owed $40 from Bob        <- Bob owes you $40
```

```
$ transfer Alice 100
Transferred $30 to Alice <- cash that actually moved (you only had $30)
Your balance is $0
Owed $70 to Alice        <- the rest became a debt
```

A `Transferred` line appears **only when cash genuinely moved**. A transfer that is entirely
absorbed by cancelling an existing debt prints no such line, because nothing moved.

### The browser version

`./start-web.sh`, then <http://localhost:8080>. Log in as one customer in one browser tab and as
another in a second tab to watch transfers and debts appear on both sides. The page also keeps a
running log in the same wording as the CLI.

The REST API underneath it:

| Method | Path | |
|---|---|---|
| `POST` | `/api/customers/{name}/login` | log in, creating if new |
| `GET` | `/api/customers/{name}` | current position |
| `GET` | `/api/customers` | everyone the bank knows |
| `POST` | `/api/customers/{name}/deposit` | `{"amount":"100"}` |
| `POST` | `/api/customers/{name}/withdraw` | `{"amount":"40"}` |
| `POST` | `/api/customers/{name}/transfer` | `{"target":"Bob","amount":"50"}` |

```bash
curl -X POST localhost:8080/api/customers/Alice/login
curl -X POST localhost:8080/api/customers/Alice/deposit \
     -H 'Content-Type: application/json' -d '{"amount":"100"}'
```

---

## 3. How it is put together

```
                 ┌──────────────┐            ┌──────────────┐
   terminal ───► │   atm-cli    │            │   atm-web    │ ◄─── browser / HTTP
                 │  REPL, parser│            │ Spring Boot  │
                 │  formatting  │            │ REST + page  │
                 └──────┬───────┘            └──────┬───────┘
                        │                           │
                        └──────────┬────────────────┘
                                   ▼
                          ┌─────────────────┐
                          │   atm-domain    │   no dependencies at all
                          │  Bank · Money   │   no Spring, no JSON, no logging
                          │ ObligationLedger│
                          └─────────────────┘
```

Three Maven modules, and the dependency arrows only ever point inwards:

| Module | Contains | Depends on |
|---|---|---|
| `atm-domain` | Every banking rule. `Bank`, `Money`, `ObligationLedger`, `Account`. | **nothing** |
| `atm-cli` | Command parsing, the REPL, output wording. | `atm-domain` |
| `atm-web` | Spring Boot, REST controller, error mapping, the HTML page. | `atm-domain` |

`atm-domain/pom.xml` has **no `<dependencies>` section**. That is not a stylistic preference — it
is the constraint enforced by the build. The rules cannot accidentally acquire a dependency on a
framework, because there is nowhere for one to come from. `atm-cli` and `atm-web` cannot see each
other, so either could be deleted without touching a line of business logic.

The practical payoff shows up in the tests: the domain's 45 tests run in under a second with no
container, no HTTP and no mocking, because there is nothing to stand up.

---

## 4. The rule that drives the whole design

Most of this problem is straightforward. One line of the sample session is not, and everything
else follows from getting it right:

```
$ login Alice
Your balance is $210
Owed $40 from Bob

$ transfer Bob 30
Your balance is $210      <- unchanged!
Owed $10 from Bob
```

Alice transfers $30 to Bob and **her balance does not move, and no `Transferred` line is
printed**. The only reading that produces this is that obligations are held **netted per pair**:
Bob owed her $40, so the $30 transfer is satisfied by cancelling $30 of that debt. No cash needs
to change hands at all.

Had the two debts been tracked separately, Alice would have paid $30 in cash and ended up owed $40
while owing $30 — a different balance and a different display. So `ObligationLedger` keeps at most
one non-zero direction per pair, and a transfer nets off before it moves anything.

The second rule follows from the deposit that settles a debt:

> **Any credit settles debts immediately.** The moment a balance increases, it is applied to
> whatever that customer owes before it is theirs to keep.

The brief shows this for `deposit`. Applying it to *incoming transfers* too is the consistent
reading, and it buys a useful invariant: **a customer holding cash owes nobody.** That is why
`withdraw` needs no special handling for debt — there is never money sitting in an account that
has already been promised away. Both invariants are asserted in `BankTest`.

---

## 5. Design decisions

### Money is `BigDecimal` behind a value type, never `double`

`double` cannot represent `0.10`, so balances drift; ten deposits of ten cents would not make a
dollar. A `long` of cents would be correct but scatters `/100` and `*100` across every call site,
and the first omission is a silent hundredfold error. `Money` wraps `BigDecimal` at a fixed scale
of 2, is immutable, and **cannot be negative** — the direction of a debt is carried by an enum, so
a dropped minus sign cannot invert an obligation. `MoneyTest` asserts the ten-cents case directly.

### Amounts are rejected, not rounded

`deposit 10.999` is refused rather than quietly becoming `11.00`. Helpfully rounding somebody's
money is how you lose their trust. Input is also matched against a plain-decimal pattern rather
than handed to `new BigDecimal(String)`, which would otherwise accept `1e9`.

### Session state lives in the adapter, not the bank

A `Bank` has customers; it has no concept of "who is logged in". A *terminal* has exactly one
person standing at it, so the CLI owns a `Session`. The web API is **stateless** and names the
customer on every request — a single global logged-in user would be obviously wrong the moment two
browsers connected. Putting the session in the domain would have forced that wrongness on both.

### Unchecked exceptions with one common supertype

Every rule violation extends `AtmException` and carries a message safe to show a customer. That
lets each adapter translate the whole family at a single point: one `catch` in the shell, one
`@RestControllerAdvice` in the web module. Checked exceptions would push `throws` clauses through
layers that cannot act on them, which in practice produces the empty `catch` block that hides them.

Unexpected exceptions are deliberately **not** caught. A defect should surface, not be swallowed.

### The web layer does not serialise domain types

Returning `CustomerSnapshot` directly would publish the internal model as a public contract, making
every later refactor a breaking change. It would also send `Money` as a JSON *number*, which a
JavaScript client would parse into a `double` — reintroducing exactly the rounding error the domain
took care to avoid. **Amounts cross the wire as strings**, and there is a test for it.

### The lock is in the web module, not in `Bank`

`Bank` is not thread safe. A Spring bean is a singleton shared by every request thread, so two
concurrent transfers would interleave a read with somebody else's write and lose money.
`BankService` serialises access with a single `ReentrantLock`.

It lives there rather than inside `Bank` because the CLI is one conversation at one terminal and
has no contention at all — making the domain synchronise would charge it for a problem it does not
have and bury a deployment concern in the business rules. **The adapter that introduces
concurrency is the adapter that pays for it.**

One coarse lock rather than a lock per account, because every operation is a few map lookups and
per-account locking would mean acquiring two locks for a transfer — precisely the lock-ordering
deadlock every ATM example is famous for. At a scale where that mattered, the answer is a database
with row-level locking, not a cleverer arrangement of monitors.

### Settlement is iterative, not recursive

Paying a creditor increases *their* balance, which may let them pay *their* creditors — handing
Alice $40 when she owes Charlie $40 should not leave the money resting with Alice. Netting removes
two-party cycles but not three-party ones, so the cascade uses an explicit work queue; recursion
would risk a stack overflow. Termination is guaranteed because a customer is only enqueued after a
strictly positive payment, and every payment strictly reduces the finite total outstanding debt.
`BankTest` includes a three-way cycle.

### No library solves the problem

Spring provides HTTP, JUnit and AssertJ provide testing. The banking rules, the ledger and the
settlement algorithm are written here.

---

## 6. Special cases and how they are handled

| Situation | Behaviour | Reasoning |
|---|---|---|
| `deposit` before `login` | Refused, session continues | Nothing to act on |
| `login` while already logged in | Refused, "logout first" | Silently switching customer is how you deposit into the wrong account |
| `logout` when not logged in | Refused | Nothing to end |
| Transfer to yourself | Refused | Cannot change any balance, so it is far likelier a typo than an intention |
| **Transfer to a name that has never logged in** | **Refused** | See below — the one place I could argue myself either way |
| Transfer larger than your balance | Allowed; shortfall becomes a debt | This is the required behaviour |
| Withdrawal larger than your balance | Refused | A transfer has a counterparty who can be owed the shortfall; a withdrawal hands over notes and there is nobody to owe |
| Amount of `0` or negative | Refused | Not an operation |
| More than 2 decimal places | Refused, not rounded | Silently altering an amount is worse than refusing it |
| Unparseable amount, unknown command, wrong argument count | Specific message, session continues | A mistyped command is part of using an ATM, not a reason to hang up |
| Name with a space, or `@`, or over 40 characters | Refused with the rule stated | The CLI tokenises on whitespace, so `transfer Mary Jane 50` would be ambiguous |
| `exit` or Ctrl-D while logged in | Implicit logout, says goodbye | Walking away from a terminal is normal; refusing to let somebody quit is not |
| Owing several people at once | Repaid oldest first | Least surprising, and easiest to explain to a customer |
| Debt cycle across three customers | Settles and terminates | Covered by a test |
| Blank line or `#` comment | Ignored | Makes scripted sessions readable |

**On refusing transfers to unknown customers.** The brief says `login` creates a customer, and says
nothing about `transfer` doing so. Auto-creating would make `transfer Alcie 500` — a typo — open an
account for "Alcie" and put a real debt against it, with no way to notice. Refusing means a
customer must have used the bank at least once before they can be paid, which is the behaviour a
real institution has. It is a one-line change in `Bank.transfer` if the intent was the opposite.

---

## 7. Assumptions and deviations

1. **`your balance is $30` in the brief is a typo.** Every other occurrence is capitalised, so the
   implementation prints `Your balance is` consistently. Flagging rather than silently copying.

2. **Single currency, and the `$` is presentation.** The domain deals in `Money`, not dollars; the
   symbol is added by the adapter. Multi-currency would need a currency on `Money` and a policy for
   cross-currency obligations — well beyond this brief.

3. **No authentication.** `login [name]` has no PIN because the brief defines it that way. Real
   authentication would sit in front of this, and would not change the domain.

4. **The output adds a two-line banner** at startup, and a `help` command. The brief invites extra
   output. Nothing was removed from the required output, and the required lines are asserted
   verbatim in `AtmShellTest`.

5. **Amounts support cents** even though the examples are all whole dollars. Costs nothing and
   removes a class of bug. Whole amounts still display as `$100`, not `$100.00`.

6. **The two interfaces format their output independently.** `atm-cli` and `atm-web` cannot depend
   on each other, so the wording exists in both. Sharing it would mean a fourth module for what is
   currently a handful of lines — the wrong trade today, and an easy change if the wording started
   to drift.

7. **Case sensitivity is asymmetric:** verbs are case insensitive, names are not. A verb is part of
   the interface and should be forgiving; a name is data, and folding its case is a decision about
   identity that would silently merge two customers.

8. **Java 17 rather than 21.** `Bank` would read slightly better with pattern-matching `switch`,
   but 17 is the widest LTS target and the difference is cosmetic. The `if`/`instanceof` chain in
   `AtmShell` is the one place this shows.

---

## 8. Testing

**82 tests.** `./run-tests.sh`

| Where | Count | What it covers |
|---|---|---|
| `SpecificationExampleTest` | 2 | The brief's sample session, replayed step by step, asserting every balance and obligation |
| `MoneyTest` | 21 | Parsing, rejection, exact decimal arithmetic, formatting |
| `BankTest` | 22 | Login, deposit, withdraw, transfer, netting, FIFO repayment, cascades, cycles, and two system-wide invariants |
| `CommandParserTest` | 21 | Every command, casing, whitespace, arity, unknown verbs, delegation of validation |
| `AtmShellTest` | 8 | The complete transcript compared line by line; error recovery; clean start |
| `AtmControllerTest` | 8 | Routing, JSON shape, and domain failures mapped to 400/404/409 |

Two of these are worth calling out.

**`SpecificationExampleTest`** is the acceptance test for the exercise. Every ambiguous reading of
the brief was settled by making it pass without special cases, so if a change breaks it, it has
broken the requirement rather than the test.

**`AtmShellTest.reproducesTheSampleTranscript`** drives the real application through its real entry
point and compares the entire transcript, line for line, against the brief. The unit tests prove
the arithmetic; this proves the product. It is also why `AtmShell` takes a `Reader` and a `Writer`
instead of reaching for `System.in` — testability was a design input, not an afterthought.

The web tests deliberately do **not** re-test banking rules. That would be the same assertions
written twice in a slower harness; they assert only what the HTTP layer can get wrong.

---

## 9. What I would do next

Being explicit about the edges, since this is a simulation and not a bank:

- **Persistence.** Everything is in memory by requirement. Real accounts need a database, which
  also replaces the coarse lock with row-level locking and proper transactions.
- **An audit trail.** A bank should record an append-only history of movements; balances would
  become a projection of it. The `CashTransfer` type is already the shape of such an event.
- **Authentication**, per assumption 3.
- **A `statement` command.** The domain already returns everything it needs.
- **Structured logging** in the web module. Left out to keep the console readable for a demo, but
  the first thing a production deployment would want.
