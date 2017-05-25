Tradeoffs
=========

.. topic:: Summary

   * *Permissioned networks are better suited to financial scenarios*
   * *Point-to-point communication maintains privacy*
   * *A UTXO model allows for higher network throughput*

Permissioned vs. permissionless
-------------------------------
Traditional blockchain networks are permissionless - parties on the network are anonymous, and can join and leave at
will.

By contrast, Corda networks are permissioned - each party on the network has a known identity that they use when
communicating with counterparties. This has several benefits:

* Anonymous parties are inappropriate for most scenarios involving regulated financial institutions
* Knowing the identity of your counterparties allows for off-ledger resolution of conflicts using existing
  legal systems (see below)
* Sybil attacks are averted without the use of expensive mechanisms such as proof-of-work

Point-to-point vs. global broadcasts
------------------------------------
Traditional blockchain networks broadcast every message to every participant. The reason for this is two-fold:

* Counterparty identities are not known, so a message must be sent to every participant to ensure it reaches its
  recipient
* Double-spends are prevented by making every participant aware of every transaction

As a result, every transaction conducted on the network is known to all participants. This is unacceptable for many
use-cases.

In Corda, every message is sent directly a specific counterparty, and is not seen by any third parties. The developer
has full control over what messages are sent, to whom, and in what order. As a result, **data is shared on a
need-to-know basis only**. This is feasible because double-spends are prevented by notaries, rather than by
proof-of-work.

Several other techniques are also used to maximize privacy on the network:

* **Transaction tear-offs**: Transactions are structured such that they can be digitally signed without disclosing the
  entirety of the transaction's contents, using a data structure called a Merkle tree. You can read more about this
  technique in :doc:`merkle-trees`.
* **Key randomisation**: The parties to a transaction are identified only by their public keys, and fresh keypairs are
  generated for each transaction. As a result, an onlooker cannot identify which parties were involved in a given
  transaction.

UTXO vs. account model
----------------------
Corda uses a *UTXO* (unspent transaction output) model. Each transaction consumes an existing set of unconsumed states
to produce a new set of states.

The alternative is an *account* model, where transactions update the status of an existing object.

The main downside of the account model is that transactions cannot be applied in parallel. Each transaction has to
wait for all the previous transactions to update the object before it can update it itself. This imposes a cap on the
speed of ledger updates. In a UTXO model, on the other hand, any transactions that are not consuming the same input(s)
can be processed in parallel.

Code-is-law vs. existing legal systems
--------------------------------------
A key requirement for financial institutions is the ability to rely on courts of law in the case of conflicts. Corda
is built from the ground up with this requirement in mind. In particular:

* Corda networks are permissioned, so participants are aware of who they are dealing with in every single transaction
* All code contracts are backed by a legal document describing the contract's intended behavior

Build vs. re-use
----------------
Wherever possible, Corda re-uses tried and tested technologies instead of reinventing the wheel, leading to a more
robust platform overall. Existing technologies re-used by Corda include:

* Standard JVM programming languages for the development of CorDapps
* SQL databases
* Messaging queues