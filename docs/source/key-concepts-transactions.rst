Transactions
============

Transactions are used to update the ledger by consuming existing state objects and producing new state objects.

A transaction update is accepted according to the following two aspects of consensus:

   #. Transaction validity: parties can ensure that the proposed transaction and all its ancestors are valid
      by checking that the associated contract code runs successfully and has all the required signatures
   #. Transaction uniqueness: parties can ensure there exists no other transaction, over which we have previously reached
      consensus (validity and uniqueness), that consumes any of the same states. This is the responsibility of a notary service.

Beyond inputs and outputs, transactions may also contain **commands**, small data packets that
the platform does not interpret itself but which parameterise execution of the contracts. They can be thought of as
arguments to the verify function. Each command has a list of **composite keys** associated with it. The platform ensures
that the transaction has signatures matching every key listed in the commands before the contracts start to execute. Thus, a verify
function can trust that all listed keys have signed the transaction, but is responsible for verifying that any keys required
for the transaction to be valid from the verify function's perspective are included in the list. Public keys
may be random/identityless for privacy, or linked to a well known legal identity, for example via a
*public key infrastructure* (PKI).

.. note:: Linkage of keys with identities via a PKI is only partially implemented in the current code.

Commands are always embedded inside a transaction. Sometimes, there's a larger piece of data that can be reused across
many different transactions. For this use case, we have **attachments**. Every transaction can refer to zero or more
attachments by hash. Attachments are always ZIP/JAR files, which may contain arbitrary content. These files are
then exposed on the classpath and so can be opened by contract code in the same manner as any JAR resources
would be loaded.

Note that there is nothing that explicitly binds together specific inputs, outputs, commands or attachments. Instead,
it's up to the contract code to interpret the pieces inside the transaction and ensure they fit together correctly. This
is done to maximise flexibility for the contract developer.

Transactions may sometimes need to provide a contract with data from the outside world. Examples may include stock
prices, facts about events or the statuses of legal entities (e.g. bankruptcy), and so on. The providers of such
facts are called **oracles** and they provide facts to the ledger by signing transactions that contain commands they
recognise, or by creating signed attachments. The commands contain the fact and the signature shows agreement to that fact.

Time is also modelled as a fact and represented as a **timestamping command** placed inside the transaction. This specifies a
time window in which the transaction is considered valid for notarisation. The time window can be open ended (i.e. with a start but no end or vice versa).
In this way transactions can be linked to the notary's clock.

It is possible for a single Corda network to have multiple competing notaries. A new (output) state is tied to a specific
notary when it is created. Transactions can only consume (input) states that are all associated with the same notary.
A special type of transaction is provided that can move a state (or set of states) from one notary to another.

.. note:: Currently the platform code will not automatically re-assign states to a single notary. This is a future planned feature.

Transaction Validation
^^^^^^^^^^^^^^^^^^^^^^
When a transaction is presented to a node as part of a flow it may need to be checked. Checking original transaction validity is
the responsibility of the ``ResolveTransactions`` flow. This flow performs a breadth-first search over the transaction graph,
downloading any missing transactions into local storage and validating them. The search bottoms out at transactions without inputs
(eg. these are mostly created from issuance transactions). A transaction is not considered valid if any of its transitive dependencies are invalid.

.. note:: Non-validating notaries assume transaction validity and do not request transaction data or their dependencies
beyond the list of states consumed.

The tutorial ":doc:`tutorial-contract`" provides a hand-ons walk-through using these concepts.

Transaction Representation
^^^^^^^^^^^^^^^^^^^^^^^^^^
By default, all transaction data (input and output states, commands, attachments) is visible to all participants in
a multi-party, multi-flow business workflow. :doc:`merkle-trees` describes how Corda uses Merkle trees to
ensure data integrity and hiding of sensitive data within a transaction that shouldn't be visible in its entirety to all
participants (eg. oracles nodes providing facts).

FROM THE SECURITY MODEL PAGE
----------------------------
Privacy techniques

* Partial data visibility: transactions are not globally broadcast as in many other systems.
* Transaction tear-offs: Transactions are structured as Merkle trees, and may have individual subcomponents be revealed to parties who already know the Merkle root hash. Additionally, they may sign the transaction without being able to see all of it.

    See :doc:`merkle-trees` for further detail.

* Multi-signature support: Corda uses composite keys to support scenarios where more than one key or party is required to authorise a state object transition.

.. note:: Future privacy techniques will include key randomisation, graph pruning, deterministic JVM sandboxing and support for secure signing devices.
See sections 10 and 13 of the `Technical white paper`_ for detailed descriptions of these techniques and features.

.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf
