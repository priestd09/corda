Nodes
=====

.. topic:: Summary

   * *A node is JVM run-time with a unique network identity running the Corda software*
   * *The node has three access points:*

      * *A network layer, for interacting with other nodes*
      * *RPC, for interacting with the node's owner*
      * *JDBC, for querying the persistence layer*

   * *The node is customized by installing CorDapps in the plugin registry*

Node architecture
-----------------
A Corda node is a JVM run-time environment with a unique identity on the network hosting Corda services and CorDapps.

We can visualize the node's internal architecture as follows:

.. image:: resources/node-architecture.png

The core elements of the architecture are:

* A persistence layer for storing data
* A network interface for interacting with other nodes
* An RPC interface for interacting with the node's owner
* A service hub for giving flows access to the node's services
* A plugin registry for extending the node by installing CorDapps

Persistence layer
-----------------
The persistence layer is split into two components:

* The **vault**, where the node stores any relevant current and historic states
* The **storage service**, where it stores transactions, attachments and flow checkpoints

The node's owner can query the node's storage using a JDBC driver.

Network interface
-----------------
All communication with other nodes on the network is handled by the node itself, as part of running a flow. The
node's owner does not interact with other network nodes directly.

RPC interface
-------------
The node's owner interacts with the node via remote procedure calls (RPC). Example RPC operations the node exposes
include:

* Starting a flow
* Reading the contents of the vault or the transaction storage
* Uploading and opening attachments

The service hub
---------------
Internally, the node has access to a much richer set of services than are exposed via RPC.

These services are used during flow execution to coordinate ledger updates. The key services provided are:

* Information on other nodes on the network and the services they offer
* Access to the contents of the vault and the storage service
* Access to, and generation of, the node's public-private keys
* Information about the node itself
* The current time, as tracked by the node

The plugin registry
-------------------
The plugin registry is where new CorDapps are installed to extend the behavior of the node.

The node also has several plugins installed by default to handle tasks such as:

* Retrieving transactions and attachments from counterparties
* Upgrading contracts
* Broadcasting agreed ledger updates for recording by counterparties