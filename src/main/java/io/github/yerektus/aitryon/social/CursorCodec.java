package io.github.yerektus.aitryon.social;

import io.github.yerektus.aitryon.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class CursorCodec {

    public String encodePage(int page) {
        final String payload = "p:" + page;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public int decodePage(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }

        try {
            final String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith("p:")) {
                throw new BadRequestException("Invalid cursor");
            }

            final int page = Integer.parseInt(decoded.substring(2));
            if (page < 0) {
                throw new BadRequestException("Invalid cursor");
            }
            return page;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid cursor");
        }
    }
}
