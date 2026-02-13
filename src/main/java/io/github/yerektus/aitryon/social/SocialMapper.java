package io.github.yerektus.aitryon.social;

import io.github.yerektus.aitryon.domain.SocialCommentEntity;
import io.github.yerektus.aitryon.domain.SocialLookDraftEntity;
import io.github.yerektus.aitryon.domain.SocialLookEntity;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.social.dto.SocialCommentResponse;
import io.github.yerektus.aitryon.social.dto.SocialLookDraftResponse;
import io.github.yerektus.aitryon.social.dto.SocialLookResponse;
import io.github.yerektus.aitryon.social.dto.SocialProfileResponse;
import io.github.yerektus.aitryon.social.dto.SocialUserResponse;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class SocialMapper {

    public SocialUserResponse toSocialUser(UserEntity user) {
        return new SocialUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }

    public SocialProfileResponse toSocialProfile(UserEntity user,
                                                 long followersCount,
                                                 long followingCount,
                                                 long looksCount,
                                                 boolean isFollowing,
                                                 UUID viewerId) {
        return new SocialProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBio(),
                followersCount,
                followingCount,
                looksCount,
                isFollowing,
                user.getId().equals(viewerId)
        );
    }

    public SocialLookResponse toSocialLook(SocialLookEntity look,
                                           List<String> tags,
                                           long likesCount,
                                           long commentsCount,
                                           boolean isLikedByMe) {
        return new SocialLookResponse(
                look.getId(),
                toSocialUser(look.getAuthor()),
                toDataUri(look.getImageData(), look.getImageMime()),
                look.getTitle(),
                look.getDescription(),
                tags,
                look.getStyle(),
                look.getVisibility().toApiValue(),
                likesCount,
                commentsCount,
                isLikedByMe,
                look.getCreatedAt()
        );
    }

    public SocialCommentResponse toSocialComment(SocialCommentEntity comment,
                                                 long repliesCount,
                                                 boolean canDelete) {
        return new SocialCommentResponse(
                comment.getId(),
                comment.getLook().getId(),
                toSocialUser(comment.getAuthor()),
                comment.getBody(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getCreatedAt(),
                repliesCount,
                false,
                0,
                canDelete
        );
    }

    public SocialLookDraftResponse toSocialLookDraft(SocialLookDraftEntity draft, List<String> tags) {
        final String dataUri = toDataUri(draft.getImageData(), draft.getImageMime());
        return new SocialLookDraftResponse(
                draft.getTitle(),
                draft.getDescription(),
                tags,
                draft.getStyle(),
                draft.getVisibility().toApiValue(),
                dataUri,
                dataUri,
                draft.getUpdatedAt()
        );
    }

    private String toDataUri(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        final String mime = (mimeType == null || mimeType.isBlank()) ? "image/jpeg" : mimeType;
        final String payload = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mime + ";base64," + payload;
    }
}
