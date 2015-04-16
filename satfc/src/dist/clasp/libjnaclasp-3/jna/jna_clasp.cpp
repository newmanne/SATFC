// Guillaume Saulnier-Comte
#include <iostream>
#include <sstream>

#include "jna_clasp.h"
#include <clasp/solver.h>
#include <clasp/enumerator.h>


namespace JNA {

	JNAProblem::JNAProblem() {
		state = r_UNKNOWN;
		assignment = NULL;
		facade = NULL;
		config = NULL;
	}

	JNAProblem::~JNAProblem() {
		delete[] assignment;
		assignment = NULL;
		delete facade;
		facade = NULL;
		if (config != NULL) {
			config->releaseConfig(configKey);	
		}
		delete config;
		config = NULL;
	}

	void JNAProblem::setFacade(Clasp::ClaspFacade* facade_) {
		facade = facade_;
	}

	void JNAProblem::setConfig(Clasp::Cli::ClaspCliConfig* config_) {
		config = config_;
	}

	void JNAProblem::setConfigKey(Clasp::Cli::ConfigKey key_) {
		configKey = key_;
	}

	void JNAProblem::setResultState(Result_State state_) {
		state = state_;
	}

	int JNAProblem::getResultState() {
		return state;
	}

	int* JNAProblem::getAssignment() {
		return assignment;
	}

	Clasp::ClaspFacade* JNAProblem::getFacade() {
		return facade;
	}

	bool JNAProblem::onModel(const Clasp::Solver& s, const Clasp::Model& m) {
		const Clasp::SymbolTable& index = s.symbolTable();
		delete[] assignment;
		assignment = NULL;
		assignment = new int[index.size()];
		assignment[0] = index.size();
		int i = 1;
		for (Var v = 1; v < index.size(); ++v)
		{
			assignment[i] = (s.value(v) == value_false ? -1:1)*v;
			i++;
		}
		return true;
	}

}

// C functions for the JNA library interface

using namespace JNA;

void* initProblem(const char* params, const char* problem) {
	JNAProblem* jnaProblem = new JNAProblem();

	// Init the configuration
	Clasp::Cli::ClaspCliConfig* config = new Clasp::Cli::ClaspCliConfig();
	jnaProblem->setConfig(config);
	Clasp::Cli::ConfigKey key = config->allocConfig();
	std::cout << "Config key is " << key << std::endl;		
	jnaProblem->setConfigKey(key);
	config->appendConfig(key, "SATFC-Config", params);
	config->init(0, key);

	// Init the facade
	Clasp::ClaspFacade* facade = new Clasp::ClaspFacade();
	jnaProblem->setFacade(facade);
	std::istringstream problemAsStream (problem);
	// Parse the problem
	facade->startSat(*config).parseProgram(problemAsStream);
	if (!facade->prepare()) {
		// TODO: should probably convey this to java
		std::cout << "error in prepare" << std::endl;		
	} 
	return jnaProblem;
}

void solveProblem(void* problem, double timeoutTime) {
	JNAProblem* jnaProblem = reinterpret_cast<JNA::JNAProblem*>(problem);
	Clasp::ClaspFacade* facade = jnaProblem->getFacade();

	// Start the solve, passing in the event handler (which JNAProblem implements)
	Clasp::ClaspFacade::AsyncResult asyncResult = facade->solveAsync(jnaProblem);
	if (asyncResult.waitFor(timeoutTime)) {
		// The problem was solved
		Clasp::ClaspFacade::Result result = asyncResult.get();
		if (result.sat()) {
			jnaProblem->setResultState(r_SAT);
		} else if (result.unsat()) {
			jnaProblem->setResultState(r_UNSAT);
		} else {
			std::cout << "result was neither sat or unsat?" << std::endl;		
			// TODO: what now? I don't think you can reach here...
		}
	} else {
		jnaProblem->setResultState(r_TIMEOUT);
		if (!asyncResult.cancel()) {
			// TODO: what now?
			std::cout << "error in cancel" << std::endl;		
			// abort not successful? No idea what to do here
		}
	} 
}

void destroyProblem(void* problem) {
	JNA::JNAProblem* jnaProblem = reinterpret_cast<JNA::JNAProblem*>(problem);
	delete jnaProblem;
}

bool interrupt(void* problem) {
	JNA::JNAProblem* jnaProblem = reinterpret_cast<JNA::JNAProblem*>(problem);
	// TODO:
	return false;
}

int getResultState(void* problem) {
	JNA::JNAProblem* jnaProblem = reinterpret_cast<JNA::JNAProblem*>(problem);
	return jnaProblem->getResultState();
}

int* getResultAssignment(void* problem) {
	JNA::JNAProblem* jnaProblem = reinterpret_cast<JNA::JNAProblem*>(problem);
	return jnaProblem->getAssignment();
}