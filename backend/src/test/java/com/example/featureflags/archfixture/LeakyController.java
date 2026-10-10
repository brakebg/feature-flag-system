package com.example.featureflags.archfixture;

import com.example.featureflags.group.FlagGroup;
import com.example.featureflags.group.FlagGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/** Fixture that breaks the controller and injection rules (ArchitectureRulesCanFailTest). */
@RestController
public class LeakyController {

  @Autowired FlagGroupRepository repository;

  public FlagGroup get() {
    return repository.findAll().get(0);
  }
}
