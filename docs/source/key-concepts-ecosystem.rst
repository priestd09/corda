Corda networks
==============

Network structure
-----------------

A Corda network is made up of nodes. Each node is a JVM run-time environment hosting Corda services and
executing applications (*CorDapps*). Each node stores its data in a local relational database that the node's owner
can query using SQL.

The nodes are arranged in an authenticated peer-to-peer network. All communication between nodes is direct.
This allows all data to be shared on a need-to-know basis only; in Corda, there is **no global broadcast**.
Messages are encrypted and sent via message queues (using AMQP/1.0 over TLS).

Corda networks are semi-private. Before joining, nodes must be provisioned with root-authority-signed TLS certificates
by a permissioning service to certify their identity.

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

CorDapps
--------

Each node can be customized by installing CorDapps: applications that extend the node to allow it to handle new
business processes. Examples of CorDapps include asset trading (see :ref:`irs-demo` and :ref:`trader-demo`),
portfolio valuations (see :ref:`simm-demo`), trade finance, post-trade order matching, KYC/AML, etc.

CorDapps are made up of state, contract and flow definitions (which will be the focus of the following sections), as
well as any required APIs, vault plugions and UI components.

Nodes may also run standalone Corda applications that provide manageability and tooling support to a Corda network.