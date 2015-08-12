cmake_minimum_required(VERSION 2.8)
project(SATenstein)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -lm -Wall -Wno-unused-variable -Wno-unused-but-set-variable -Wno-comment")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

include_directories( src )

set(SOURCE_FILES
                    src/adaptg2wsatplus.c
                    src/adaptnovelty.c
                    src/algorithms.c
                    src/derandomized.c
                    src/ddfw.c
                    src/satenstein.c
                    src/gsat.c
                    src/gsat-tabu.c
                    src/gwsat.c
                    src/hsat.c
                    src/hwsat.c
                    src/irots.c
                    src/mt19937ar.c
                    src/mylocal.c
                    src/novelty.c
                    src/parameters.c
                    src/paws.c
                    src/random.c
                    src/reports.c
                    src/rgsat.c
                    src/rnovelty.c
                    src/rots.c
                    src/samd.c
                    src/saps.c
                    src/ubcsat-help.c
                    src/ubcsat-internal.c
                    src/ubcsat-io.c
                    src/ubcsat-mem.c
                    src/ubcsat-reports.c
                    src/ubcsat-time.c
                    src/ubcsat-triggers.c
                    src/vw.c
                    src/walksat.c
                    src/walksat-tabu.c

                    src/dcca.c
                    src/satenstein-types.h
                    src/sparrow.c
)

include_directories( test )

set(TEST_FILES ${SOURCE_FILES}
# For now, one of these lines must be uncommented for tests to be run.
# TODO: Make it so that all these tests can be run simultaneously.
#                test/AddRemoveListsTest.cpp
#                test/DccaTest.cpp
                test/JNATest.cpp
                )


# Add google test framework
ADD_SUBDIRECTORY(lib/gtest-1.7.0 gtest)
include_directories(gtest STATIC lib/gtest-1.7.0/include )

add_executable(ubcsat src/ubcsat.c ${SOURCE_FILES})
add_executable(runTests ${TEST_FILES} test/TestRunner.cpp)
add_library(jnaubcsat SHARED src/jna-ubcsat.cpp ${SOURCE_FILES})

target_link_libraries(runTests gtest jnaubcsat)
