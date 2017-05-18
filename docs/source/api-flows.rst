.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Flows
=====

FlowLogic
---------
The concept of a flow is implemented in code as a set of implementations of the abstract ``FlowLogic`` class. Each
party to the flow will run a flowlogic, and these flowlogics will communicate to handle a specific business process.

Each ``FlowLogic`` implementation must override the ``FlowLogic.call()`` method to describe the actions it will
take to achieve its role in the flow.

ServiceHub
----------

Each flowlogic has access to the node's ``ServiceHub``, giving the CorDapp developer access to the various services
the node provides.

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

* Looking up your own identity or the identity of counterparties using the ``networkMapCache``
* Identifying the provides of a given service (e.g. a notary service) using the ``networkMapCache``
* Retrieving states for building a transaction using the ``vaultService``
* Retrieving attachments and past transactions for building a transaction using the ``storageService``
* Creating a timestamp using the ``clock``
* Signing a transaction using the ``keyManagementService``

Communication between flowlogics
--------------------------------
Flowlogics communicate with counterparties using three functions:

* ``FlowLogic.send(otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``
* ``FlowLogic.receive(receiveType: Class<R>, otherParty: Party)``
    * Receives an object of type ``receiveType`` from the ``otherParty``
* ``FlowLogic.sendAndReceive(receiveType: Class<R>, otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``, and receives an object of type ``receiveType`` back

Communication between an initiator node and a counterparty node is established when the initiator's
``FlowLogic`` first calls ``send()``/``sendAndReceive()``:

* A message is sent to the specified counterparty
* The counterparty examines which flowlogics they have registered to respond to:

    a. If the counterparty has registered a flowlogic to respond to the flowlogic sending the
       message, it starts this flowlogic
    b. Otherwise, the counterparty will ignore the message
* The counterparty steps through their flowlogic until they encounter a call to ``receive()``, at which point
  they process the message from the initiator

A flowlogic is paused upon calling ``receive()``/``sendAndReceive()``. The node will then process other flowlogics
until a response is received.

UntrustworthyData
-----------------

``send()`` and ``sendAndReceive()`` return the payload wrapped in an ``UntrustworthyData`` instance. This is a
reminder that any data received off the wire is untrustworthy and must be verified.

We retrieve the payload from the ``UntrustworthyData`` instance using a lambda:

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

These flows are designed to be used as building blocks in your own flows. You do so by making a call to ``FlowLogic
.subFlow()`` from within ``FlowLogic.call()``. Here is an example from ``TwoPartyDealFlow.kt``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/flows/TwoPartyDealFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 12

In this example, we are invoking ``CollectSignaturesFlow`` and passing it a partially signed transaction. This
returns a fully-signed version of the same transaction.

FlowException
-------------
If a node throws an exception while running a flow, counterparties waiting for a message from the node (i.e. as part
of a ``receive()`` or ``sendAndReceive()`` call) will not be notified.

In some cases, this is desirable behavior. However, if you want to notify any waiting counterparties that you are
ending the flow, you should throw a ``FlowException``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/flows/FlowException.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

The flow framework will automatically propagate the ``FlowException`` back to the waiting counterparties.

You can imagine many scenarios in which throwing a ``FlowException`` would be appropriate:

* A transaction doesn't ``verify()``
* A transaction's signatures are invalid
* The transaction does not match the parameters of the deal as discussed
* You are reneging on a deal

Suspending flows
----------------
In order for nodes to be able to run multiple flow concurrently, and to allow flows to survive node upgrades and
restarts, flows need to be suspendable.

This is achieved by marking any function invoked from within a flowlogic's ``call()`` method with an
``@Suspendable`` annotation.

We can see an example in ``CollectSignaturesFlow``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/flows/CollectSignaturesFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1