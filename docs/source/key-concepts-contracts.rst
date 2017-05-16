Contracts
=========

Recall that a transaction is only valid if it is digitally signed by all required parties. However, even if a
transaction gathers all the required signatures, it is only valid if it is also **contractually valid**.

**Contract validity** is defined as follows:

* Each state points to a *contract*
* Each contract defines a function that takes a transaction as input, and states whether the transaction is
  considered valid according to its contract rules
* A transaction is only valid if the contract of **every input state** and **every output state** considers it to be
  valid

A transaction that is not contractually valid will not become accepted as part of the ledger. In this way,
contracts impose rules on the evolution of states over time that are independent of the willingness of individual
nodes to sign a given transaction.

Contracts are *stateless* and *deterministic* - they do not have any storage, and will either always accept or
always reject a given transaction.

Each contract also refers to a legal prose document that states the rules for contract validity in a way that is
compatible with traditional legal systems. This document can be relied upon in the case of legal disputes.

Oracles
-------
We can imagine that in some cases, contractual validity may depend on some external piece of data, such as an
exchange rate. However, contract execution must be deterministic. If the contract gave a different view on the
contract's validity based on the time of execution or the information source used, disagreements would arise
regarding the true state of the ledger.

Corda addresses this using *oracles*. Oracles are network services that, upon request, provide commands encapsulating a
specific fact (e.g. the exchange rate at time x). The oracle is listed as a required signer on the command they return.

If a node then wishes to use this fact in their transaction, they include it by way of the command provided by the
oracle, who will then be required to sign the transaction to assert that the fact is true. If they wish to monetize
their services, oracles may decide to only sign a transaction including a fact that they are attesting to for a fee.

Transaction tear-offs are used to prevent the oracle from seeing unwanted information about the transaction. See
:doc:`merkle-trees` for further detail.