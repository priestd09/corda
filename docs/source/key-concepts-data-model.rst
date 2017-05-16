Data model
==========

Overview
--------

In Corda, there is **no single central store of data**. Instead, each node maintains its own database of known facts.

The guarantee made by Corda is that whenever one of these facts is shared by multiple nodes on the network, it evolves
in lockstep in the database of every node that is aware of it.

For example, we can imagine a network with five nodes, each storing a set of shared facts:

* **Alice**, storing facts 1 and 7
* **Bob**, storing facts 5 and 6
* **Carl**, storing facts 2, 3, 4, 5, 6 and 9
* **Demi**, storing facts 2, 3 and 8
* **Ed**, storing facts 3, 4, 8 and 9

We can see, for example, that although Carl, Demi and Ed are aware of shared fact 3, **Alice and Bob are not**. Equally
importantly, we can be sure that Carl, Demi and Ed will always see the **same version** of fact 3 at any point in time.