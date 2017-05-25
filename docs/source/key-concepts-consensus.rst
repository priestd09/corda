Consensus
=========

.. topic:: Summary

   * *Proposed transactions must achieve validity and uniqueness consensus*
   * *Validity consensus requires contract acceptance of the transaction and all its dependencies*
   * *Uniqueness consensus prevents double-spends*

Two types of consensus
----------------------
Determining whether a proposed transaction is a valid ledger update involves reaching two types of consensus:

* *Validity consensus*
* *Uniqueness consensus*

The former should be checked by each required signer before signing a transaction. The latter will be checked by a
notary service.

Validity consensus
------------------
Validity consensus is the process of checking that for a proposed transaction, along with every transaction in the
transaction chains that generated the inputs to this proposed transaction, the following conditions hold:

* The transaction is accepted by the contracts of every input and output state
* The transaction has all the required signatures

It is not enough to just verify the proposed transaction itself - we must verify the transaction chains that
generated its inputs as well. This is known as *walking the chain*. Suppose, for example, that a party on the network
creates a transaction offering to exchange central-bank-issued cash for a bond. We can only be sure that the cash
represents a valid claim on the central bank if:

* The cash was issued by the central bank in a valid transaction
* Every subsequent transaction in which the cash changed hands was also valid

The only way to be sure of both conditions is to validate the transaction's chains, as well as the transaction itself.
We can visualize this process as follows:

.. image:: resources/validation-consensus.png

When verifying a proposed transaction, a given party may not have every transaction in the transaction chains that they
need to verify. In this case, they can request the transactions they are missing from the transaction proposer(s).

Uniqueness consensus
--------------------
Imagine that Bob holds a valid central-bank-issued cash state of $1,000,000. Bob can now create two transaction
proposals:

* A transaction transferring the $1,000,000 to Charlie in exchange for £800,000
* A transaction transferring the $1,000,000 to Dan in exchange for €900,000

This is a problem because, although both transactions will achieve validity consensus, Bob has managed to
"double-spend" his USD to get double the amount of GBP and EUR. We can visualize this as follows:

.. image:: resources/uniqueness-consensus.png

To prevent this, a valid transaction proposal must also achieve uniqueness consensus. Uniqueness consensus is
requirement that none of the inputs to a proposed transaction have been consumed in a previous transaction.

If one or more of the inputs have already been consumed in a previous transaction, this is known as a *double spend*,
and the transaction proposal is considered invalid.