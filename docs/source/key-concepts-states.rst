States
======

A state represents a fact known by one or more Corda nodes at a fixed point in time. States can contain arbitrary data,
allowing them to represent facts of any kind (e.g. stocks, bonds, loans, KYC data, identity information...).

For example, the following diagram represents a cash agreement:

.. image:: resources/contract.png

Specifically, this state represents a cash claim of £100 by a shipping company against a commercial bank.

As well as any information about the fact itself, the state also contains a reference to the **contract** that governs
the evolution of the shared fact over time. Contracts will be discussed in-depth later.

State sequences
---------------

In Corda, states are immutable: their attributes cannot be modified directly.

Instead, the lifecycle of a shared fact over time is represented by a **state sequence**. When a shared fact needs to
be updated, we create a new version of the state representing the current situation, and mark the existing state as
historic.

By linking these updates together in chronological order, we get a full view of the evolution of the shared fact over
time.

The vault
---------

Each node on the network maintains a **vault** where they track all the current and historic states that they
are aware of, and which are relevant to them.

We can think of the ledger from each node's point-of-view as the set of all the current (i.e. non-historic) states that
they are aware of.