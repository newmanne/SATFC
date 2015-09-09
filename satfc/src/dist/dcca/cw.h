#ifndef _CW_H_
#define _CW_H_

#include "basis.h"

#define sigscore	ave_weight   //significant score needed for aspiration

int		ave_weight=1;
int		delta_total_weight=0;

/**************************************** clause weighting for 3sat **************************************************/

int		threshold;
float		p_scale=0.3;//w=w*p+ave*(1-p)
float		q_scale=0.7;//q=1-p 
int		scale_ave;//scale_ave==ave_weight*q_scale

void smooth_clause_weights_3sat()
{
	int i,j,c,v;
	int new_total_weight=0;

	for (v=1; v<=num_vars; ++v) 
		score[v] = 0;
	
	//smooth clause score and update score of variables
	for (c = 0; c<num_clauses; ++c)
	{
		clause_weight[c] = clause_weight[c]*p_scale+scale_ave;
		
		new_total_weight+=clause_weight[c];
		
		//update score of variables in this clause 
		if (sat_count[c]==0) 
		{
			for(j=0; j<clause_lit_count[c]; ++j)
			{
				score[clause_lit[c][j].var_num] += clause_weight[c];
			}
		}
		else  if(sat_count[c]==1)
			score[sat_var[c]]-=clause_weight[c];
	}
	
	ave_weight=new_total_weight/num_clauses;
}

void update_clause_weights_3sat()
{
	int i,v;

	for(i=0; i < unsat_stack_fill_pointer; ++i)
		clause_weight[unsat_stack[i]]++;
	
	for(i=0; i<unsatvar_stack_fill_pointer; ++i)
	{
		v = unsatvar_stack[i];
		score[v] += unsat_app_count[v];
		if(score[v]>0 &&  conf_change[v]==1 && already_in_goodvar_stack[v] ==0)
		{
			push(v,goodvar_stack);
			already_in_goodvar_stack[v] =1;
		}
	}
	
	delta_total_weight+=unsat_stack_fill_pointer;
	if(delta_total_weight>=num_clauses)
	{
		ave_weight+=1;
		delta_total_weight -= num_clauses;
		
		//smooth weights
		if(ave_weight>threshold)
			smooth_clause_weights_3sat();
	}
}



/**********************************clause weighting for large ksat, k>3*************************************************/
const int	  dec_weight =1;
const float       MY_RAND_MAX_FLOAT = 10000000.0;
const int   	  MY_RAND_MAX_INT =   10000000;
const float 	BASIC_SCALE = 0.0000001; //1.0f/MY_RAND_MAX_FLOAT;
float  smooth_probability;
float  smooth_probability2;
int    large_clause_count_threshold;

//for PAWS (for large ksat)
int            large_weight_clauses[MAX_CLAUSES];
int            large_weight_clauses_count=0;	


void inc_clause_weights_large_sat()
{
	int i, j, c, v;
	
	for(i=0; i < unsat_stack_fill_pointer; ++i)
	{
		c = unsat_stack[i];
		clause_weight[c]++;
		if(clause_weight[c] == 2)
		{
			large_weight_clauses[large_weight_clauses_count++] = c;
		}
	}
	
	for(i=0; i<unsatvar_stack_fill_pointer; ++i)
	{
		v = unsatvar_stack[i];
		score[v] += unsat_app_count[v];
		if(score[v]>0 &&  conf_change[v]>0  && already_in_goodvar_stack[v] ==0)//
		{
			push(v,goodvar_stack);
			already_in_goodvar_stack[v] =1;
		}
	}

}

void smooth_clause_weights_large_sat()
{
	int i, j,clause, var;
	for(i=0; i<large_weight_clauses_count; i++)
	{
		clause = large_weight_clauses[i];
		if(sat_count[clause]>0)
		{
			clause_weight[clause]-=dec_weight;
				
			if(clause_weight[clause]==1)
			{
				large_weight_clauses[i] = large_weight_clauses[--large_weight_clauses_count];
				i--;
			}
			if(sat_count[clause] == 1)
			{
				var = sat_var[clause];
				score[var]+=dec_weight;
				if(score[var]>0 &&  conf_change[var]>0  && already_in_goodvar_stack[var]==0)
				{
					push(var,goodvar_stack);
					already_in_goodvar_stack[var]=1;
				}
			}
		}
	}
	
}

void update_clause_weights_large_sat()
{
	if( ((rand()%MY_RAND_MAX_INT)*BASIC_SCALE)<smooth_probability && large_weight_clauses_count>large_clause_count_threshold )
		smooth_clause_weights_large_sat();
	else 
		inc_clause_weights_large_sat();
}

/**************************setting clause weighting scheme*********************************************************/

void (* update_clause_weights)();

void set_clause_weighting()
{	
	if(probtype==SAT3)
	{
		update_clause_weights = update_clause_weights_3sat;
		threshold=200+(num_vars+250)/500;
		scale_ave=(threshold+1)*q_scale;//when smoothing, ave_weight=threshold+1.
	}
	else if(probtype==SAT4)
	{
		update_clause_weights = update_clause_weights_large_sat;
		smooth_probability = 0.75;
		large_clause_count_threshold = 10;
	}
	else if(probtype==SAT5)
	{
		update_clause_weights = update_clause_weights_large_sat;
		smooth_probability = 0.8;
		//if(num_vars>=1500) smooth_probability = 0.725;
		large_clause_count_threshold = 10;
	}
	else if(probtype==SAT6)
	{
		update_clause_weights = update_clause_weights_large_sat;
		smooth_probability = 0.9;
		large_clause_count_threshold = 10;
	}
	else if(probtype==SAT7 || probtype==SATmore)
	{
		update_clause_weights = update_clause_weights_large_sat;
		smooth_probability = 0.92;
		large_clause_count_threshold = 10;
	}
	else //non k-SAT
	{
		update_clause_weights = update_clause_weights_3sat;
		threshold=300;
		scale_ave=(threshold+1)*q_scale;
	}
}

#endif
