package io.github.yerektus.aitryon.social;

import io.github.yerektus.aitryon.domain.SocialLookEntity;
import io.github.yerektus.aitryon.domain.SocialLookVisibility;
import io.github.yerektus.aitryon.domain.repo.SocialFollowRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SocialVisibilityPolicy {

    private final SocialFollowRepository socialFollowRepository;

    public SocialVisibilityPolicy(SocialFollowRepository socialFollowRepository) {
        this.socialFollowRepository = socialFollowRepository;
    }

    public boolean canViewLook(SocialLookEntity look, UUID viewerId) {
        final UUID authorId = look.getAuthor().getId();
        if (authorId.equals(viewerId)) {
            return true;
        }

        if (look.getVisibility() == SocialLookVisibility.PUBLIC) {
            return true;
        }

        return look.getVisibility() == SocialLookVisibility.FOLLOWERS
                && socialFollowRepository.existsByFollower_IdAndFollowee_Id(viewerId, authorId);
    }
}
