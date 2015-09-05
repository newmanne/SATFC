#include <string>
#include <sstream>
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
using namespace std;

char *inst;
int seed;

int total_time;
double sls_time_ratio;
int sls_time;

int parse_parameters(int argc, char* argv[])
{
	int i;
	string cur_str;
	
	if(argc==1)
	{
		return 0;
	}
	
	for(i=1; i<argc; i++)
	{
		cur_str = argv[i];
		if(cur_str.compare("-inst")==0)
		{
			i++;
			inst = argv[i];
		}
		else if(cur_str.compare("-seed")==0)
		{
			i++;
			sscanf(argv[i], "%d", &seed);
		}
		else if(cur_str.compare("-sls_time_ratio")==0)
		{
			i++;
			sscanf(argv[i], "%lf", &sls_time_ratio);
		}
		else
		{
			return 0;
		}
	}
	return 1;
}



int main(int argc, char **argv)
{
	printf("c this is DCCASat+march_rw [Version: CSSC2014]\n");
	printf("c for random instances\n");
	printf("c many thanks to the march_rw team!\n");
	total_time = 300;
	sls_time_ratio = 0.5;
	
	int ret = parse_parameters(argc, argv);
	if(ret==0)
	{
		printf("c Invalid parameters\n");
		printf("c Usage: %s -inst <inst> -seed <seed> -sls_time_ratio <sls_time_ratio>\n", argv[0]);
		return -1;
	}
	
	sls_time = total_time*sls_time_ratio;
	
	string command = "";
	string inst_str = inst;
	string seed_str;
	string sls_time_str;
	
	stringstream sstream;
	sstream.str("");
	sstream.clear();
	sstream<<seed;
	sstream>>seed_str;
	sstream.str("");
	sstream.clear();
	sstream<<sls_time;
	sstream>>sls_time_str;
	
	command = "solvers/DCCASat+march_rw/DCCASat " + inst_str + " " + seed_str + " " + sls_time_str;
	printf("c start DCCASat\n");
	int status = system(command.c_str());
	
	int dccasat_ret = WEXITSTATUS(status);
	
	if(dccasat_ret!=0)
	{
		command = "solvers/DCCASat+march_rw/march_rw " + inst_str;
		printf("c start march_rw\n");
		status = system(command.c_str());
	}
	
	
	return 0;
}

