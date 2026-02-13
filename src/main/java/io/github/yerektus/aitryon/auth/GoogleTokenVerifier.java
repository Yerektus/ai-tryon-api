package io.github.yerektus.aitryon.auth;

public interface GoogleTokenVerifier {
    GoogleIdentity verify(String idToken);
}
