package com.example.featureflags.archfixture;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Breaks the Jackson 3 only rule on purpose (spec 002 AC-UPG-2); never used by production. */
public class Jackson2User {

  ObjectMapper mapper;
}
