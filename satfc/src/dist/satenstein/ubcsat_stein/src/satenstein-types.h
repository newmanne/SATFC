//
// Created by pcernek on 6/3/15.
//

#ifndef SATENSTEIN_SATENSTEIN_TYPES_H
#define SATENSTEIN_SATENSTEIN_TYPES_H

typedef enum {
  UPDATE_G2WSAT_1 = 1,
  UPDATE_G2WSAT_2,
  UPDATE_GNOVELTYPLUS,
} PromListUpdateScheme;

typedef enum {
  PICK_FREEBIE = 1,
  PICK_BESTSCORE,
  PICK_OLDEST,
  PICK_BEST_VW1,
  PICK_BEST_VW2, // 5
  PICK_RANDOM,
  PICK_NOVELTY,
  PICK_NOVELTYPLUSPLUS,
  PICK_NOVELTYPLUS,
  PICK_NOVELTYPLUSPLUSPRIME, // 10
  PICK_NOVELTYPLUSP,
  PICK_GNOVELTYPLUS,
  PICK_DCCA // 13
} DecPromVarPickingStrategy;

typedef enum {
  H_PICK_NOVELTY = 1,
  H_PICK_NOVELTYPLUS,
  H_PICK_NOVELTYPLUSPLUS,
  H_PICK_NOVELTYPLUSPLUSPRIME,
  H_PICK_RNOVELTY, // 5
  H_PICK_RNOVELTYPLUS,
  H_PICK_VW1,
  H_PICK_VW2,
  H_PICK_WALKSAT,
  H_PICK_NOVELTY_PROMISING, //10
  H_PICK_NOVELTYPLUS_PROMISING,
  H_PICK_NOVELTYPLUSPLUS_PROMISING,
  H_PICK_NOVELTYPLUSPLUSPRIME_PROMISING,
  H_PICK_NOVELTYPLUSFC,
  H_PICK_NOVELTYPLUSPROMISINGFC, // 15
  H_PICK_RANDOMPROB,
  H_PICK_NOVELTYSATTIME,
  H_PICK_NOVELTYPLUSSATTIME,
  H_PICK_SPARROWPROBDIST,
  H_PICK_DCCA_DIVERSIFY  // 20
} Heuristic;

typedef enum {
  SMOOTH_SAPS = 1,
  SMOOTH_PAWS,
} SmoothingScheme;

#endif //SATENSTEIN_SATENSTEIN_TYPES_H