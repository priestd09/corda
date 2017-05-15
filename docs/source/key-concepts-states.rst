States
======

A state object represents an agreement between two or more parties, the evolution of which governed by machine-readable contract code.
This code references, and is intended to implement, portions of human-readable legal prose.
It is intended to be shared only with those who have a legitimate reason to see it.

The following diagram illustrates a state object:

.. image:: resources/contract.png

In the diagram above, we see a state object representing a cash claim of £100 against a commercial bank, owned by a fictional shipping company.

.. note:: Legal prose (depicted above in grey-shade) is currently implemented as an unparsed reference to the natural language
contract that the code is supposed to express (usually a hash of the contract's contents).

States contain arbitrary data, but they always contain at minimum a hash of the bytecode of a
**contract code** file, which is a program expressed in JVM byte code that runs sandboxed inside a Java virtual machine.
Contract code (or just "contracts" in the rest of this document) are globally shared pieces of business logic.

.. note:: In the current code dynamic loading of contracts is not implemented. This will change in the near future.