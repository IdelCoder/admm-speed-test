admm-speed-test
===============

Code for comparing ADMM and an interior-point method, as presented in
[Hinge-Loss Markov Random Fields and Probabilistic Soft Logic](http://arxiv.org/abs/1505.04406)
and my Ph.D. dissertation. It also contains the data and setup for the paper
[Scaling MPE Inference for Constrained Continuous Markov Random Fields with Consensus Optimization](http://stephenbach.net/files/bach-nips12.pdf),
but this code tests the improved version of the inference algorithm for
piecewise-quadratic problems.

Please cite this work as

	@article{bach:arxiv15,
	 Title = {Hinge-Loss Markov Random Fields and Probabilistic Soft Logic},
	 Author = {Bach, Stephen H. and Broecheler, Matthias and Huang, Bert and Getoor, Lise},
	 Volume = {arXiv:1505.04406 [cs.LG]},
	 Year = {2015}}

Instructions
=============

Prerequisites
-------------
This software depends on Java 6+ and [Maven 3](http://maven.apache.org).
Python (>=2.7) is also required to process the results.

Running the experiment also requires installing the [MOSEK](http://mosek.com)
add-on for PSL. Please follow the [instructions](https://github.com/linqs/psl/wiki/MOSEK-add-on).

PSL Library
-----------
The algorithms for this experiment is implemented in the PSL library,
[version 1.2](https://github.com/linqs/psl/tree/1.2). It will be downloaded
by Maven automatically.

Executing
---------
The experiment can be run by executing run.sh.
