#!/bin/bash

# send any command to the daemon

. config.sh
parse_config vars.cfg

msg=
for item in "$@"
do
	len=${#item}
	msg="$msg$len
$item"
done
echo -n "$msg" | nc -U "$PARTE_PHPD_IN_SOCKET_DOMAIN" -s -
