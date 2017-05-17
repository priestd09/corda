CorDapp overview
================

Corda is a platform. Its functionality is extended by developers through the creation of Corda distributed
applications (CorDapps). CorDapps are not installed on the network itself, but on the individual nodes instead.

Each CorDapp allows a node to handle new business processes - everything from asset trading (see :ref:`irs-demo` to
portfolio valuations (see :ref:`simm-demo`). It does so by defining new flows on the node that can automatically
negotiate a given ledger update with other nodes on the network. The node's owner can then start these flows as
required, either through HTTP requests or direct remote procedure call (RPC) requests.

.. image:: resources/node-diagram.png

CorDapp developers will usually have to define not only these flows, but also any states and contracts that these
flows use. They will also have to define any web APIs that will run on the node's built-in web server, any static web
content, and any new services that they want their CorDapp to offer.

This means that CorDapps are made up of definitions for the following components:

* States
* Contracts
* Flows
* Web APIs and static web content
* Services