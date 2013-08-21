phpd
====

Executes PHP code incrementally with a daemon instead of all at once. Normally,
PHP is immediately evaluated and the Zend Engine is shut down. This makes it
difficult to analyze scripts with external tools.

Phpd provides a PHP script that executes arbitrary PHP code without shutting
down the engine. The script is run as a deamon that recieves its commands and
code through a socket interface.

Requirements
------------

Phpd has been tested with PHP v5.2; any later versions should work fine, and it
may run with earlier versions as well. It requires that PHP be compiled with the
--enable-sockets option.

All shell scripts have been tested with bash; they may work in other shells with
minor modification.

Usage
-----

Run `phpd.sh -h' to see a list of commands.

Java Interface
--------------

The daemon may be used from java by building the included JPHPD library. Due to
the source code's dependence on custom macros, it must be built by using ant.
Run `ant help' to see a list of build targets.
