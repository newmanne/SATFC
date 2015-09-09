all: DCCASat clean

DCCASat: cca.cpp cca.h basis.h cw.h
	g++ -O3 -fPIC -shared cca.cpp -o DCCASat

clean:
	rm -f *~

cleanup:
	rm -f DCCASat
	rm -f *~
