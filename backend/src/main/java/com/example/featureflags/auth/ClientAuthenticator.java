package com.example.featureflags.auth;

import java.util.Optional;

/** Spec 5.1: checks client credentials; replaceable by a real client store later. */
public interface ClientAuthenticator {

  Optional<ClientRegistration> authenticate(String clientId, String clientSecret);
}
