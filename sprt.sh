#!/bin/bash

java_path=$(which java)
testbench=./testbench

args=(
  -openings file=./UHO_Lichess_4852_v1.epd format=epd order=random -srand 1007
  -rounds 50000 -concurrency 5 -maxmoves 100
  -sprt elo0=0 elo1=10 alpha=0.05 beta=0.1
  -autosaveinterval 0 -config outname=fastchess-config.json
#  -log engine=true file=fastchess.log
  -draw movenumber=40 movecount=10 score=15
  -resign movecount=4 score=700
  -each cmd="$java_path" tc=4+0.04 option.Hash=8
  -engine name="$1" args="-Xmx300M -jar $testbench/Erinn-$1.jar"
  -engine name="$2" args="-Xmx300M -jar $testbench/Erinn-$2.jar"
)

fastchess "${args[@]}"
