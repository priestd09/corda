.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Flows
=====

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-flows`.

FlowLogic
---------
A flow is implemented in code as one or more ``FlowLogic`` subclasses that communicate to handle a specific business
process. Each ``FlowLogic`` subclass must override ``FlowLogic.call()``, which describes the actions it will
take as part of the flow.

ServiceHub
----------

Within ``FlowLogic.call()``, the flow developer has access to the node's ``ServiceHub`` that provides access to the
various services the node provides.

The key ``ServiceHub`` services are:

* ``ServiceHub.networkMapCache``
    * Provides info on other nodes on the network (e.g. notaries…)
* ``ServiceHub.vaultService``
    * Stores the node’s current and historic states
* ``ServiceHub.storageService``
    * Stores additional info such as transactions and attachments
* ``ServiceHub.keyManagementService``
    * Manages the node’s digital signing keys
* ``ServiceHub.myInfo``
    * Other information about the node
* ``ServiceHub.clock``
    * Provides access to the node’s internal time and date

Some common tasks performed using the ``ServiceHub`` are:

* Looking up your own identity or the identity of a counterparty using the ``networkMapCache``
* Identifying the providers of a given service (e.g. a notary service) using the ``networkMapCache``
* Retrieving states to use in a transaction using the ``vaultService``
* Retrieving attachments and past transactions to use in a transaction using the ``storageService``
* Creating a timestamp using the ``clock``
* Signing a transaction using the ``keyManagementService``

Communication between flows
---------------------------
``FlowLogic`` instances communicate using three functions:

* ``FlowLogic.send(otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``
* ``FlowLogic.receive(receiveType: Class<R>, otherParty: Party)``
    * Receives an object of type ``receiveType`` from the ``otherParty``
* ``FlowLogic.sendAndReceive(receiveType: Class<R>, otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``, and receives an object of type ``receiveType`` back

Each ``FlowLogic`` subclass can be annotated to respond to messages from a given *counterparty* flow. When a node
first receives a message from a given ``FlowLogic.call()`` invocation, it responds as follows:

* The node checks whether they have a ``FlowLogic`` subclass that is registered to respond to the ``FlowLogic`` that
is sending the message:

    a. If yes, the node starts an instance of this ``FlowLogic`` by invoking ``FlowLogic.call()``
    b. Otherwise, the node ignores the message
* The counterparty steps through their ``FlowLogic.call()`` method until they encounter a call to ``receive()``, at
  which point they process the message from the initiator

Upon calling ``receive()``/``sendAndReceive()``, the ``FlowLogic`` is paused. The node will then process the
logic of other existing ``FlowLogic`` instances until a response is received.

UntrustworthyData
-----------------

``send()`` and ``sendAndReceive()`` return a payload wrapped in an ``UntrustworthyData`` instance. This is a
reminder that any data received off the wire is untrustworthy and must be verified.

We verify the ``UntrustworthyData`` and retrieve its payload using a lambda:

.. container:: codeset

   .. sourcecode:: kotlin

        val partSignedTx = receive<SignedTransaction>(otherParty).unwrap { partSignedTx ->
                val wireTx = partSignedTx.verifySignatures(keyPair.public, notaryPubKey)
                wireTx.toLedgerTransaction(serviceHub).verify()
                partSignedTx
            }

   .. sourcecode:: java

        final SignedTransaction partSignedTx = receive(SignedTransaction.class, otherParty)
            .unwrap(tx -> {
                try {
                    final WireTransaction wireTx = tx.verifySignatures(keyPair.getPublic(), notaryPubKey);
                    wireTx.toLedgerTransaction(getServiceHub()).verify();
                } catch (SignatureException ex) {
                    throw new FlowException(tx.getId() + " failed signature checks", ex);
                }
                return tx;
            });

Subflows
--------
Corda provides a number of built-in flows for handling common tasks. The most important are:

* ``CollectSignaturesFlow``, to collect a transaction's required signatures
* ``FinalityFlow``, to notarise and record a transaction
* ``ResolveTransactionsFlow``, to verify the chain of inputs to a transaction
* ``ContractUpgradeFlow``, to change a state's contract
* ``NotaryChangeFlow``, to change a state's notary

These flows are designed to be used as building blocks in your own flows. You do so by calling ``FlowLogic.subFlow()``
from within ``FlowLogic.call()``. Here is an example from ``TwoPartyDealFlow.kt``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/flows/TwoPartyDealFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 12

In this example, we are starting a ``CollectSignaturesFlow``, passing in a partially signed transaction, and
receiving back a fully-signed version of the same transaction.

FlowException
-------------
If a node throws an exception while running a flow, any counterparties waiting for a message from the node (i.e. as part
of a call to ``receive()`` or ``sendAndReceive()``) will not be notified.

You can notify any waiting counterparties that you have encountered an exception and are having to end the
flow by throwing a ``FlowException``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/flows/FlowException.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

The flow framework will automatically propagate the ``FlowException`` back to the waiting counterparties.

There are many scenarios in which throwing a ``FlowException`` would be appropriate:

* A transaction doesn't ``verify()``
* A transaction's signatures are invalid
* The transaction does not match the parameters of the deal as discussed
* You are reneging on a deal

Suspending flows
----------------
In order for nodes to be able to run multiple flows concurrently, and to allow flows to survive node upgrades and
restarts, flows need to be checkpointable and serializable to disk.

This is achieved by marking any function invoked from within ``FlowLogic.call()`` with an ``@Suspendable`` annotation.

We can see an example in ``CollectSignaturesFlow``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/flows/CollectSignaturesFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1