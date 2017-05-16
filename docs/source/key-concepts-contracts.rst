Contracts
=========

Recall that a transaction is only valid if it is digitally signed by all required parties. However, even if a
transaction gathers all the required signatures, it is only valid if it is also **contractually valid**.

**Contract validity** is defined as follows:

* Each state points to a *contract*
* Each contract defines a function that takes a transaction as input, and states whether the transaction is
considered valid according to its contract rules
* A transaction is only valid if the contract of every input state and every output state considers it to be valid

A transaction that is not **contractually valid** will not become accepted as part of the ledger. In this way,
contracts impose rules on the evolution of states over time, independent of what the individual nodes are willing to
sign.

Contracts are *stateless* and *deterministic* - they do not have any storage, and will either always accept or
always reject a given transaction.

.. note:: In the future, contracts will be mobile. Nodes will download and run contracts inside a sandbox without any
 review in some deployments, although we envisage the use of signed code for Corda deployments in the regulated
 sphere. Corda will use an augmented JVM custom sandbox that is radically more restrictive than the ordinary JVM
 sandbox, and it will enforce not only security requirements but also deterministic execution.

Each contract also refers to a legal prose document that states the rules for contract validity in a way that is
compatible with traditional legal systems. This document can be relied upon in the case of legal disputes.