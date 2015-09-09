#ifndef _BASIS_H_
#define _BASIS_H_

#include <iostream>
#include <fstream>
#include <cstdlib>
#include <cmath>
#include <string>
#include <sstream>

using namespace std;

enum type{SAT3, SAT4, SAT5, SAT6, SAT7, SATmore, strSAT} probtype;

/* limits on the size of the problem. */
#define MAX_VARS    5000050
#define MAX_CLAUSES 21500215

#define LINE_LENGTH 10240


// Define a data structure for a literal in the SAT problem.
struct lit {
    int clause_num;		//clause num, begin with 0
    int var_num;		//variable num, begin with 1
    int sense;			//is 1 for true literals, 0 for false literals.
};

/*parameters of the instance*/
int     num_vars;		//var index from 1 to num_vars
int     num_clauses;	//clause index from 0 to num_clauses-1
int		max_clause_len;
int		min_clause_len;
int 	num_empty_clauses;

/* literal arrays */				
lit*	var_lit[MAX_VARS];				//var_lit[i][j] means the j'th literal of var i.
int		var_lit_count[MAX_VARS];        //amount of literals of each var
lit*	clause_lit[MAX_CLAUSES];		//clause_lit[i][j] means the j'th literal of clause i.
int		clause_lit_count[MAX_CLAUSES]; 	// amount of literals in each clause			
			
/* Information about the variables. */
int     score[MAX_VARS];				
long long		time_stamp[MAX_VARS];
int		conf_change[MAX_VARS];
int*	var_neighbor[MAX_VARS];
int		var_neighbor_count[MAX_VARS];

int		pscore[MAX_VARS];

int		cscc[MAX_VARS];


/* Information about the clauses */			
int     clause_weight[MAX_CLAUSES];		
int     sat_count[MAX_CLAUSES];			
int		sat_var[MAX_CLAUSES];
int		sat_var2[MAX_CLAUSES];

//unsat clauses stack
int		unsat_stack[MAX_CLAUSES];		//store the unsat clause number
int		unsat_stack_fill_pointer;
int		index_in_unsat_stack[MAX_CLAUSES];//which position is a clause in the unsat_stack

//variables in unsat clauses
int		unsatvar_stack[MAX_VARS];		
int		unsatvar_stack_fill_pointer;
int		index_in_unsatvar_stack[MAX_VARS];
int		unsat_app_count[MAX_VARS];		//a varible appears in how many unsat clauses

//configuration changed decreasing variables (score>0 and confchange=1)
int		goodvar_stack[MAX_VARS];		
int		goodvar_stack_fill_pointer;
int		already_in_goodvar_stack[MAX_VARS];

/* Information about solution */
int             cur_soln[MAX_VARS];	//the current solution, with 1's for True variables, and 0's for False variables

//cutoff
long long	max_tries = 4500000000000000000ll;
long long	max_flips = 4000000000ll;
volatile static long long	step;

void setup_datastructure();
void free_memory();
int build_instance(char *filename);
void build_neighbor_relation();

void free_memory()
{
	int i;
	for (i = 0; i < num_clauses; i++) 
	{
		delete[] clause_lit[i];
	}
	
	for(i=1; i<=num_vars; ++i)
	{
		delete[] var_lit[i];
		delete[] var_neighbor[i];
	}
}

/*
 * Read in the problem.
 */
int temp_lit[MAX_VARS]; //the max length of a clause can be MAX_VARS
int build_instance(const char *cnf_string)
{
	//char    line[LINE_LENGTH];
	//char    tempstr1[10];
	//char    tempstr2[10];
	
	string line, tempstr1, tempstr2;
	const char* c_line;
	int     cur_lit;
	int     i,j;
	int		v,c;//var, clause
	
	std::istringstream infile(cnf_string);
	if(infile==NULL) {
		cout << "NULL" << endl;
		return 0;
	}

	/*** build problem data structures of the instance ***/
	//infile.getline(line,LINE_LENGTH);
	//while (line[0] != 'p')
	//	infile.getline(line,LINE_LENGTH);

	//sscanf(line, "%s %s %d %d", tempstr1, tempstr2, &num_vars, &num_clauses);
	
	getline(infile, line);
	c_line = line.c_str();
	while(c_line[0] != 'p')
	{
		getline(infile, line);
		c_line = line.c_str();
	}
	
	istringstream input_line(line);
	input_line >> tempstr1 >> tempstr2 >> num_vars >> num_clauses;
	
	if(num_vars>=MAX_VARS || num_clauses>=MAX_CLAUSES)
	{
		cout<<"the size of instance exceeds out limitation, please enlarge MAX_VARS and (or) MAX_CLAUSES."<<endl;
		exit(1);
	}
	
	for (c = 0; c < num_clauses; c++) 
		clause_lit_count[c] = 0;
	for (v=1; v<=num_vars; ++v)
		var_lit_count[v] = 0;
		
	max_clause_len = 0;
	min_clause_len = num_vars;
	num_empty_clauses = 0;
		
	//Now, read the clauses, one at a time.
	for (c = 0; c < num_clauses; c++) 
	{
		infile>>cur_lit;

		while (cur_lit != 0) { 
			temp_lit[clause_lit_count[c]] = cur_lit;
			clause_lit_count[c]++;
		
			infile>>cur_lit;
		}
		
		clause_lit[c] = new lit[clause_lit_count[c]+1];
		
		for(i=0; i<clause_lit_count[c]; ++i)
		{
			clause_lit[c][i].clause_num = c;
			clause_lit[c][i].var_num = abs(temp_lit[i]);
			if (temp_lit[i] > 0) clause_lit[c][i].sense = 1;
				else clause_lit[c][i].sense = 0;
			
			var_lit_count[clause_lit[c][i].var_num]++;
		}
		clause_lit[c][i].var_num=0; 
		clause_lit[c][i].clause_num = -1;
		
		if(clause_lit_count[c] > max_clause_len)
			max_clause_len = clause_lit_count[c];
		else if(clause_lit_count[c] < min_clause_len)
			min_clause_len = clause_lit_count[c];
		
		if(clause_lit_count[c]==00)
		{
			num_empty_clauses++;
		}
	}
	
	//creat var literal arrays
	for (v=1; v<=num_vars; ++v)
	{
		var_lit[v] = new lit[var_lit_count[v]+1];
		var_lit_count[v] = 0;	//reset to 0, for build up the array
	}
	//scan all clauses to build up var literal arrays
	for (c = 0; c < num_clauses; ++c) 
	{
		for(i=0; i<clause_lit_count[c]; ++i)
		{
			v = clause_lit[c][i].var_num;
			var_lit[v][var_lit_count[v]] = clause_lit[c][i];
			++var_lit_count[v];
		}
	}
	for (v=1; v<=num_vars; ++v) //set boundary
		var_lit[v][var_lit_count[v]].clause_num=-1;

	build_neighbor_relation();
	
	//problem type
	if(max_clause_len == min_clause_len)
	{
		if(max_clause_len<=3) probtype=SAT3;
		else if(max_clause_len<=4) probtype=SAT4;
		else if(max_clause_len<=5) probtype=SAT5;
		else if(max_clause_len<=6) probtype=SAT6;
		else if(max_clause_len<=7) probtype=SAT7;
		else probtype=SATmore;
	}
	else {
		probtype=strSAT;
	}
	
	return 1;
}

int	neighbor_flag[MAX_VARS];
int temp_neighbor[MAX_VARS];
int temp_neighbor_count;
void build_neighbor_relation()
{
	//int*	neighbor_flag = new int[num_vars+1];
	int	i,j,count;
	int 	v,c;

	for(v=1; v<=num_vars; ++v)
	{
		var_neighbor_count[v] = 0;

		//for(i=1; i<=num_vars; ++i)
		//	neighbor_flag[i] = 0;
		neighbor_flag[v] = 1;
		temp_neighbor_count = 0;
		
		for(i=0; i<var_lit_count[v]; ++i)
		{
			c = var_lit[v][i].clause_num;
			for(j=0; j<clause_lit_count[c]; ++j)
			{
				if(neighbor_flag[clause_lit[c][j].var_num]==0)
				{
					var_neighbor_count[v]++;
					neighbor_flag[clause_lit[c][j].var_num] = 1;
					temp_neighbor[temp_neighbor_count++] = clause_lit[c][j].var_num;
				}
			}
		}

		neighbor_flag[v] = 0;
 
		var_neighbor[v] = new int[var_neighbor_count[v]+1];

		count = 0;
		for(i=0; i<temp_neighbor_count; i++)
		{
			var_neighbor[v][count++] = temp_neighbor[i];
			neighbor_flag[temp_neighbor[i]] = 0;
		}
		/*
		for(i=1; i<=num_vars; ++i)
		{
			if(neighbor_flag[i]==1)
			{
				var_neighbor[v][count] = i;
				count++;
			}
		}
		*/
		var_neighbor[v][count]=0;
	}
	
	//delete[] neighbor_flag; neighbor_flag=NULL;
}


void print_solution()
{
     int    i;

     cout<<"v ";
     for (i=1; i<=num_vars; i++) {
         if(cur_soln[i]==0) cout<<"-";
         cout<<i;
         if(i%10==0) cout<<endl<<"v ";
         else	cout<<' ';
     }
     cout<<"0"<<endl;
}

int* export_solution() {
	int* solution = new int[num_vars + 1];
	solution[0] = num_vars;
	int i;
	for (i = 1; i <= num_vars; i++) {
		solution[i] = cur_soln[i];
	}
	return solution;
}

int verify_sol()
{
	int c,j; 
	int flag;
	
	for (c = 0; c<num_clauses; ++c) 
	{
		flag = 0;
		for(j=0; j<clause_lit_count[c]; ++j)
			if (cur_soln[clause_lit[c][j].var_num] == clause_lit[c][j].sense) {flag = 1; break;}

		if(flag ==0){//output the clause unsatisfied by the solution
			cout<<"clause "<<c<<" is not satisfied"<<endl;
			
			for(j=0; j<clause_lit_count[c]; ++j)
			{
				if(clause_lit[c][j].sense==0)cout<<"-";
				cout<<clause_lit[c][j].var_num<<" ";
			}
			cout<<endl;
			
			for(j=0; j<clause_lit_count[c]; ++j)
				cout<<cur_soln[clause_lit[c][j].var_num]<<" ";
			cout<<endl;

			return 0;
		}
	}
	return 1;
}

#endif

