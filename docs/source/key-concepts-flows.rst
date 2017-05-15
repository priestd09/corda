Flows
=====

A flow is a sequence of steps that tells a node how to handle a specific business process, such as issuing an asset or
settling a trade. Once a given business process has been encapsulated in a flow and installed on the node as part of a
CorDapp, the node's owner can instruct the node to conduct this business process at any time with a single
message. All activity on a node, and all inter-node communication, happens in the context of these flows.

Flows provide functionality to send and receive data to and from other identities on the network, report progress
information to observers, and even request human interaction (e.g. for manual resolution of exceptional scenarios).

A node can have millions of flows active at once and they may last days, across node restarts and even upgrades.

.. note:: In the future, if a flow encounters an error or requires human assistance, it will pause and be sent to the
          flow hospital. At this point, the node's owner will be able to choose to repair or kill the paused flow.

Section 4 of the `Technical white paper`_ provides further detail on the above features

The flow library
----------------

A library of common flows is provided, meaning that developers do not have to redefine common processes such as:

* Notarising and recording a transaction
* Gathering signatures from counterparty nodes
* Verifying a chain of transactions

Further information on the available built-in flows can be found here: :doc:`flow-library`.

An example flow
---------------

The following diagram illustrates a sample multi-party business flow:

.. image:: resources/flowFramework.png

This flow has 3 participants:

* The buyer
* The seller
* The notary

The BuyerFlow and SellerFlow (depicted in green) are written by developers and deployed as part of a CorDapp. The other
flows - the ``TwoPartyTradeFlow`` (depicted in orange) and the ``ResolveTransactionsFlow`` and ``NotaryFlow`` (depicted
in yellow) are part of the core Corda platform.

Within the flow, we can see that the node is able to interact with its own storage (e.g. to record a signed, notarised
transaction) and vault (eg. to perform a spend of some fungible asset). The node is also able to interact with its
peers by sending and receiving messages.

The white labels in each flow are progress tracker steps that report flow progress back to the node's owner.

.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf