Contracts
=========

Contracts define part of the business logic of the ledger.

Corda enforces business logic through smart contract code, which is constructed as a pure function (called "verify") that either accepts
or rejects a transaction, and which can be composed from simpler, reusable functions. The functions interpret transactions
as taking states as inputs and producing output states through the application of (smart contract) commands, and accept
the transaction if the proposed actions are valid. Given the same transaction, a contract’s “verify” function always yields
exactly the same result. Contracts do not have storage or the ability to interact with anything.

.. note:: In the future, contracts will be mobile. Nodes will download and run contracts inside a sandbox without any review in some deployments,
although we envisage the use of signed code for Corda deployments in the regulated sphere. Corda will use an augmented
          JVM custom sandbox that is radically more restrictive than the ordinary JVM sandbox, and it will enforce not only
          security requirements but also deterministic execution.

To further aid writing contracts we introduce the concept of :doc:`clauses` which provide a means of re-using common
verification logic.