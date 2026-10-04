package com.example.featureflags.auth;

/** Spec 5.1, 13: checks admin credentials; replaceable by a real user store later. */
public interface AdminAuthenticator {

  boolean authenticate(String username, String password);
}
