Data model
==========

Overview
--------

In Corda, there is **no single central store of data**. Instead, each node maintains its own database of known facts.

The guarantee made by Corda is that whenever one of these facts is shared by multiple nodes on the network, it evolves
in lockstep in the database of every node that is aware of it.

For example, we can imagine a network with five nodes, where each coloured circle represents a shared fact:

.. image:: resources/ledger-venn.png

We can see, for example, that although Carl, Demi and Ed are aware of shared fact 3, **Alice and Bob are not**. Equally
importantly, we can be sure that Carl, Demi and Ed will always see the **same version** of fact 3 at any point in time.