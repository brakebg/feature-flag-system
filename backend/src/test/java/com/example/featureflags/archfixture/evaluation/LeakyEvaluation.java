package com.example.featureflags.archfixture.evaluation;

import com.example.featureflags.group.FlagGroupRepository;

/** Fixture: evaluation code reading the database outside a cache loader. */
public class LeakyEvaluation {

  private final FlagGroupRepository repository;

  public LeakyEvaluation(FlagGroupRepository repository) {
    this.repository = repository;
  }

  public long serve() {
    return repository.count();
  }
}
