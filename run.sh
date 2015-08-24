#!/bin/sh

echo "Compiling..."
mvn compile > /dev/null
mvn dependency:build-classpath -Dmdep.outputFile=classpath.out > /dev/null
mkdir -p results > /dev/null

echo "Starting experiment..."
for i in `seq 1 3`;
do
	# Linear problems
	for method in "admm" "ipm";
	do
		for size in "22050" "33075" "38588" "44100" "49613" "55125" "66150";
		do
			echo -n "Running $method $size linear #$i..." 
			java -Xmx2g -cp ./target/classes:`cat classpath.out` edu.umd.cs.ADMMSpeedTest $method $size linear > results/$method-$size-linear-$i.out
			echo "Done!"
		done
	done

	# Quadratic problems
	for size in "22050" "33075" "38588" "44100" "49613" "55125" "66150";
	do
		echo -n "Running admm $size quad #$i..."
		java -Xmx2g -cp ./target/classes:`cat classpath.out` edu.umd.cs.ADMMSpeedTest admm $size quad > results/admm-$size-quad-$i.out
		echo "Done!"
	done

	for size in "22050" "33075" "38588";
	do
		echo -n "Running ipm $size quad #$i..."
		java -Xmx2g -cp ./target/classes:`cat classpath.out` edu.umd.cs.ADMMSpeedTest ipm $size quad > results/ipm-$size-quad-$i.out
		echo "Done!"
	done
done

echo "Writing results to ./results/results.csv"
./src/main/python/parse_results.py > ./results/results.csv
