Consensus
=========

Consensus model
---------------

Corda nodes require a mechanism for ensuring that they remain in consensus about the current state of their shared
facts. To do so, nodes must check the validity of each transaction (i.e. each ledger update) they receive before
signing it.

Ensuring that a transaction is valid is a two-part process:

1. Transaction **validity** - checking that a transaction is accepted by the contracts of every
   single input and output state, and has all the required signatures

2. Transaction **uniqueness** - checking that the inputs to the transaction have not already been consumed in
   a previous transaction (i.e. a "double-spend")

In traditional distributed ledger systems, both of these checks are performed by every network participant. This means
that all data must be shared with every network participant, compromising privacy.

Instead, in Corda, transaction uniqueness is checked by **notary services**, while transaction validity is checked only
by the participants to a transaction (in some cases, the notary will also check transaction validity).

Notarisation
------------

A **notary** is a service that provides transaction ordering by attesting that, for a given transaction, it has not
signed another transaction consuming any of the same input states.

Upon being sent asked to notarise a transaction, a notary will either:

* Sign the transaction if it has not already signed another transaction consuming any of the same input states
* Reject the transaction and flag that a double-spend attempt has occurred otherwise

In doing so, the notary provides the point of finality in the system. Until the notary's signature is obtained, parties
cannot be sure that an equally valid, but conflicting, transaction will not be regarded the "valid" attempt to spend
the input states in question. However, after the signature is obtained, the parties know that the transaction's inputs
had not already been consumed. Hence, notarisation is the point at which we can say finality has occurred.

Every **state** has an appointed notary, and a notary will only notarise a transaction if it is the appointed notary
for all the input and output states.

Consensus algorithms
^^^^^^^^^^^^^^^^^^^^

Corda has "pluggable" consensus, allowing notaries to choose the preferred trade-offs between privacy, scalability,
legal-system compatibility and algorithmic agility.

In particular, notaries may differ in terms of:

* **Structure** - a notary may be a single node, a cluster of mutually-trusting nodes, or a cluster of mutually-distrusting
  nodes
* **Uniquness algorithm** - a notary cluster may choose to run a high-speed, high-trust RAFT algorithm, or a low-speed,
  low-trust BFT algorithm

Validation
^^^^^^^^^^

A notary service must also decided whether or not to **validate** a transaction before accepting it. The importance of
this trade-off is as follows:

* If a transaction **is not** checked for validity, it opens the platform to "denial of state" attacks, where a node
  knowingly builds an invalid transaction consuming the states of another nodes and sends the transaction to the
  notary, causing the states to be marked as consumed.
* If the transaction **is** checked for validity, the notary will need to see the full contents of the transaction and
  its dependencies. This is a privacy leak.

In both cases, there are attenuating factors. For the non-validating model, Corda's controlled data distribution model
means that information on unconsumed states is not widely shared. Additionally, Corda's permissioned network means that
the notary can store to the identity of the party that created the "denial of state" transaction, allowing the attack
to be resolved off-ledger.

For the validating model, the use of anonymous, freshly-generated public keys instead of identities to identify
parties in a transaction limit the information the notary sees.

The platform is flexible and currently supports both validating and non-validating notary implementations. Nodes can
select which one to use based on their privacy requirements.

Multiple notaries
^^^^^^^^^^^^^^^^^

Each Corda network can have multiple notaries, each potentially running a different consensus algorithm. This provides
several benefits:

* **Privacy** - we can have both validating and non-validating notaries on the same network, allowing nodes to make a
  choice based on their specific requirements
* **Load balancing** - spreading the transaction load over multiple notaries allows higher transaction throughput for
  the platform overall
* **Low latency** - latency can be minimised by choosing a notary physically closer to the transacting parties

Changing notaries
^^^^^^^^^^^^^^^^^

A notary will only sign a transaction if it is the appointed notary of all the transaction's input states. However,
there are several cases in which we may want to change a state's appointed notary, including:

* A single transaction needs to consume states with different appointed notaries
* A node would prefer to use a different notary for a given transaction due to privacy or speed concerns

In cases where a transaction involves states controlled by multiple notaries, the states first have to be repointed to
the same notary. This is achieved using a special type of transaction that takes:

* A single input state
* An output state identical to the input state, except that the appointed notary has been changed

In practice, changing a state's appointed notary is handled by a library flow called the ``NotaryChangeFlow``. This
flow will:

1. Construct a transaction with the old state as the input and the new state as the output
2. Obtain signatures from all the *participants* (where a *participant* is any party that is able to consume this state in
   a valid transaction, as defined by the state itself)
3. Obtain the **old** notary's signature
4. Record and distribute the final transaction to the participants so that everyone has the new state

.. note:: In the future, changing notaries will be handled automatically on demand.

Timestamping
------------

A notary also act as the *timestamping authority*, verifying the timestamp on a transaction before notarising it.

For a timestamp to be meaningful, its implications must be binding on the party requesting it. A party can obtain a
timestamp signature in order to prove that some event happened *before*, *on*, or *after* a particular point in time.
However, if the party is not also compelled to commit to the associated transaction, it has a choice of whether or not
to reveal this fact until some point in the future. As a result, we need to ensure that the notary either has to also
sign the transaction within some time tolerance, or perform timestamping *and* notarisation at the same time. The
latter is the chosen behaviour for this model.

There will never be exact clock synchronisation between the party creating the transaction and the notary.
This is not only due to issues of physics and network latency, but also because between inserting the command and
getting the notary to sign there may be many other steps (e.g. sending the transaction to other parties involved in the
trade, requesting human sign-off...). Thus the time at which the transaction is sent for notarisation may be quite
different to the time at which the transaction was created.

For this reason, times in transactions are specified as time *windows*, not absolute times.
In a distributed system there can never be "true time", only an approximation of it. Time windows can be
open-ended (i.e. specify only one of "before" and "after") or they can be fully bounded. If a time window needs to
be converted to an absolute time (e.g. for display purposes), there is a utility method on ``Timestamp`` to
calculate the mid point.

In this way, we express the idea that the *true value* of the fact "the current time" is actually unknowable. Even when
both before and after timestamps are included, the transaction could have occurred at any point between those two
timestamps.

By creating a range that can be either closed or open at one end, we allow all of the following situations to be
modelled:

* This transaction occurred at some point after the given time (e.g. after a maturity event)
* This transaction occurred at any time before the given time (e.g. before a bankruptcy event)
* This transaction occurred at some point roughly around the given time (e.g. on a specific day)

.. note:: It is assumed that the time feed for a notary is GPS/NaviStar time as defined by the atomic
   clocks at the US Naval Observatory. This time feed is extremely accurate and available globally for free.

Also see section 7 of the `Technical white paper`_ which covers this topic in significantly more depth.

.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf