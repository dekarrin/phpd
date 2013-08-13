#!/bin/bash

parse_config ()
{
	while read line
	do
		if [[ -n "$line" && ${line:0:1} != '#' ]]
		then
			VAR=$(echo $line | sed -e 's/=.*$//')
			VAL=$(echo $line | sed -e 's/^.*=//' -e 's/^/"/' -e 's/$/"/')
			eval "$VAR=$VAL"
		fi
	done < "$1"
}
