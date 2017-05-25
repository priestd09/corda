The network
===========

.. topic:: Summary

   * *A Corda network is a network of nodes running Corda and CorDapps*
   * *Communication is point-to-point, instead of a global broadcast*
   * *The network is permissioned, with access controlled by a doorman*
   * *Nodes are identified on the network by their legal identity*

Network structure
-----------------
A Corda network is an authenticated peer-to-peer network of nodes, where each node is a JVM run-time environment
hosting Corda services and executing applications known as *CorDapps*. Each node stores its data in a local relational
database that the node's owner can query using SQL.

All communication between nodes is direct. This allows all data to be shared on a need-to-know basis only; in Corda,
there is **no global broadcast**. Messages are encrypted and sent via message queues (using AMQP/1.0 over TLS).

The doorman
-----------
Corda networks are semi-private. Each network has a doorman that enforces rules regarding the information
that nodes must provide and the know-your-customer process that they must complete before being able to join the
network.

When a node wishes to join the network, they contact the doorman and provide the required information. If the
doorman is satisfied, the node will receive a root-authority-signed TLS certificate from the network's permissioning
service. This certificate certifies the node's identity to other network participants.

We can visualize a network as follows:

.. image:: resources/network.png

Network services
----------------
Nodes can provide several types of services:

* A **network map service** that publishes the IP addresses through which every node on the network can be reached,
  along with the identity certificates of those nodes and the services they provide.
* One or more pluggable **notary services**. Notaries guarantee the uniqueness, and possibility the validity, of ledger
  updates. A notary service may be run on a single node, or across a cluster of nodes.
* Zero or more **oracle services**. An oracle is a well-known service that signs transactions if they state a fact and
  that fact is considered to be true.

These components are illustrated in the following diagram:

.. image:: resources/cordaNetwork.png
    :align: center

Where Corda infrastructure services are those which all participants depend upon, such as the network map
and notaries. Corda services may be deployed by participants, third parties or a central network operator (e.g. R3).
This diagram is not intended to imply that only a centralised model is supported.

