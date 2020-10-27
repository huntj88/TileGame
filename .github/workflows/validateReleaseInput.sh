#!/bin/bash

echo "part to bump: $1"

inputOptions="^(major|minor|patch)$"
if [[ $1 =~ $inputOptions ]];
then
  echo "valid tag bump option: $1"
else
  echo "invalid option: $1, expected (major, minor, patch)"
  exit 1
fi
