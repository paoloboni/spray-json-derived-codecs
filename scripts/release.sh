#!/usr/bin/env bash

: "${SONATYPE_USERNAME?must be defined}"
: "${SONATYPE_PASSWORD?must be defined}"

GPG_TTY=$(tty)
export GPG_TTY
sbt "release cross with-defaults skip-tests"
