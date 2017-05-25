Contracts
=========

.. topic:: Summary

   * *A transaction is verified by running the contract of every input and output state*
   * *Contracts are written in a Turing-complete programming language*
   * *Contracts are deterministic and their verification is based on the transaction contents alone*

Transaction verification
------------------------
Recall that a transaction is only valid if it is digitally signed by all required parties. However, even if a
transaction gathers all the required signatures, it is only valid if it is also **contractually valid**.

**Contract validity** is defined as follows:

* Each state points to a *contract*
* A *contract* is an object that takes a transaction as input, and states whether the transaction is considered valid
  according to a set of developer-defined rules
* A transaction is only valid if the contract of **every input state** and **every output state** considers it to be
  valid

We can picture this situation as follows:

.. image:: resources/tx-validation.png

The contract code is written in a standard programming language, and allows the following:

* Checks on the number of inputs, outputs, commands, timestamps, and/or attachments
* Checks on the contents of any of these components
* Looping constructs, variable assignment, function calls, helper methods, etc.
* Grouping similar states to validate them as a group (e.g. imposing a rule on the combined value of the cash states)

A transaction that is not contractually valid is not a valid ledger update proposal. In this way, contracts impose
rules on the evolution of states over time that are independent of the willingness of individual nodes to sign a
given transaction.

The contract sandbox
--------------------
Transaction verification must be *deterministic* - a contract should either **always accept** or **always reject** a
given transaction. For example, transaction validity cannot depend on the time at which validation is conducted, or
the amount of information the peer running the contract holds. This is a necessary condition to ensure that all peers
on the network share the same view regarding which ledger updates are valid.

To achieve this, contracts evaluate transactions in a deterministic sandbox, with no access to external resources such
as the current time, random numbers, the filesystem or the internet. The only information available to the contract
is the information included in the transaction.

Oracles
-------
Sometimes, transaction validity will depend on some outside piece of information, such as an exchange rate). In
these cases, an oracle is required. See :doc:`key-concepts-oracles` for further details.

Limitations of contracts
------------------------
Since a contract has no access to information from the outside world, it can only check the transaction for internal
validity. It cannot check, for example, that the transaction is in accordance with what was agreed with any
counterparties.

Peers should therefore check the contents of a transaction before signing it, *even if the transaction is
contractually valid*, to see whether they are happy for the desired ledger update to take place. A peer is under no
obligation to sign a transaction just because it is contractually valid.

Legal prose
-----------
Each contract also refers to a legal prose document that states the rules for contract validity in a way that is
compatible with traditional legal systems. This document can be relied upon in the case of legal disputes.