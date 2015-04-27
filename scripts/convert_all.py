# coding: utf-8
for i in range(len(files)):
   file_name = files[i]
   name = names[i] + "_EVERYTHING.csv"
   get_ipython().system(u'python /ubc/cs/home/n/newmanne/scripts/replaceTAEInstanceName.py -cnf_to_srpk_index /ubc/cs/home/n/newmanne/NEW_MAILBOX/validationCNFReverseIndex.txt -input $file_name -output $name')
   full_instance_file = names[i] + "_FULL_INSTANCES.csv"
   get_ipython().system(u'cat $name | grep -v "component" > $full_instance_file')
   n = names[i]
   get_ipython().system(u'python /ubc/cs/home/n/newmanne/scripts/components.py -csv $name -out $n')
