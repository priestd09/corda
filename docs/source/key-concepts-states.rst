States
======

A *state* is an immutable object representing a fact known by one or more Corda nodes at a specific point in time.
States can contain arbitrary data, allowing them to represent facts of any kind (e.g. stocks, bonds, loans, KYC data,
 identity information...).

For example, the following state represents an IOU:

.. image:: resources/state.png

Specifically, this state represents an IOU of £10 from Alice to Bob.

As well as any information about the fact itself, the state also contains a reference to the **contract** that governs
the evolution of the shared fact over time. We discuss contracts in :doc:`key-concepts-contracts`.

State sequences
---------------
Since states are immutable, their attributes cannot be modified directly.

Instead, the lifecycle of a shared fact over time is represented by a **state sequence**. When a shared fact needs to
be updated, we create a new version of the state representing the current situation, and mark the existing state as
historic.

By linking these updates together in chronological order, we get a full view of the evolution of the shared fact over
time. We can picture this as follows:

.. image:: resources/state-sequence.png

The vault
---------
Each node on the network maintains a *vault* - a database where the node tracks all the current and historic states
that it is aware of, and which it considers to be relevant:

.. image:: resources/vault-simple.png

We can think of the ledger from each node's point-of-view as the set of all the current (i.e. non-historic) states that
they are aware of.