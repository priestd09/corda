Transactions
============

Transactions are used to update the ledger by consuming zero or more existing ledger states (the *inputs*) and
producing zero or more new ledger states (the *outputs*). They represent a single link in the state sequences of the
previous section.

Issuances (transactions with zero inputs) and exits (transactions with zero outputs) are not special transaction
types in Corda, and can be proposed by any node. Similarly, transactions can use any combination of input and output
state types (e.g. a transaction involving both cash states and bond states).

Transactions are *atomic* - either all the transaction's proposed changes are accepted, or none are. There is no
situation in which only some of the changes proposed by a given transaction are accepted.

Committing transactions
-----------------------
Initially, a transaction is just a **proposal** to update the ledger. It represents the future state of the ledger
that is desired by the transaction builder(s).

For the changes proposed by the transaction to become fact, the transaction must be committed. A transaction is
committed if it satisfies the following two conditions:

   * **Transaction validity**: For both the proposed transaction, and every single past transaction in the chain of
     transactions that led up to the creation of the current proposed transaction's inputs:
       * The transaction is digitally signed by all the required parties
       * The transaction is *contractually valid* (we'll examine this condition in the next section on
         :doc:`key-concepts-contracts`)
   * **Transaction uniqueness**: There exists no other committed transaction that consumes any of the same inputs as
     our proposed transaction (we'll examine this condition in the section on :doc:`key-concepts-consensus`)

If both of these conditions are satisfied, the transaction becomes committed, meaning that:

* The transaction's inputs are marked as historic, and cannot be used in any future transactions
* The transaction's outputs become part of the current state of the ledger

Other transaction components
----------------------------
As well as input states and output states, transactions may contain:

* Commands
* Attachments
* Timestamps

Commands
^^^^^^^^
Suppose we have a transaction with a cash state and a bond state as inputs, and a cash state and a bond state as
outputs. This transaction could represent several scenarios:

* A bond purchase
* A coupon payment on a bond

Clearly, the rules for contractual validity are different in these scenarios. For example, in the former, we would
require a change in the bond's current owner; in the latter, the bond would not change ownership.

*Commands* allow us to indicate the intent of a transaction, affecting how contractual validity is checked.

Each command is also associated with a list of one or more *signers*. To be valid, a transaction must be signed by
every party listed in the transaction's commands. Returning to our earlier example, we might imagine that:

* In a bond purchase, the owner of the cash and the owner of the bond are required to sign
* In a coupon payment on a bond, only the payer of the coupon is required to sign

Attachments
^^^^^^^^^^^
Sometimes, there's a larger piece of data that can be reused across many different transactions (e.g. a calendar of
public holidays). For this use case, we have *attachments*. Every transaction can refer to zero or more attachments
by hash.

Attachments are always ZIP/JAR files, which may contain arbitrary content. The information in these files can then be
used when checking the transaction for contractual validity.

Timestamps
^^^^^^^^^^
A *timestamp* specifies the time window within which the transaction can be committed. We discuss timestamps in the
section on :doc:`key-concepts-consensus`

Privacy maximization
--------------------
Corda aims to maximize privacy. To do so, it implements various techniques to minimize the amount of data seen during
transaction validation:

* **Controlled data distribution**: Transactions are **only broadcast** to the relevant parties, and third-parties do not
  perform transaction validation
* **Transaction tear-offs**: Transactions are structured such that they can be digitally signed without disclosing the
  entirety of the transaction's contents, using something called Merkle trees. You can read more about this technique
  in :doc:`merkle-trees`.
* **Key randomisation**: The parties to a transaction are identified only by their public keys, and fresh keypairs are
  generated for each transaction. As a result, an onlooker cannot identify which parties were involved in a given
  transaction.