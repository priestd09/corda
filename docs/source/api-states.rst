States
======

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-states`.

ContractState
-------------

All Corda states are JVM classes that directly or indirectly implement ``ContractState``.

The ``ContractState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

Where:

* ``contract`` is the ``Contract`` class defining the constraints on the creation and consumption of states of this type
* ``participants`` is a ``List`` of the ``PublicKey`` of each party involved in this state

ContractState child interfaces
------------------------------
To be tracked by the node's vault, a state must implement one of:

* ``LinearState``, which represents facts that evolve over time
* ``OwnableState``, which represents fungible assets

We can picture this as follows:

.. image:: resources/state-hierarchy.png

Both interfaces provide a way for the node's vault to ascertain whether the state is relevant to it (and therefore worth
tracking) or not.

LinearState
^^^^^^^^^^^
``LinearState`` models facts that evolve over time. Remember that in Corda, states are immutable and can't be
updated directly. Instead, we represent an evolving fact as a series of states, where each state is a
``LinearState`` and has the same ``linearId``. This allows us to link together the changes to the fact over time.

The ``LinearState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2

Where:

* ``linearId`` is a ``UniqueIdentifier`` that:
  * Allows the various versions of the fact to be linked over time
  * Allows the state to be referenced in external systems
* ``isRelevant(ourKeys: Set<PublicKey>)`` checks whether, given a set of keys, this state is relevant and should be
  tracked by our vault

The vault tracks the head (i.e. the most recent evolution) of each ``LinearState`` chain (i.e. a sequence of
states that share a ``linearId``). To update a ``LinearState``, it is first retrieved from the vault using its
``linearId``.

OwnableState
^^^^^^^^^^^^
``OwnableState`` models fungible assets - assets for which it's the quantity held that is important, rather than
the identity of the individual units.

The ``OwnableState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3

Where:

* ``owner`` is the ``PublicKey`` of the asset's owner
  * By default, the node's vault tracks states of which it is the owner
* ``withNewOwner(newOwner: PublicKey)`` creates an identical copy of the state, only with a new owner

Other interfaces
^^^^^^^^^^^^^^^^
``ContractState`` has several more child interfaces that can be implemented:

* ``QueryableState``, which allows the state to be queried in the node's database using SQL (see
  :doc:`event-scheduling`)
* ``SchedulableState``, which allows us to schedule future actions for the state (e.g. a coupon on a bond) (see
  :doc:`persistence`)

User-defined fields
-------------------
Beyond implementing ``LinearState`` or ``OwnableState``, the definition of the state is up to the CorDapp developer.
You can define any additional class fields and methods you see fit.

For example, here is a relatively complex state definition, for a state representing cash:

.. container:: codeset

    .. literalinclude:: ../../finance/src/main/kotlin/net/corda/contracts/asset/Cash.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

TransactionState
----------------
Before a state is stored on the ledger, it must be wrapped in a ``TransactionState``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 4
        :end-before: DOCEND 4

Where:

* ``data`` is the state to be stored on-ledger
* ``notary`` is the notary service for this state
* ``encumbrance`` points to another state that must also appear as an input in any transaction consuming this
  state