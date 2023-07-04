#!/usr/bin/env python2.7
# encoding: utf-8

'''
emptyWrapper -- template for a wrapper based in genericWrapper.py

@author:     Marius Lindauer, Chris Fawcett, Alex Fréchette, Frank Hutter
@copyright:  2014 AClib. All rights reserved.
@license:    GPL
@contact:    lindauer@informatik.uni-freiburg.de, fawcettc@cs.ubc.ca, afrechet@cs.ubc.ca, fh@informatik.uni-freiburg.de

'''

from generic_wrapper_mod import AbstractWrapper
import re
import os
import pandas as pd
import sys
import argparse
import imp
from tempfile import NamedTemporaryFile
import shutil
import gzip

SATFC_DIR = "/ubc/cs/research/arrow/satfc"
CNF_BASE_DIR = "/global/scratch/newmanne/satfc/instances/cnfs"
ALG_DIR = os.path.join(SATFC_DIR, "aclib/target_algorithms")
SAT_ALG_DIR = os.path.join(ALG_DIR, "sat")
MIP_ALG_DIR = os.path.join(ALG_DIR, "mip")

PURELY_SLS_SOLVERS = [
    'SATenstein', 'gnovelty+PCL', 'gnovelty+GCwa', 'gnovelty+GCa',
    'DCCASat+march-rw-cssc14', 'CSCCSat2014-cssc14', 'probSAT-cssc14',
    'yalsat-cssc14', 'DCCA+'
    ]

USES_ASSIGNMENT = ['DCCA+', 'SATenstein']

class SATFCWrapper(AbstractWrapper):

    def __init__(self):
        AbstractWrapper.__init__(self)
        self.parser.add_argument("--portfolio_file", required=False, default=None)
        self.parser.add_argument("--cnf_dir", required=False)
        self.parser.add_argument("--preserve", default=False, type=bool)
        self.parser.add_argument("--solubility_file", required=False, default=None)
        self.parser.add_argument("--output_solution_file", required=False, default=None)

    @staticmethod
    def alg_type(alg):
        return 'mip' if ('cplex' in alg or 'gurobi' in alg) else 'sat'

    @staticmethod
    def can_prove_UNSAT(alg):
        return not alg in PURELY_SLS_SOLVERS

    @staticmethod
    def uses_assignment(alg):
        return alg in USES_ASSIGNMENT

    def get_cnf_filename(self, instance, encoding=None, opt=None, suffix='.cnf'):
        if instance.endswith(suffix):
            return instance
        # Special SATFC encoding
        if encoding and opt:
            return os.path.join(CNF_BASE_DIR, self.args.cnf_dir, start, encoding, opt, os.path.basename(instance) + suffix)
        else:
            return os.path.join(CNF_BASE_DIR, self.args.cnf_dir, os.path.basename(instance) + suffix)

    def get_command_line_args(self, runargs, config):
        '''
        Returns the command line call string to execute the target algorithm (here: Spear).
        Args:
            runargs: a map of several optional arguments for the execution of the target algorithm.
                    {
                      "instance": <instance>,
                      "specifics" : <extra data associated with the instance>,
                      "cutoff" : <runtime cutoff>,
                      "runlength" : <runlength cutoff>,
                      "seed" : <seed>
                    }
            config: a mapping from parameter name to parameter value
        Returns:
            A command call list to execute the target algorithm.
        '''
        # Find out what algorithm we are running
        alg_choice = config['-algorithm']
        self.alg_choice = alg_choice
        alg_type = SATFCWrapper.alg_type(alg_choice)
        suffix = '.cnf' if alg_type == 'sat' else '.lp'

        # SATFC specific params
        encoding_type = config.get('-SATFC_encoding_type', None)
        encoding_optimization = config.get('-SATFC_encoding_optimization', None)

        # Do the replacement of all the weird rules
        normalized_config = { k.replace("%s_" % (alg_choice), '', 1): v for k, v in config.iteritems() if k.startswith("%s_" % (alg_choice)) }
        normalized_runargs = dict(runargs)

        if self.args.cnf_dir:
            normalized_runargs['instance'] = self.get_cnf_filename(runargs['instance'], encoding=encoding_type, opt=encoding_optimization, suffix=suffix)

        # If the file is zipped, we unzip it to a temporary directory
        if not os.path.exists(normalized_runargs['instance']):
            gz_instance = normalized_runargs['instance'] + '.gz'
            if not os.path.exists(gz_instance):
                raise ValueError("Couldn't find instance file! %s" % normalized_runargs['instance'])
            else:
                 self._gzip = True

        if self._gzip:
            self.print_d("Gunzipping file to temp dir")
            self._unzipped_file = NamedTemporaryFile(suffix=suffix, prefix="instance", dir=self._tmp_dir, delete=False)
            normalized_runargs['instance'] = self._unzipped_file.name
            with gzip.open(gz_instance, 'rb') as f_instance:
                shutil.copyfileobj(f_instance, self._unzipped_file)
            self._unzipped_file.close()
            if SATFCWrapper.uses_assignment(alg_choice):
                assignment_filename = gz_instance.replace('.cnf','_assignment.txt')
                assert os.path.isfile(assignment_filename)
                unzipped_assignment_filename = normalized_runargs['instance'].replace('.cnf', '_assignment.txt')
                self._unzipped_assignment_file = open(unzipped_assignment_filename, 'wb')
                with gzip.open(assignment_filename, 'rb') as f_assignment:
                    shutil.copyfileobj(f_assignment, self._unzipped_assignment_file)
                self._unzipped_assignment_file.close()

        if self.args.portfolio_file is not None: # Record portfolio time instead
            portfolio_time = self.portfolio_times.loc[runargs['instance']]['runtime']
            if portfolio_time < runargs['cutoff']:
                #print "Using portfolio time %.2f instead of cutoff %.2f, potentially saving %.2f s" % (portfolio_time, self._cutoff, self._cutoff - portfolio_time)
                self._cutoff = int(float(portfolio_time) + 1)
                normalized_runargs['cutoff'] = self._cutoff

        if self.args.output_solution_file and self.alg_choice == 'gurobi600':
            self._gurobi_output_file = NamedTemporaryFile(suffix=".sol", prefix="gurobi_sol", dir=self._tmp_dir, delete=True)
            normalized_config['SATFC_output_solution_file'] = self._gurobi_output_file.name


        # Call into the appropriate script and return
        py_script = "target_algorithms/%s/%s/callstring_generator.py" % (alg_type, alg_choice)
        ruby_script = "target_algorithms/%s/%s/callstring_generator.rb" % (alg_type, alg_choice)

        if os.path.exists(py_script): # Python
            loaded_script = imp.load_source("cssc", py_script)
            return loaded_script.get_command_line_cmd(normalized_runargs, normalized_config)
        elif os.path.exists(ruby_script): # Ruby
            ext_callstring = "ruby %s" % (ruby_script)
            return self.get_command_line_args_ext(runargs=normalized_runargs, config=normalized_config, ext_call=ext_callstring)
        else:
            raise ValueError("Nothing found at %s or %s" % (py_script, ruby_script))

    def process_results(self, filepointer, out_args):

        self.print_d("reading solver results from %s" % (filepointer.name))
        resultMap = {}
        resultMap['misc'] = self.alg_choice
        resultMap['status'] = self._ta_status

        hasResult = False
        solution_output = None

        alg_type = SATFCWrapper.alg_type(self.alg_choice)

        # TODO: CLEAN THIS PART UP!!!

        if alg_type == "sat":

            if self.alg_choice == 'SATenstein':
                lines = [line.strip() for line in filepointer.readlines()]
                for i, line in enumerate(lines):
                    # line indicating number of successful runs
                    if line.startswith('SuccessfulRuns ='):
                        hasResult = True
                        splits = line.split('=')
                        if int(splits[1].strip()) > 0: # at least one success run means SAT
                            resultMap['status'] = 'SUCCESS'
                        else:
                            resultMap['status'] = 'TIMEOUT'
                        break
            else:
                data = filepointer.read()
                if re.search('s SATISFIABLE', data):
                    resultMap['status'] = 'SAT'
                    hasResult = True
                elif re.search('s UNSATISFIABLE', data):
                    resultMap['status'] = 'UNSAT'
                    hasResult = True
                elif re.search('s UNKNOWN', data):
                    resultMap['status'] = 'TIMEOUT'
                elif re.search('INDETERMINATE', data):
                    resultMap['status'] = 'TIMEOUT'
                # Store result
                if self.args.output_solution_file and resultMap['status'] == 'SAT':
                    filepointer.seek(0)
                    literals = []
                    for line in filepointer.readlines():
                        line = line.strip()
                        if line.startswith('v '):
                            literals += line[2:].split(' ')
                    if literals[-1] != '0':
                        raise ValueError("Expected 0 to be last literal!")
                    solution_output = " ".join(literals[:-1])

        elif alg_type == "mip":
            if self.alg_choice == 'cplex':

                numeric_const_pattern = r"[+\-]?(?:0|[1-9]\d*)(?:\.\d*)?(?:[eE][+\-]?\d+)?"
                gap = None
                internal_measured_runtime = None
                iterations = None
                measured_runlength = None
                obj = None
                solved = None

                for line in filepointer:
                    if re.match("MIP - Integer infeasible.", line):
                        solved = "UNSAT"
                        break
                    elif re.match("gap = (%s), (%s)" %(numeric_const_pattern, numeric_const_pattern),line):
                        m = re.match("gap = %s, (%s)" %(numeric_const_pattern, numeric_const_pattern),line)
                        gap = float(m.group(0))
                    elif re.match("^Solution time\s*=\s*(%s)\s*sec\.\s*Iterations\s*=\s*(\d+)\s*Nodes\s*=\s*(\d+)$" %(numeric_const_pattern), line):
                        m = re.match("Solution time\s*=\s*(%s)\s*sec\.\s*Iterations\s*=\s*(\d+)\s*Nodes\s*=\s*(\d+)" %(numeric_const_pattern), line)
                        internal_measured_runtime = float(m.group(1))
                        iterations = int(m.group(2))
                        measured_runlength = int(m.group(3))
                    elif re.match("MIP\s*-\s*Integer optimal solution:\s*Objective\s*=\s*(%s)" %(numeric_const_pattern), line):
                        m = re.match("MIP\s*-\s*Integer optimal solution:\s*Objective\s*=\s*(%s)" %(numeric_const_pattern), line)
                        gap = 0
                        obj = float(m.group(1))
                        solved = "SAT"
                    elif re.match("MIP\s*-\s*Integer optimal,\s*tolerance\s*\(%s\/%s\):\s*Objective\s*=\s*(%s)" %(numeric_const_pattern, numeric_const_pattern, numeric_const_pattern), line):
                         m = re.match("MIP\s*-\s*Integer optimal,\s*tolerance\s*\(%s\/%s\):\s*Objective\s*=\s*(%s)" %(numeric_const_pattern, numeric_const_pattern, numeric_const_pattern), line)
                         obj = float(m.group(1))
                         solved = "SAT"
                    elif re.match("Optimal:\s*Objective =\s*%s" %(numeric_const_pattern), line):
                        solved = "SAT"
                    elif re.match("No problem exists.", line): # instance could not be read
                        solved = "ABORT"
                    elif re.match("MIP\s*-\s*Time limit exceeded, integer feasible:\s*Objective\s*=\s*(%s)" %(numeric_const_pattern), line):
                        m = re.match("MIP\s*-\s*Time limit exceeded, integer feasible:\s*Objective\s*=\s*(%s)" %(numeric_const_pattern), line)
                        obj = float(m.group(1))
                        solved = "TIMEOUT"
                    elif re.match("MIP\s*-\s*Error termination, integer feasible:\s*Objective\s*=\s*(%s)" %(numeric_const_pattern), line):
                        m = re.match("MIP\s*-\s*Error termination, integer feasible:\s*Objective\s*=\s*(%s)" %(numeric_const_pattern), line)
                        obj = float(m.group(1))
                        solved = "TIMEOUT"
                    elif "MIP - Error termination, no integer solution." in line:
                        solved = "TIMEOUT"
                    elif "MIP - Time limit exceeded, no integer solution." in line:
                        solved = "TIMEOUT"
                    elif "CPLEX Error  1001: Out of memory." in line:
                        solved = "TIMEOUT"
                    elif "MIP - Memory limit exceeded" in line:
                        solved = "TIMEOUT"

                if solved is not None:
                    resultMap["status"] = solved
                if obj is not None:
                    resultMap["quality"] = obj

            elif self.alg_choice == 'gurobi600':
                stdout = filepointer.read()
                timeout_re = re.compile(r'^Time limit reached|^Solve interrupted',flags=re.M)
                sat_re = re.compile(r'^Optimal solution found',flags=re.M)
                unsat_re = re.compile(r'^Model is infeasible',flags=re.M)

                solved = None
                if sat_re.search(stdout):
                    solved = 'SAT'
                    hasResult = True
                elif unsat_re.search(stdout):
                    solved = 'UNSAT'
                elif timeout_re.search(stdout):
                    solved = 'TIMEOUT'

                if solved:
                    resultMap['status'] = solved

                # Store result
                if self.args.output_solution_file and solved == 'SAT':
                    solution_output = self._gurobi_output_file.read()
                    self._gurobi_output_file.close()


            else:
                raise "Dont know to handle this algorithm"

        hasResult = hasResult or resultMap.get('status', None)

        # Hydra Hook
        if self.args.portfolio_file is not None:
            portfolio_time = self.portfolio_times.loc[self._instance]['runtime']
            portfolio_status = self.portfolio_times.loc[self._instance]['result']
            # Can the portfolio improve upon the result? Potentially, if the portfolio can solve the result before the cutoff
            if portfolio_status != 'TIMEOUT':
                # Check solubility
                if hasResult and portfolio_status != resultMap['status']:
                    raise ValueError("Portfolio says %s but solver returned %s for %s" % (portfolio_status, resultMap['status'], self._instance))
                if portfolio_time <= self._cutoff:
                    # If runsolver killed the run, then you SHOULD use the portfolio (runsolver killed it b/c CPU time exceeded)
                    # Otherwise, runsolver did not kill the run. So now we should just see if the portfolio was better
                    if self.killed_by_runsolver or (hasResult and (resultMap['status'] == 'TIMEOUT' or portfolio_time < self._ta_runtime)):
                        if hasResult:
                            print "Replacing the old result, score of %s, %.2f" % (resultMap['status'], self._ta_runtime)
                        print "Using the portfolio score of %s, %.2f" % (portfolio_status, portfolio_time)
                        resultMap['runtime'] = portfolio_time
                        resultMap['status'] = portfolio_status

        if self.args.output_solution_file:
            with open(self.args.output_solution_file, 'w') as solution_file:
                solution_file.write(resultMap.get('status', 'TIMEOUT') + '\n')
                solution_file.write(str(resultMap.get('runtime', self._ta_runtime)) + '\n')
                solution_file.write(str(self._walltime) + '\n')
                if solution_output:
                    solution_file.write(solution_output + '\n')

        return resultMap

if __name__ == "__main__":
    wrapper = SATFCWrapper()
    wrapper.main(['--runsolver-path', '/ubc/cs/research/arrow/satfc/runsolver/runsolver', '--mem-limit', '6144'])
