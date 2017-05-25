Transactions
============

.. topic:: Summary

   * *Transactions are proposals to update the ledger*
   * *A transaction proposal will only be committed if:*
     * *It doesn't contain double-spends*
     * *It is contractually valid*
     * *It is signed by the required parties*

*Transactions* are objects that update the ledger by consuming zero or more existing ledger states (the
*inputs*) and producing zero or more new ledger states (the *outputs*). They represent a single link in the state
sequences of the previous section.

An example of an update transaction, with two inputs and two outputs:

.. image:: resources/basic-tx.png

There are no constraints on the number or type of inputs and outputs you can include in a transaction:

* Transactions can include *n* different state types (e.g. both cash and bonds)
* Transactions can be issuances (have zero inputs) or exits (have zero outputs)
* Transactions can merge or split fungible assets (e.g. combining a $2 state and a $5 state into a $7 cash state)

Transactions are *atomic* - either all the transaction's proposed changes are accepted, or none are. There is no
situation in which only some of the changes proposed by a given transaction are accepted.

There are two basic types of transactions:

* Notary-change transactions (used to change a state's notary - see :doc:`key-concepts-notaries`)
* General transactions (used for everything else)

Transaction chains
------------------
When creating a transaction, the proposed output states do not exist yet, and more therefore be created by the
proposer(s) of the transaction. However, the input states already exist as the outputs of previous transactions.
They are included in the proposed transaction by referencing them in the transaction that created them.

These input states references are a combination of:

* The hash of the transaction that created the input
* The input's index in the outputs of the previous transaction

This situation can be illustrated as follows:

.. image:: resources/tx-chain.png

It is this series of input state references that link together transactions over time into what is known as a
*transaction chain*.

Committing transactions
-----------------------
Initially, a transaction is just a **proposal** to update the ledger. It represents the future state of the ledger
that is desired by the transaction builder(s):

.. image:: resources/uncommitted_tx.png

To become reality, the transaction must satisfy the following conditions:

   * **Transaction validity**: For both the proposed transaction, and every single past transaction in the chain of
     transactions that led up to the creation of the current proposed transaction's inputs:
       * The transaction is digitally signed by all the required parties
       * The transaction is *contractually valid* (we'll examine this condition in the next section on
         :doc:`key-concepts-contracts`)
   * **Transaction uniqueness**: There exists no other committed transaction that consumes any of the same inputs as
     our proposed transaction (we'll examine this condition in the section on :doc:`key-concepts-consensus`)

Additionally, the transaction must receive signatures from all of the *required signers* (see **Commands**, below). Each
required signer appends their signature to the transaction to indicate that they approve the proposal:

.. image:: resources/tx_with_sigs.png

If all of these conditions are met, the transaction becomes committed:

.. image:: resources/committed_tx.png

This means that:

* The transaction's inputs are marked as historic, and cannot be used in any future transactions
* The transaction's outputs become part of the current state of the ledger

Other transaction components
----------------------------
As well as input states and output states, transactions may contain:

* Commands
* Attachments
* Timestamps

For example, a transaction where Alice settles £5 of an IOU with Bob in exchange for a £5 cash payment from Alice to
Bob, supported by two attachments and a timestamp, may look as follows:

.. image:: resources/full-tx.png

We explore the role played by the remaining transaction components below.

Commands
^^^^^^^^
Suppose we have a transaction with a cash state and a bond state as inputs, and a cash state and a bond state as
outputs. This transaction could represent several scenarios:

* A bond purchase
* A coupon payment on a bond

Clearly, the rules for contractual validity are different in these scenarios. For example, in the former, we would
require a change in the bond's current owner; in the latter, the bond would not change ownership.

For this, we use *commands* to allow us to indicate the intent of a transaction, affecting how contractual validity is
checked.

Each command is also associated with a list of one or more *signers*. A transaction's required signers is the union of
all the public keys listed in the commands. In our example, we might imagine that:

* In a bond purchase, the owner of the cash and the owner of the bond are required to sign
* In a coupon payment on a bond, only the payer of the coupon is required to sign

Attachments
^^^^^^^^^^^
Sometimes, there's a larger piece of data that can be reused across many different transactions. Some examples:

* A calendar of public holidays
* Supporting legal documentation
* A table of currency codes

For this use case, we have *attachments*. Every transaction can refer to zero or more attachments by hash. These
attachments are always ZIP/JAR files, and can contain arbitrary content. The information in these files can then be
used when checking the transaction for contractual validity.

Timestamps
^^^^^^^^^^
In some cases, a transaction will only valid at a certain point in time. For example:

* An option can only be exercised after a certain date
* A bond may only be redeemed before its expiry date

In such cases, we can add a *timestamp* to the transaction. Timestamps specify the time window during which the
transaction can be committed. We discuss timestamps in the section on :doc:`key-concepts-notaries`.