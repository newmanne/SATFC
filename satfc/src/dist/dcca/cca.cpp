#include "basis.h"
#include "cca.h"
#include "cw.h"

#include <sys/times.h> //these two h files are for linux
#include <unistd.h>

volatile bool is_interrupted = false;

int Gamma = 1000;
int Beta = 1000;
int para_d = 6;

int cutoff_time = 5000;
struct tms start, stop;

const double Threshold_ratio_SAT3_left = 4.24;
const double Threshold_ratio_SAT3_right = 4.294;
const double Threshold_ratio_SAT4_left = 9.8;
const double Threshold_ratio_SAT4_right = 10.062;
const double Threshold_ratio_SAT5_left = 20.5;
const double Threshold_ratio_SAT5_right = 21.734;
const double Threshold_ratio_SAT6_left = 42;
const double Threshold_ratio_SAT6_right = 44.74;
const double Threshold_ratio_SAT7_left = 86;
const double Threshold_ratio_SAT7_right = 89.58;

//int delta_age;

int pick_var_large()
{
	int         i,k,c,v;
	int         best_var;
	lit*		clause_c;
	
	int hscore_v;
	int hscore_best_var;
	
	/**Greedy Mode**/
	
	/*CCD (configuration changed decreasing) mode, the level with configuation chekcing*/
	if(goodvar_stack_fill_pointer>0)
	{
	
		best_var = -1;
		for(i=0; i<goodvar_stack_fill_pointer; i++)
		{
			v = goodvar_stack[i];
			if(cscc[v]==1)
			{
				best_var = v;
				break;
			}
		}
		
		if(best_var!=-1)
		{
			for(i++; i<goodvar_stack_fill_pointer; i++)
			{
				v = goodvar_stack[i];
				if(cscc[v]==0)
					continue;
				if(score[v]>score[best_var])
					best_var = v;
				else if(score[v]==score[best_var])
				{
					if(pscore[v]+(step-time_stamp[v])/Gamma > pscore[best_var]+(step-time_stamp[best_var])/Gamma)
						best_var = v;
					else if(pscore[v]+(step-time_stamp[v])/Gamma < pscore[best_var]+(step-time_stamp[best_var])/Gamma)
						continue;
					else if(time_stamp[v]<time_stamp[best_var])
						best_var = v;
				}
			}
			
			return best_var;
		}
	
		best_var = goodvar_stack[0];
		
		for(i=1; i<goodvar_stack_fill_pointer; ++i)
		{
			v=goodvar_stack[i];
			if(score[v]>score[best_var]) best_var = v;
	
			else if(score[v]==score[best_var])
			{
				if(pscore[v]+(step-time_stamp[v])/Gamma > pscore[best_var]+(step-time_stamp[best_var])/Gamma)
					best_var = v;
				else if(pscore[v]+(step-time_stamp[v])/Gamma < pscore[best_var]+(step-time_stamp[best_var])/Gamma)
					continue;
				else if(time_stamp[v]<time_stamp[best_var])
					best_var = v;
			}
		}
		
		return best_var;
	}
	
	/*SD (significant decreasing) mode, the level with aspiration*/
	best_var = 0;
	for(i=0; i<unsatvar_stack_fill_pointer; ++i)
	{
		if(score[unsatvar_stack[i]]>sigscore) 
		{
			best_var = unsatvar_stack[i];
			break;
		}
	}

	for(++i; i<unsatvar_stack_fill_pointer; ++i)
	{
		v=unsatvar_stack[i];
		if(score[v]>score[best_var]) best_var = v;
		else if(score[v]==score[best_var])
		{
			if(pscore[v]+(step-time_stamp[v])/Gamma > pscore[best_var]+(step-time_stamp[best_var])/Gamma)
				best_var = v;
			else if(pscore[v]+(step-time_stamp[v])/Gamma < pscore[best_var]+(step-time_stamp[best_var])/Gamma)
				continue;
			else if(time_stamp[v]<time_stamp[best_var])
				best_var = v;
		}
	}
		
	if(best_var!=0) return best_var;
		
	/**Diversification Mode**/

	update_clause_weights();
	
	/*focused random walk*/
	c = unsat_stack[rand()%unsat_stack_fill_pointer];
	clause_c = clause_lit[c];
	best_var = clause_c[0].var_num;
	hscore_best_var = score[best_var]+pscore[best_var]/para_d+(step-time_stamp[best_var])/Beta;
	for(k=1; k<clause_lit_count[c]; ++k)
	{
		v=clause_c[k].var_num;
		hscore_v = score[v]+pscore[v]/para_d+(step-time_stamp[v])/Beta;
		if(hscore_v>hscore_best_var)
		{
			best_var = v;
			hscore_best_var = hscore_v;
		}
		else if(hscore_v<hscore_best_var)
			continue;
		else if(time_stamp[v]<time_stamp[best_var])
			best_var = v;
	}
	
	return best_var;
}

//pick a var to be flip
int pick_var_3SAT()
{
	int         i,k,c,v;
	int         best_var;
	lit*		clause_c;
	
	/**Greedy Mode**/
	/*CCD (configuration changed decreasing) mode, the level with configuation chekcing*/
	if(goodvar_stack_fill_pointer>0)
	{
	
		best_var = -1;
		for(i=0; i<goodvar_stack_fill_pointer; i++)
		{
			v = goodvar_stack[i];
			if(cscc[v]==1)
			{
				best_var = v;
				break;
			}
		}
		
		if(best_var!=-1)
		{
			for(i++; i<goodvar_stack_fill_pointer; i++)
			{
				v = goodvar_stack[i];
				if(cscc[v]==0)
					continue;
				if(score[v]>score[best_var]) best_var = v;
				else if(score[v]==score[best_var] && time_stamp[v]<time_stamp[best_var]) best_var = v;
			}
			return best_var;
		}
	
		best_var = goodvar_stack[0];
		
		for(i=1; i<goodvar_stack_fill_pointer; ++i)
		{
			v=goodvar_stack[i];
			if(score[v]>score[best_var]) best_var = v;
			else if(score[v]==score[best_var] && time_stamp[v]<time_stamp[best_var]) best_var = v;
		}
		
		return best_var;
	}
	
	/*SD (significant decreasing) mode, the level with aspiration*/
	best_var = 0;
	for(i=0; i<unsatvar_stack_fill_pointer; ++i)
	{
		if(score[unsatvar_stack[i]]>sigscore) 
		{
			best_var = unsatvar_stack[i];
			break;
		}
	}

	for(++i; i<unsatvar_stack_fill_pointer; ++i)
	{
		v=unsatvar_stack[i];
		if(score[v]>score[best_var]) best_var = v;
		else if(score[v]==score[best_var] && time_stamp[v]<time_stamp[best_var]) best_var = v;
	}
		
	if(best_var!=0) return best_var;
		
	/**Diversification Mode**/

	update_clause_weights();
	
	/*focused random walk*/
	c = unsat_stack[rand()%unsat_stack_fill_pointer];
	clause_c = clause_lit[c];
	best_var = clause_c[0].var_num;
	for(k=1; k<clause_lit_count[c]; ++k)
	{
		v=clause_c[k].var_num;
		if(time_stamp[v]<time_stamp[best_var]) best_var = v;
	}
	
	return best_var;
}


int (* pick_var) ();


//set functions in the algorithm
void set_functions()
{
	set_clause_weighting();
	
	if(probtype==SAT3||probtype==strSAT)
	{
		flip = flip_3SAT;
		pick_var = pick_var_3SAT;
	}
	else //large ksat
	{
		flip = flip_large;
		pick_var = pick_var_large;
		
		para_d = 13-max_clause_len;
		if(para_d<6) para_d = 6;
		/*
		if(probtype==SAT5)
		{
			delta_age=2*num_vars;
			if(delta_age>2000)delta_age=2000;
		}
		else {
			delta_age=20*num_vars;
			if(delta_age>2500)delta_age=2500;
		}
		*/
	}
}

void set_cutoff()
{
	double cnf_ratio = (double)num_clauses/num_vars;
	double tmp_cutoff_time = cutoff_time;
	
	if(probtype==SAT3)
	{
		if(cnf_ratio<Threshold_ratio_SAT3_left)
		{
			cutoff_time = tmp_cutoff_time*0.9+1;
		}
		else if(cnf_ratio>Threshold_ratio_SAT3_right)
		{
			cutoff_time = tmp_cutoff_time*0.1+1;
		}
		else
		{
			cutoff_time = tmp_cutoff_time*0.5+1;
		}
	}
	else if(probtype==SAT4)
	{
		if(cnf_ratio<Threshold_ratio_SAT4_left)
		{
			cutoff_time = tmp_cutoff_time*0.9+1;
		}
		else if(cnf_ratio>Threshold_ratio_SAT4_right)
		{
			cutoff_time = tmp_cutoff_time*0.1+1;
		}
		else
		{
			cutoff_time = tmp_cutoff_time*0.5+1;
		}
	}
	else if(probtype==SAT5)
	{
		if(cnf_ratio<Threshold_ratio_SAT5_left)
		{
			cutoff_time = tmp_cutoff_time*0.9+1;
		}
		else if(cnf_ratio>Threshold_ratio_SAT5_right)
		{
			cutoff_time = tmp_cutoff_time*0.1+1;
		}
		else
		{
			cutoff_time = tmp_cutoff_time*0.5+1;
		}
	}
	else if(probtype==SAT6)
	{
		if(cnf_ratio<Threshold_ratio_SAT6_left)
		{
			cutoff_time = tmp_cutoff_time*0.9+1;
		}
		else if(cnf_ratio>Threshold_ratio_SAT6_right)
		{
			cutoff_time = tmp_cutoff_time*0.1+1;
		}
		else
		{
			cutoff_time = tmp_cutoff_time*0.5+1;
		}
	}
	else if(probtype==SAT7)
	{
		if(cnf_ratio<Threshold_ratio_SAT7_left)
		{
			cutoff_time = tmp_cutoff_time*0.9+1;
		}
		else if(cnf_ratio>Threshold_ratio_SAT7_right)
		{
			cutoff_time = tmp_cutoff_time*0.1+1;
		}
		else
		{
			cutoff_time = tmp_cutoff_time*0.5+1;
		}
	}
	else if(probtype==SATmore)
	{
		cutoff_time = tmp_cutoff_time*0.5+1;
	}
	else
	{
		cutoff_time = tmp_cutoff_time*0.5+1;
	}
	
	printf("c cutoff = %d\n", cutoff_time);
}

void local_search()
{
	int flipvar, j;
    for (step = 0; step<max_flips; )
	{
		for(j=0; j<10000; j++, step++)
		{
			if(unsat_stack_fill_pointer==0) return;
			flipvar = pick_var();
			flip(flipvar);
			time_stamp[flipvar] = step;
		}
		times(&stop);
		double elap_time = (stop.tms_utime - start.tms_utime +stop.tms_stime - start.tms_stime) / sysconf(_SC_CLK_TCK);
		if(elap_time >= cutoff_time || is_interrupted)return;
	}
}

// JNA Library
extern "C" {
	void initProblem(const char* problem, int aSeed) {
		if (build_instance(problem)==0)
		{
			cout<<"Can't parse problem!"<< endl;
			return;
		}

	    srand(aSeed);
	    
		set_functions();
		
		cout<<"c start searching"<<endl;

	}
	int* solveProblem(long* prevAssign, long prev_assignment_size, double aCutoff_time) {
		times(&start);
		cutoff_time = (int) (aCutoff_time * 1000);

		long long i;
		for (i = 0; i <= max_tries; i++) 
		{
			 init(prevAssign, prev_assignment_size);
			 
			 times(&stop);
		 
			 local_search();

			 if (unsat_stack_fill_pointer==0) break;
			 
			 times(&stop);
			 double elap_time = (stop.tms_utime - start.tms_utime +stop.tms_stime - start.tms_stime) / sysconf(_SC_CLK_TCK);
			 if(elap_time >= cutoff_time) break;
		}
		// print_solution();
		return export_solution();
	}
	void destroyProblem() {
    	free_memory();
	}
	bool interrupt(void* jnaProblemPointer) {
		is_interrupted = true;
	}
	int getResultState(void* jnaProblemPointer);
	int* getResultAssignment(void* jnaProblemPointer);
}

// int main(int argc, char* argv[])
// {
// 	int     seed;
// 	int		ret = 1;
// 	long long i;
// 	cout<<"c this is DCCASat [Version: CSSC2014 Random_With_Cutoff]" <<endl;
// 	cout<<"c for random instances" <<endl;
    
//     if(!(argc==4 || argc==5))
//     {
//     	cout<<"c Usage: "<<argv[0] << " <instance> <seed> <cutoff_time> (<assignment>)" <<endl;
//     	return 1;
//     }
     
// 	times(&start);

// 	if (build_instance(argv[1])==0)
// 	{
// 		cout<<"c Invalid filename: "<< argv[1]<<endl;
// 		return 1;
// 	}
	
// 	if(num_clauses==0)
// 	{
// 		cout<<"s SATISFIABLE"<<endl;
// 		print_solution();
// 		free_memory();
// 		return 0;
// 	}
	
// 	if(num_empty_clauses>0)
// 	{
// 		cout<<"s UNSATISFIABLE"<<endl;
// 		free_memory();
// 		return 0;
// 	}
     
//     sscanf(argv[2],"%d",&seed);
    
//     sscanf(argv[3],"%d",&cutoff_time);
    
//     srand(seed);
    
//     //set_cutoff();
    
// 	set_functions();
	
// 	cout<<"c cutoff_time = "<<cutoff_time<<endl;
// 	cout<<"c start searching"<<endl;
// 	//cout<<"c smooth_probability = "<<smooth_probability<<endl;
	
// 	//sscanf(argv[3],"%f", &smooth_probability);

// 	for (i = 0; i <= max_tries; i++) 
// 	{
// 		 init(argc == 5 ? argv[4] : NULL);
		 
// 		 times(&stop);
	 
// 		 local_search();

// 		 if (unsat_stack_fill_pointer==0) break;
		 
// 		 times(&stop);
// 		 double elap_time = (stop.tms_utime - start.tms_utime +stop.tms_stime - start.tms_stime) / sysconf(_SC_CLK_TCK);
// 		 if(elap_time >= cutoff_time) break;
// 	}

// 	times(&stop);
// 	double comp_time = double(stop.tms_utime - start.tms_utime +stop.tms_stime - start.tms_stime) / sysconf(_SC_CLK_TCK);

// 	if(unsat_stack_fill_pointer==0)
// 	{
// 		if(verify_sol()==1)
// 		{
// 			cout<<"s SATISFIABLE"<<endl;
// 			print_solution();
//             ret = 0;
//         }
//         else 
//         {
//         	cout<<"c Sorry, something is wrong."<<endl;
//         	ret = 1;
//         }
//     }
//     else  
//     {
//     	cout<<"c UNKNOWN"<<endl;
//     	ret = 1;
//     }
    
//     cout<<"c solveSteps = "<<i<<" tries + "<<step<<" steps (each try has " << max_flips << " steps)."<<endl;
//     cout<<"c solveTime = "<<comp_time<<endl;
	 
//     free_memory();

//     return ret;
// }
