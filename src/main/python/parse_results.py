#!/usr/bin/env python

import re

all_data = ['22050', '33075', '38588', '44100', '49613', '55125', '66150']
ipm_quad_data = ['22050', '33075', '38588']
nums_pots_and_cons = ['130080', '194408', '224938', '260970', '292692', '326626', '397488']

def main():

	# Parses results
	results = {}
	for i in range(1,4):
		for data in all_data:
				for model in ['linear', 'quad']:
					key = 'admm-' + data + '-' + model + '-' + str(i)
					results[key] = parseFile(key)

		for data in all_data:
			key = 'ipm-' + data + '-linear-' + str(i)
			results[key] = parseFile(key)

		for data in ipm_quad_data:
			key = 'ipm-' + data + '-quad-' + str(i)
			results[key] = parseFile(key)

	# Writes out results
	print " , Num. Potentials and Constraints, , Time 1, Time 2, Time 3, , Objective 1, Objective 2, Objective 3, , Infeasibility 1, Infeasibility 2, Infeasibility 3"

	print "ADMM - Linear"
	for data in all_data:
		key = 'admm-' + data + '-linear'
		printLine(results, key)

	print "IPM - Linear"
	for data in all_data:
		key = 'ipm-' + data + '-linear'
		printLine(results, key)
	
	print "ADMM - Quad"
	for data in all_data:
		key = 'admm-' + data + '-quad'
		printLine(results, key)
	
	print "IPM - Quad"
	for data in ipm_quad_data:
		key = 'ipm-' + data + '-quad'
		printLine(results, key)

def parseFile(key):
	result = runResult()
	timeStart = None
	timeStartPat = re.compile("^(\\d+) \\[main\\] INFO  edu.umd.cs.psl.application.inference.MPEInference  - Beginning inference.")
	timeStopPat = re.compile("^(\\d+) \\[main\\] INFO  edu.umd.cs.psl.application.inference.MPEInference  - Inference complete. Writing results to Database.")
	if key.startswith('admm'):
		objPat = re.compile("^Repaired objective: (\\d+\\.\\d+)")
		infPat = re.compile("^Repaired infeasibility: (\\d+\\.\\d+((E-\\d+)|(E+\\d+))?)")
	else:
		objPat = re.compile("^Objective: (\\d+\\.\\d+)")
		infPat = re.compile("^Infeasibility: (\\d+\\.\\d+((E-\\d+)|(E+\\d+))?)")

	for line in open('results/' + key + '.out'):
		match = timeStartPat.match(line.strip())
		if match is not None:
			timeStart = match.group(1)
			continue

		match = timeStopPat.match(line.strip())
		if match is not None:
			result.time = (float(match.group(1)) - float(timeStart))/1000
			continue

		match = objPat.match(line.strip())
		if match is not None:
			result.objective = match.group(1)
			continue

		match = infPat.match(line.strip())
		if match is not None:
			result.infeasibility = match.group(1)
			continue

	return result

def printLine(results, key):
	times = []
	objs = []
	infs = []
	size = nums_pots_and_cons[all_data.index(key.split('-')[1])]
	for i in range(1,4):
		times.append(str(results[key+'-'+str(i)].time))
		objs.append(str(results[key+'-'+str(i)].objective))
		infs.append(str(results[key+'-'+str(i)].infeasibility))
	print ", " + size + ", , " + ", ".join(times) + ", , " + ", ".join(objs) + ", , " + ", ".join(infs)

class runResult:
	time = None
	objective = None
	infeasibility = None

if __name__ == '__main__':
	main()
