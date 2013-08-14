#!/bin/bash

parse_config ()
{
	while read line
	do
		line=$(echo $line | sed 's/\s\s*#.*$//' | grep '^[A-Za-z_][A-Za-z0-9_]*=.*$')
		if [ -n "$line" ]
		then
			VAR=$(echo $line | sed -e 's/=.*$//')
			VAL=$(echo $line | sed -e 's/^.*=//' -e 's/^/"/' -e 's/$/"/')
			eval "$VAR=$VAL"
		fi
	done < "$1"
}
