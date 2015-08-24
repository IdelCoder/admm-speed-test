package edu.umd.cs;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import edu.emory.mathcs.utils.ConcurrencyUtils
import edu.umd.cs.psl.application.inference.MPEInference
import edu.umd.cs.psl.application.util.GroundKernels;
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.core.*
import edu.umd.cs.psl.core.inference.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database
import edu.umd.cs.psl.database.DatabasePopulator
import edu.umd.cs.psl.database.DatabaseQuery
import edu.umd.cs.psl.database.Partition
import edu.umd.cs.psl.database.ReadOnlyDatabase
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.evaluation.result.*
import edu.umd.cs.psl.groovy.*
import edu.umd.cs.psl.model.argument.ArgumentType
import edu.umd.cs.psl.model.argument.GroundTerm
import edu.umd.cs.psl.model.argument.StringAttribute
import edu.umd.cs.psl.model.argument.Variable
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.QueryAtom
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.function.ExternalFunction
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.optimizer.conic.mosek.MOSEK;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;
import edu.umd.cs.psl.reasoner.conic.ConicReasoner;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.ui.loading.*;
import edu.umd.cs.psl.util.database.Queries;

Logger log = LoggerFactory.getLogger(this.class);

if (args.length < 3 || args.length >= 5) {
	throw new IllegalArgumentException("Usage: <java command> <Method> <Size> <Model> (db path modifier)");
}

/*
 * Prepares configuration bundle
 */

ConfigManager cm = ConfigManager.getManager();
ConfigBundle config = cm.getBundle("speedtest");

if (args[0].equals("admm")) {
	config.setProperty(MPEInference.REASONER_KEY, "edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory");
	config.setProperty(ADMMReasoner.NUM_THREADS_KEY, 1);
}
else if (args[0].equals("ipm")) {
	config.setProperty(MPEInference.REASONER_KEY, "edu.umd.cs.psl.reasoner.conic.ConicReasonerFactory");
	config.setProperty(ConicReasoner.CPS_KEY, "edu.umd.cs.psl.optimizer.conic.mosek.MOSEKFactory");
	config.setProperty(MOSEK.DUALITY_GAP_THRESHOLD_KEY, 1e-4);
	config.setProperty(MOSEK.PRIMAL_FEASIBILITY_THRESHOLD_KEY, 1e-4);
	config.setProperty(MOSEK.DUAL_FEASIBILITY_THRESHOLD_KEY, 1e-4);
}
else {
	throw new IllegalArgumentException("Method must be either admm or ipm.");
}

boolean sq;
if (args[2].equals("linear")) {
	sq = false;
}
else if (args[2].equals("quad")) {
	sq = true;
}
else {
	throw new IllegalArgumentException("Model must be either linear or quad.");
}

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = ((args.length == 4) ? defaultPath + args[3] : defaultPath) + File.separator + "speedtest";
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config);

/*
 * Defines model
 */

PSLModel m = new PSLModel(this, data);

m.add predicate: "knows" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "knowswell" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "mentor" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "boss" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "olderRelative" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "idol" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "registeredAs" , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "party" , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "votes", types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

m.add function: "bias" , implementation: new PersonalBias();

m.add rule : ( registeredAs(A,X) & party(P,Y) & bias(X,Y) ) >> votes(A,P),  weight : 0.5, squared: sq
m.add rule : ( votes(A,P) & knowswell(B,A) ) >> votes(B,P),  weight : 0.3, squared: sq
m.add rule : ( votes(A,P) & knows(B,A) ) >> votes(B,P),  weight : 0.1, squared: sq
m.add rule : ( votes(A,P) & boss(B,A) ) >> votes(B,P),  weight : 0.05, squared: sq
m.add rule : ( votes(A,P) & mentor(B,A) ) >> votes(B,P),  weight : 0.1, squared: sq
m.add rule : ( votes(A,P) & olderRelative(B,A) ) >> votes(B,P),  weight : 0.7, squared: sq
m.add rule : ( votes(A,P) & idol(B,A) ) >> votes(B,P),  weight : 0.8, squared: sq

m.add PredicateConstraint.PartialFunctional , on : votes

/*
 * Loads data
 */

Partition read =  new Partition(1);
Partition write = new Partition(2);

def inserter = data.getInserter(party, read);
inserter.insert 0, 'Republican';
inserter.insert 1, 'Democratic';

def file = "./data/socialNet" + args[1] + ".txt";

InserterLookupMap inserterLookup = new InserterLookupMap();
inserterLookup.put "knows", data.getInserter(knows, read);
inserterLookup.put "knowswell", data.getInserter(knowswell, read);
inserterLookup.put "mentor", data.getInserter(mentor, read);
inserterLookup.put "boss", data.getInserter(boss, read);
inserterLookup.put "olderRelative", data.getInserter(olderRelative, read);
inserterLookup.put "idol", data.getInserter(idol, read);
inserterLookup.put "anon1", data.getInserter(registeredAs, read);
InserterUtils.loadDelimitedMultiData inserterLookup, 1, file

Database db = data.getDatabase(write, read);
int numEntities = db.executeQuery(new DatabaseQuery(registeredAs(X,Y).getFormula())).size();

DatabasePopulator dbPop = new DatabasePopulator(db);
Variable Party = new Variable("Party");
Set<GroundTerm> partyGroundings = new HashSet<GroundTerm>();
partyGroundings.add(data.getUniqueID(0));
partyGroundings.add(data.getUniqueID(1));

Variable Person = new Variable("Person");
Set<GroundTerm> personGroundings = new HashSet<GroundTerm>();
for (int i = 0; i < numEntities; i++)
	personGroundings.add(data.getUniqueID(i));

Map<Variable, Set<GroundTerm>> substitutions = new HashMap<Variable, Set<GroundTerm>>();
substitutions.put(Person, personGroundings);
substitutions.put(Party, partyGroundings);
dbPop.populate(new QueryAtom(votes, Person, Party), substitutions);

/*
 * Runs inference
 */

MPEInference mpe = new MPEInference(m, db, config);
FullInferenceResult result = mpe.mpeInference();

System.out.println("Objective: " + GroundKernels.getTotalWeightedIncompatibility(mpe.getReasoner().getCompatibilityKernels()));
System.out.println("Infeasibility: " + GroundKernels.getInfeasibilityNorm(mpe.getReasoner().getConstraintKernels()));

/*
 * If we are testing ADMM, repairs the feasibility using a simple method, so that
 * we compare the quality of two perfectly feasible solutions between ADMM and the IPM.
 */
if (args[0].equals("admm")) {
	for (GroundConstraintKernel gk : mpe.getReasoner().getConstraintKernels()) {
		if (gk instanceof GroundConstraintKernel) {
			ConstraintTerm con = gk.getConstraintDefinition();
			double value = con.getFunction().getValue();
			if ((FunctionComparator.SmallerThan.equals(con.getComparator()) && value > con.getValue())
					|| (FunctionComparator.Equality.equals(con.getComparator()))
					|| (FunctionComparator.LargerThan.equals(con.getComparator()) && value < con.getValue())) {
				if (gk.getAtoms().size() == 2 && FunctionComparator.SmallerThan.equals(con.getComparator())) {
					diff = (con.getValue() - con.getFunction().getValue()) / 2;
					for (GroundAtom atom : gk.getAtoms()){
						if (atom instanceof RandomVariableAtom) {
							((RandomVariableAtom) atom).setValue(atom.getValue() + diff);
						}
						else {
							throw new IllegalStateException("Cannot repair feasibility because atom is observed: " + atom);
						}
					}
				}
				else
					throw new IllegalStateException("Only repairs less-than constraints with two atoms.");
			}
		}
	}
	
	System.out.println("Repaired objective: " + GroundKernels.getTotalWeightedIncompatibility(mpe.getReasoner().getCompatibilityKernels()));
	System.out.println("Repaired infeasibility: " + GroundKernels.getInfeasibilityNorm(mpe.getReasoner().getConstraintKernels()));
}

/* We close the Database to make sure all writes are flushed */
db.close();

/*
 * Model functions 
 */

class PartyAffilication implements ExternalFunction {
	
	public double similarity(String a, String b) {
		return (a.charAt(0)==b.charAt(0)?1.0:0.0);
	}

	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
		String a = ((StringAttribute) args[0]).getValue();
		String b = ((StringAttribute) args[1]).getValue();
		return similarity(a, b);
	}

	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		ArgumentType[] types = new ArgumentType[2];
		types[0] = ArgumentType.String;
		types[1] = ArgumentType.String;
		return types;
	}
	
}

class PersonalBias implements ExternalFunction {
	
	public double similarity(String a, String b) {
		double value = Double.parseDouble(a);
		if (b.charAt(0)=='R') {
			if (value>=0) return 0.0;
			else return Math.abs(value)
		} else if (b.charAt(0)=='D') {
			if (value<=0) return 0.0;
			else return value;
		} else throw new IllegalArgumentException();
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
		String a = ((StringAttribute) args[0]).getValue();
		String b = ((StringAttribute) args[1]).getValue();
		return similarity(a, b);
	}

	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		ArgumentType[] types = new ArgumentType[2];
		types[0] = ArgumentType.String;
		types[1] = ArgumentType.String;
		return types;
	}
	
}
