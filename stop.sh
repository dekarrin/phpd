#!/bin/bash

# quickly stop the daemon after daemon.php is manually launched

. config.sh
parse_config vars.cfg

send.sh "$PARTE_PHPD_CMD_END"
