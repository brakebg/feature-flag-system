package com.example.featureflags.auth;

import jakarta.validation.constraints.NotBlank;

/** Spec 5.2 login body. The password is never part of {@code toString()}. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {

  @Override
  public String toString() {
    return "LoginRequest[username=" + username + "]";
  }
}
