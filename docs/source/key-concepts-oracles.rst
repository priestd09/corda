Oracles
=======

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
:doc:`merkle-trees` for further information.