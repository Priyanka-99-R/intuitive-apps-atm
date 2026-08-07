# Sample sessions

These transcripts were **captured by piping commands into the built application**, not
written by hand. Regenerate them at any time with the commands shown above each one.

---

## 1. The session from the problem statement

Reproduced exactly. Every balance, every `Owed` line and every `Transferred` line matches
the brief. This same sequence is asserted line by line in `AtmShellTest`, so it cannot
silently drift.

```bash
printf 'login Alice
deposit 100
logout
login Bob
deposit 80
transfer Alice 50
transfer Alice 100
deposit 30
logout
login Alice
transfer Bob 30
logout
login Bob
deposit 100
logout
' | ./start.sh
```

```
ATM ready. Type 'help' for commands, 'exit' to quit.
This session starts empty - no customers exist until somebody logs in.

$ login Alice
Hello, Alice!
Your balance is $0

$ deposit 100
Your balance is $100

$ logout
Goodbye, Alice!

$ login Bob
Hello, Bob!
Your balance is $0

$ deposit 80
Your balance is $80

$ transfer Alice 50
Transferred $50 to Alice
Your balance is $30

$ transfer Alice 100
Transferred $30 to Alice
Your balance is $0
Owed $70 to Alice

$ deposit 30
Transferred $30 to Alice
Your balance is $0
Owed $40 to Alice

$ logout
Goodbye, Bob!

$ login Alice
Hello, Alice!
Your balance is $210
Owed $40 from Bob

$ transfer Bob 30
Your balance is $210
Owed $10 from Bob

$ logout
Goodbye, Alice!

$ login Bob
Hello, Bob!
Your balance is $0
Owed $10 to Alice

$ deposit 100
Transferred $10 to Alice
Your balance is $90

$ logout
Goodbye, Bob!

$ 
```

> The one line that pins down the whole design is Alice's `transfer Bob 30`. Her balance
> stays at `$210` and no `Transferred` line is printed, because the transfer is satisfied
> entirely by cancelling $30 of the $40 Bob already owed her. No cash needs to move.

---

## 2. Error handling and edge cases

Every one of these is refused with a specific, actionable message, and the session carries
on. Nothing is silently ignored and nothing crashes the application.

```
ATM ready. Type 'help' for commands, 'exit' to quit.
This session starts empty - no customers exist until somebody logs in.

$ deposit 50
Error: You are not logged in. Use 'login [name]' first.

$ login Alice
Hello, Alice!
Your balance is $0

$ login Bob
Error: Already logged in as Alice. Use 'logout' first.

$ deposit
Error: Usage: deposit [amount]

$ deposit abc
Error: 'abc' is not a valid amount. Expected a number such as 100 or 24.50

$ deposit -5
Error: '-5' is not a valid amount. Expected a number such as 100 or 24.50

$ deposit 0
Error: Amount must be greater than zero

$ deposit 10.999
Error: '10.999' is not a valid amount. Expected a number such as 100 or 24.50

$ withdraw 100
Error: Cannot withdraw $100 - your balance is $0

$ frobnicate
Error: Unknown command 'frobnicate'. Type 'help' to see the available commands.

$ transfer Nobody 5
Error: No such customer: Nobody

$ transfer Alice 5
Error: Cannot transfer to yourself, Alice

$ login @lice
Error: '@lice' is not a valid customer name. Use up to 40 letters, digits, underscores, hyphens or apostrophes, with no spaces

$ deposit 100
Your balance is $100

$ withdraw 40
Your balance is $60

$ withdraw 60
Your balance is $0

$ withdraw 0.01
Error: Cannot withdraw $0.01 - your balance is $0

$ logout
Goodbye, Alice!

$ logout
Error: You are not logged in. Use 'login [name]' first.

$ exit
```

---

## 3. Debts settling down a chain, and exact cent arithmetic

Carol owes Alice $100 and has nothing. Bob then pays Carol $60 - and that money does not
stop with Carol, it passes straight through to Alice, because any credit settles debts
immediately. Carol ends at $0 still owing $40, and Alice is $60 better off without having
done anything.

Dave then deposits $0.10 ten times and lands on exactly $1 - the reason amounts are
`BigDecimal` and never `double`.

```
ATM ready. Type 'help' for commands, 'exit' to quit.
This session starts empty - no customers exist until somebody logs in.

$ login Alice
Hello, Alice!
Your balance is $0

$ logout
Goodbye, Alice!

$ login Carol
Hello, Carol!
Your balance is $0

$ transfer Alice 100
Your balance is $0
Owed $100 to Alice

$ logout
Goodbye, Carol!

$ login Bob
Hello, Bob!
Your balance is $0

$ deposit 60
Your balance is $60

$ transfer Carol 60
Transferred $60 to Carol
Your balance is $0

$ logout
Goodbye, Bob!

$ login Alice
Hello, Alice!
Your balance is $60
Owed $40 from Carol

$ logout
Goodbye, Alice!

$ login Carol
Hello, Carol!
Your balance is $0
Owed $40 to Alice

$ logout
Goodbye, Carol!

$ login Dave
Hello, Dave!
Your balance is $0

$ deposit 0.10
Your balance is $0.10

$ deposit 0.10
Your balance is $0.20

$ deposit 0.10
Your balance is $0.30

$ deposit 0.10
Your balance is $0.40

$ deposit 0.10
Your balance is $0.50

$ deposit 0.10
Your balance is $0.60

$ deposit 0.10
Your balance is $0.70

$ deposit 0.10
Your balance is $0.80

$ deposit 0.10
Your balance is $0.90

$ deposit 0.10
Your balance is $1

$ withdraw 0.55
Your balance is $0.45

$ logout
Goodbye, Dave!

$ exit
```
