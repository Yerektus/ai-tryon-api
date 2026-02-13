package io.github.yerektus.aitryon.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import io.github.yerektus.aitryon.common.BadRequestException;
import io.github.yerektus.aitryon.common.UnauthorizedException;
import io.github.yerektus.aitryon.config.GoogleProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

    private final GoogleProperties googleProperties;

    public GoogleTokenVerifierImpl(GoogleProperties googleProperties) {
        this.googleProperties = googleProperties;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        final List<String> audiences = googleProperties.getAllowedClientIds();
        if (audiences == null || audiences.isEmpty() || (audiences.size() == 1 && audiences.get(0).isBlank())) {
            throw new BadRequestException("Google OAuth is not configured");
        }

        final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();

        try {
            final GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new UnauthorizedException("Invalid Google token");
            }

            final GoogleIdToken.Payload payload = token.getPayload();
            final String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Google token does not include email");
            }

            final Object emailVerified = payload.get("email_verified");
            if (emailVerified instanceof Boolean verified && !verified) {
                throw new UnauthorizedException("Google email is not verified");
            }

            final String subject = payload.getSubject();
            final String name = (String) payload.get("name");
            return new GoogleIdentity(subject, email, name == null ? email : name);
        } catch (GeneralSecurityException | IOException e) {
            throw new UnauthorizedException("Unable to verify Google token");
        }
    }
}
