#!/bin/bash
find ./ -name ".project" | xargs rm
find ./ -name ".cache" | xargs rm
find ./ -name ".classpath" | xargs rm
find ./ -name "target" | xargs rm -fr

