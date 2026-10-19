#!/bin/sh
mkdir -p out && javac -encoding UTF-8 -d out src/timetravel/*.java && java -cp out timetravel.Main
