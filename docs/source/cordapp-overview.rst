CorDapp overview
================

Corda is a platform. Its functionality is extended by developers through the creation of Corda distributed
applications (CorDapps).

These CorDapps are not installed on the network itself. Instead, individual nodes will install CorDapps to allow them
to handle new business processes. CorDapps allow nodes to handle everything from asset trading (see
:ref:`irs-demo` to portfolio valuations (see :ref:`simm-demo`).

In practical terms, CorDapps give a node's owner the ability to order their node to run new flows that automatically
negotiate a given ledger update with other nodes on the network. Flows can be started either through HTTP requests or
direct remote procedure call (RPC) requests

.. image:: resources/node-diagram.png

CorDapps are made up of definitions for the following components:

* States
* Contracts
* Flows
* Web APIs and static web content
* Services

As well as a plugin defining how these components interact.