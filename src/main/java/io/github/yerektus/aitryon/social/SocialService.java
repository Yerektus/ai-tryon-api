package io.github.yerektus.aitryon.social;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yerektus.aitryon.common.BadRequestException;
import io.github.yerektus.aitryon.common.ForbiddenException;
import io.github.yerektus.aitryon.common.NotFoundException;
import io.github.yerektus.aitryon.common.PayloadTooLargeException;
import io.github.yerektus.aitryon.domain.SocialCommentEntity;
import io.github.yerektus.aitryon.domain.SocialFollowEntity;
import io.github.yerektus.aitryon.domain.SocialFollowId;
import io.github.yerektus.aitryon.domain.SocialLookDraftEntity;
import io.github.yerektus.aitryon.domain.SocialLookEntity;
import io.github.yerektus.aitryon.domain.SocialLookLikeEntity;
import io.github.yerektus.aitryon.domain.SocialLookLikeId;
import io.github.yerektus.aitryon.domain.SocialLookVisibility;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.repo.SocialCommentRepository;
import io.github.yerektus.aitryon.domain.repo.SocialFollowRepository;
import io.github.yerektus.aitryon.domain.repo.SocialLookDraftRepository;
import io.github.yerektus.aitryon.domain.repo.SocialLookLikeRepository;
import io.github.yerektus.aitryon.domain.repo.SocialLookRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import io.github.yerektus.aitryon.social.dto.CursorPageResponse;
import io.github.yerektus.aitryon.social.dto.FollowRelationResponse;
import io.github.yerektus.aitryon.social.dto.SocialCommentResponse;
import io.github.yerektus.aitryon.social.dto.SocialLookDraftResponse;
import io.github.yerektus.aitryon.social.dto.SocialLookResponse;
import io.github.yerektus.aitryon.social.dto.SocialProfileResponse;
import io.github.yerektus.aitryon.social.dto.UpdateMySocialProfileRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SocialService {

    private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {
    };

    private final UserRepository userRepository;
    private final SocialFollowRepository socialFollowRepository;
    private final SocialLookRepository socialLookRepository;
    private final SocialLookLikeRepository socialLookLikeRepository;
    private final SocialCommentRepository socialCommentRepository;
    private final SocialLookDraftRepository socialLookDraftRepository;
    private final SocialVisibilityPolicy socialVisibilityPolicy;
    private final SocialMapper socialMapper;
    private final CursorCodec cursorCodec;
    private final ObjectMapper objectMapper;

    public SocialService(UserRepository userRepository,
                         SocialFollowRepository socialFollowRepository,
                         SocialLookRepository socialLookRepository,
                         SocialLookLikeRepository socialLookLikeRepository,
                         SocialCommentRepository socialCommentRepository,
                         SocialLookDraftRepository socialLookDraftRepository,
                         SocialVisibilityPolicy socialVisibilityPolicy,
                         SocialMapper socialMapper,
                         CursorCodec cursorCodec,
                         ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.socialFollowRepository = socialFollowRepository;
        this.socialLookRepository = socialLookRepository;
        this.socialLookLikeRepository = socialLookLikeRepository;
        this.socialCommentRepository = socialCommentRepository;
        this.socialLookDraftRepository = socialLookDraftRepository;
        this.socialVisibilityPolicy = socialVisibilityPolicy;
        this.socialMapper = socialMapper;
        this.cursorCodec = cursorCodec;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SocialProfileResponse fetchMyProfile(UUID viewerId) {
        return fetchProfile(viewerId, viewerId);
    }

    @Transactional(readOnly = true)
    public SocialProfileResponse fetchProfile(UUID viewerId, UUID profileId) {
        final UserEntity profile = getUser(profileId);
        return buildProfileResponse(profile, viewerId);
    }

    @Transactional
    public SocialProfileResponse updateMyProfile(UUID viewerId, UpdateMySocialProfileRequest request) {
        final UserEntity user = getUser(viewerId);

        if (request.displayName() != null) {
            final String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new BadRequestException("displayName cannot be blank");
            }
            user.setDisplayName(displayName);
        }

        if (request.bio() != null) {
            final String bio = request.bio().trim();
            user.setBio(bio.isEmpty() ? null : bio);
        }

        if (request.avatarUrl() != null) {
            final String avatarUrl = request.avatarUrl().trim();
            user.setAvatarUrl(avatarUrl.isEmpty() ? null : avatarUrl);
        }

        final UserEntity saved = userRepository.save(user);
        return buildProfileResponse(saved, viewerId);
    }

    @Transactional
    public FollowRelationResponse follow(UUID viewerId, UUID targetUserId) {
        if (viewerId.equals(targetUserId)) {
            throw new BadRequestException("Cannot follow yourself");
        }

        final UserEntity follower = getUser(viewerId);
        final UserEntity followee = getUser(targetUserId);

        final boolean exists = socialFollowRepository.existsByFollower_IdAndFollowee_Id(viewerId, targetUserId);
        if (!exists) {
            final SocialFollowEntity relation = new SocialFollowEntity();
            relation.setId(new SocialFollowId(viewerId, targetUserId));
            relation.setFollower(follower);
            relation.setFollowee(followee);
            socialFollowRepository.save(relation);
        }

        final long followersCount = socialFollowRepository.countByFollowee_Id(targetUserId);
        return new FollowRelationResponse(targetUserId, true, followersCount);
    }

    @Transactional
    public FollowRelationResponse unfollow(UUID viewerId, UUID targetUserId) {
        if (viewerId.equals(targetUserId)) {
            throw new BadRequestException("Cannot unfollow yourself");
        }

        getUser(targetUserId);
        socialFollowRepository.deleteByFollower_IdAndFollowee_Id(viewerId, targetUserId);

        final long followersCount = socialFollowRepository.countByFollowee_Id(targetUserId);
        return new FollowRelationResponse(targetUserId, false, followersCount);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialProfileResponse> listFollowers(UUID viewerId,
                                                                   UUID profileId,
                                                                   String cursor,
                                                                   Integer limit) {
        getUser(profileId);

        final int safeLimit = normalizeLimit(limit);
        final int page = cursorCodec.decodePage(cursor);
        final Pageable pageable = PageRequest.of(page, safeLimit);

        final List<SocialFollowEntity> rows = socialFollowRepository.findFollowersPage(profileId, pageable);
        final boolean hasMore = !socialFollowRepository.findFollowersPage(profileId, PageRequest.of(page + 1, safeLimit))
                .isEmpty();

        final List<SocialProfileResponse> items = rows.stream()
                .map(SocialFollowEntity::getFollower)
                .map(user -> buildProfileResponse(user, viewerId))
                .toList();

        return new CursorPageResponse<>(items, hasMore ? cursorCodec.encodePage(page + 1) : null, hasMore);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialProfileResponse> listFollowing(UUID viewerId,
                                                                   UUID profileId,
                                                                   String cursor,
                                                                   Integer limit) {
        getUser(profileId);

        final int safeLimit = normalizeLimit(limit);
        final int page = cursorCodec.decodePage(cursor);
        final Pageable pageable = PageRequest.of(page, safeLimit);

        final List<SocialFollowEntity> rows = socialFollowRepository.findFollowingPage(profileId, pageable);
        final boolean hasMore = !socialFollowRepository.findFollowingPage(profileId, PageRequest.of(page + 1, safeLimit))
                .isEmpty();

        final List<SocialProfileResponse> items = rows.stream()
                .map(SocialFollowEntity::getFollowee)
                .map(user -> buildProfileResponse(user, viewerId))
                .toList();

        return new CursorPageResponse<>(items, hasMore ? cursorCodec.encodePage(page + 1) : null, hasMore);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialLookResponse> listProfileLooks(UUID viewerId,
                                                                   UUID profileId,
                                                                   String cursor,
                                                                   Integer limit) {
        getUser(profileId);

        final int safeLimit = normalizeLimit(limit);
        final int page = cursorCodec.decodePage(cursor);
        final Pageable pageable = PageRequest.of(page, safeLimit);

        final List<SocialLookEntity> rows;
        final boolean hasMore;
        if (viewerId.equals(profileId)) {
            rows = socialLookRepository.findByAuthor_IdOrderByCreatedAtDescIdDesc(profileId, pageable);
            hasMore = !socialLookRepository.findByAuthor_IdOrderByCreatedAtDescIdDesc(profileId, PageRequest.of(page + 1, safeLimit))
                    .isEmpty();
        } else {
            final boolean followsAuthor = socialFollowRepository.existsByFollower_IdAndFollowee_Id(viewerId, profileId);
            final List<SocialLookVisibility> visible = followsAuthor
                    ? List.of(SocialLookVisibility.PUBLIC, SocialLookVisibility.FOLLOWERS)
                    : List.of(SocialLookVisibility.PUBLIC);
            rows = socialLookRepository.findByAuthor_IdAndVisibilityInOrderByCreatedAtDescIdDesc(profileId, visible, pageable);
            hasMore = !socialLookRepository.findByAuthor_IdAndVisibilityInOrderByCreatedAtDescIdDesc(
                    profileId,
                    visible,
                    PageRequest.of(page + 1, safeLimit)
            ).isEmpty();
        }

        final List<SocialLookResponse> items = rows.stream()
                .map(look -> toLookResponse(look, viewerId))
                .toList();

        return new CursorPageResponse<>(items, hasMore ? cursorCodec.encodePage(page + 1) : null, hasMore);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialLookResponse> listPublishedLooks(UUID viewerId,
                                                                     String cursor,
                                                                     Integer limit) {
        final int safeLimit = normalizeLimit(limit);
        final int page = cursorCodec.decodePage(cursor);
        final Pageable pageable = PageRequest.of(page, safeLimit);

        final List<SocialLookEntity> rows = socialLookRepository.findByVisibilityOrderByCreatedAtDescIdDesc(
                SocialLookVisibility.PUBLIC,
                pageable
        );
        final boolean hasMore = !socialLookRepository.findByVisibilityOrderByCreatedAtDescIdDesc(
                SocialLookVisibility.PUBLIC,
                PageRequest.of(page + 1, safeLimit)
        ).isEmpty();

        final List<SocialLookResponse> items = rows.stream()
                .map(look -> toLookResponse(look, viewerId))
                .toList();

        return new CursorPageResponse<>(items, hasMore ? cursorCodec.encodePage(page + 1) : null, hasMore);
    }

    @Transactional
    public SocialLookResponse createLook(UUID viewerId,
                                         byte[] imageBytes,
                                         String imageMime,
                                         String title,
                                         String description,
                                         String style,
                                         String visibility,
                                         List<String> rawTags) {
        final UserEntity user = getUser(viewerId);

        validateImage(imageBytes);
        final String normalizedTitle = validateLookTitle(title);
        final String normalizedDescription = validateLookDescription(description);
        final String normalizedStyle = normalizeStyle(style);
        final SocialLookVisibility normalizedVisibility = SocialLookVisibility.fromApiValue(visibility);
        final List<String> tags = normalizeTags(rawTags);

        final SocialLookEntity look = new SocialLookEntity();
        look.setAuthor(user);
        look.setImageData(imageBytes);
        look.setImageMime(normalizeMime(imageMime));
        look.setTitle(normalizedTitle);
        look.setDescription(normalizedDescription);
        look.setStyle(normalizedStyle);
        look.setVisibility(normalizedVisibility);
        look.setTagsJson(writeTagsJson(tags));

        final SocialLookEntity saved = socialLookRepository.save(look);
        return toLookResponse(saved, viewerId);
    }

    @Transactional(readOnly = true)
    public SocialLookResponse fetchLook(UUID viewerId, UUID lookId) {
        final SocialLookEntity look = getLook(lookId);
        ensureCanViewLook(look, viewerId);
        return toLookResponse(look, viewerId);
    }

    @Transactional
    public SocialLookResponse likeLook(UUID viewerId, UUID lookId) {
        final SocialLookEntity look = getLook(lookId);
        ensureCanViewLook(look, viewerId);

        final boolean liked = socialLookLikeRepository.existsByLook_IdAndUser_Id(lookId, viewerId);
        if (!liked) {
            final SocialLookLikeEntity entity = new SocialLookLikeEntity();
            entity.setId(new SocialLookLikeId(lookId, viewerId));
            entity.setLook(look);
            entity.setUser(getUser(viewerId));
            socialLookLikeRepository.save(entity);
        }

        return toLookResponse(look, viewerId);
    }

    @Transactional
    public SocialLookResponse unlikeLook(UUID viewerId, UUID lookId) {
        final SocialLookEntity look = getLook(lookId);
        ensureCanViewLook(look, viewerId);

        socialLookLikeRepository.deleteByLook_IdAndUser_Id(lookId, viewerId);
        return toLookResponse(look, viewerId);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialCommentResponse> listComments(UUID viewerId,
                                                                  UUID lookId,
                                                                  UUID parentId,
                                                                  String cursor,
                                                                  Integer limit) {
        final SocialLookEntity look = getLook(lookId);
        ensureCanViewLook(look, viewerId);

        if (parentId != null) {
            final SocialCommentEntity parent = getComment(parentId);
            if (!parent.getLook().getId().equals(lookId)) {
                throw new NotFoundException("Comment parent not found");
            }
        }

        final int safeLimit = normalizeLimit(limit);
        final int page = cursorCodec.decodePage(cursor);
        final Pageable pageable = PageRequest.of(page, safeLimit);

        final List<SocialCommentEntity> rows = parentId == null
                ? socialCommentRepository.findByLook_IdAndParentIsNullOrderByCreatedAtDescIdDesc(lookId, pageable)
                : socialCommentRepository.findByLook_IdAndParent_IdOrderByCreatedAtDescIdDesc(lookId, parentId, pageable);

        final boolean hasMore = parentId == null
                ? !socialCommentRepository.findByLook_IdAndParentIsNullOrderByCreatedAtDescIdDesc(
                lookId,
                PageRequest.of(page + 1, safeLimit)
        ).isEmpty()
                : !socialCommentRepository.findByLook_IdAndParent_IdOrderByCreatedAtDescIdDesc(
                lookId,
                parentId,
                PageRequest.of(page + 1, safeLimit)
        ).isEmpty();
        final List<SocialCommentResponse> items = rows.stream()
                .map(comment -> toCommentResponse(comment, viewerId))
                .toList();

        return new CursorPageResponse<>(items, hasMore ? cursorCodec.encodePage(page + 1) : null, hasMore);
    }

    @Transactional
    public SocialCommentResponse createComment(UUID viewerId,
                                               UUID lookId,
                                               String body,
                                               UUID parentId) {
        final SocialLookEntity look = getLook(lookId);
        ensureCanViewLook(look, viewerId);

        final String normalizedBody = validateCommentBody(body);
        final SocialCommentEntity parent;
        if (parentId == null) {
            parent = null;
        } else {
            parent = getComment(parentId);
            if (!parent.getLook().getId().equals(lookId)) {
                throw new BadRequestException("parentId does not belong to look");
            }
            if (parent.getParent() != null) {
                throw new BadRequestException("Reply depth greater than 2 is not allowed");
            }
        }

        final SocialCommentEntity comment = new SocialCommentEntity();
        comment.setLook(look);
        comment.setAuthor(getUser(viewerId));
        comment.setParent(parent);
        comment.setBody(normalizedBody);

        final SocialCommentEntity saved = socialCommentRepository.save(comment);
        return toCommentResponse(saved, viewerId);
    }

    @Transactional
    public void deleteComment(UUID viewerId, UUID commentId) {
        final SocialCommentEntity comment = getComment(commentId);
        if (!comment.getAuthor().getId().equals(viewerId)) {
            throw new ForbiddenException("You cannot delete this comment");
        }

        socialCommentRepository.deleteByParent_Id(commentId);
        socialCommentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public SocialLookDraftResponse fetchMyLookDraft(UUID viewerId) {
        final SocialLookDraftEntity draft = socialLookDraftRepository.findById(viewerId)
                .orElseThrow(() -> new NotFoundException("Look draft not found"));
        return socialMapper.toSocialLookDraft(draft, readTagsJson(draft.getTagsJson()));
    }

    @Transactional
    public SocialLookDraftResponse upsertMyLookDraft(UUID viewerId,
                                                     byte[] imageBytes,
                                                     String imageMime,
                                                     String title,
                                                     String description,
                                                     String style,
                                                     String visibility,
                                                     List<String> rawTags,
                                                     boolean clearImage) {
        final String normalizedTitle = validateDraftTitle(title);
        final String normalizedDescription = validateLookDescription(description);
        final String normalizedStyle = normalizeStyle(style);
        final SocialLookVisibility normalizedVisibility = SocialLookVisibility.fromApiValue(visibility);
        final List<String> tags = normalizeTags(rawTags);

        if (imageBytes != null && imageBytes.length > 0) {
            validateImage(imageBytes);
        }

        final UserEntity user = getUser(viewerId);
        final SocialLookDraftEntity draft = socialLookDraftRepository.findById(viewerId)
                .orElseGet(() -> {
                    final SocialLookDraftEntity next = new SocialLookDraftEntity();
                    next.setUser(user);
                    next.setTitle("");
                    next.setDescription("");
                    next.setStyle("Casual");
                    next.setVisibility(SocialLookVisibility.PUBLIC);
                    next.setTagsJson("[]");
                    return next;
                });

        draft.setTitle(normalizedTitle);
        draft.setDescription(normalizedDescription);
        draft.setStyle(normalizedStyle);
        draft.setVisibility(normalizedVisibility);
        draft.setTagsJson(writeTagsJson(tags));

        if (imageBytes != null && imageBytes.length > 0) {
            draft.setImageData(imageBytes);
            draft.setImageMime(normalizeMime(imageMime));
        } else if (clearImage) {
            draft.setImageData(null);
            draft.setImageMime(null);
        }

        final SocialLookDraftEntity saved = socialLookDraftRepository.save(draft);
        return socialMapper.toSocialLookDraft(saved, tags);
    }

    @Transactional
    public void deleteMyLookDraft(UUID viewerId) {
        socialLookDraftRepository.deleteById(viewerId);
    }

    private SocialProfileResponse buildProfileResponse(UserEntity profile, UUID viewerId) {
        final UUID profileId = profile.getId();
        final long followersCount = socialFollowRepository.countByFollowee_Id(profileId);
        final long followingCount = socialFollowRepository.countByFollower_Id(profileId);
        final long looksCount = socialLookRepository.countByAuthor_Id(profileId);
        final boolean isFollowing = !viewerId.equals(profileId)
                && socialFollowRepository.existsByFollower_IdAndFollowee_Id(viewerId, profileId);

        return socialMapper.toSocialProfile(profile, followersCount, followingCount, looksCount, isFollowing, viewerId);
    }

    private SocialLookResponse toLookResponse(SocialLookEntity look, UUID viewerId) {
        final List<String> tags = readTagsJson(look.getTagsJson());
        final long likesCount = socialLookLikeRepository.countByLook_Id(look.getId());
        final long commentsCount = socialCommentRepository.countByLook_Id(look.getId());
        final boolean isLikedByMe = socialLookLikeRepository.existsByLook_IdAndUser_Id(look.getId(), viewerId);
        return socialMapper.toSocialLook(look, tags, likesCount, commentsCount, isLikedByMe);
    }

    private SocialCommentResponse toCommentResponse(SocialCommentEntity comment, UUID viewerId) {
        final long repliesCount = socialCommentRepository.countByParent_Id(comment.getId());
        final boolean canDelete = comment.getAuthor().getId().equals(viewerId);
        return socialMapper.toSocialComment(comment, repliesCount, canDelete);
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private SocialLookEntity getLook(UUID lookId) {
        return socialLookRepository.findById(lookId)
                .orElseThrow(() -> new NotFoundException("Look not found"));
    }

    private SocialCommentEntity getComment(UUID commentId) {
        return socialCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
    }

    private void ensureCanViewLook(SocialLookEntity look, UUID viewerId) {
        if (!socialVisibilityPolicy.canViewLook(look, viewerId)) {
            throw new ForbiddenException("You cannot access this look");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return SocialLimits.DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(SocialLimits.MAX_PAGE_SIZE, limit));
    }

    private void validateImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BadRequestException("image is required");
        }
        if (imageBytes.length > SocialLimits.MAX_IMAGE_SIZE_BYTES) {
            throw new PayloadTooLargeException("Image size must be at most 5MB");
        }
    }

    private String validateLookTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BadRequestException("title is required");
        }

        final String normalized = title.trim();
        if (normalized.length() > SocialLimits.LOOK_TITLE_MAX) {
            throw new BadRequestException("title is too long");
        }

        return normalized;
    }

    private String validateDraftTitle(String title) {
        final String normalized = title == null ? "" : title.trim();
        if (normalized.length() > SocialLimits.LOOK_TITLE_MAX) {
            throw new BadRequestException("title is too long");
        }
        return normalized;
    }

    private String validateLookDescription(String description) {
        final String normalized = description == null ? "" : description.trim();
        if (normalized.length() > SocialLimits.LOOK_DESCRIPTION_MAX) {
            throw new BadRequestException("description is too long");
        }
        return normalized;
    }

    private String validateCommentBody(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new BadRequestException("body is required");
        }

        final String normalized = body.trim();
        if (normalized.length() > SocialLimits.COMMENT_BODY_MAX) {
            throw new BadRequestException("comment body is too long");
        }

        return normalized;
    }

    private String normalizeStyle(String style) {
        if (style == null || style.trim().isEmpty()) {
            return "Casual";
        }

        final String normalized = style.trim();
        if (normalized.length() > 64) {
            throw new BadRequestException("style is too long");
        }

        return normalized;
    }

    private String normalizeMime(String imageMime) {
        if (imageMime == null || imageMime.isBlank()) {
            return "image/jpeg";
        }

        final String normalized = imageMime.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("image/")) {
            throw new BadRequestException("Only image files are supported");
        }

        return normalized;
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        final LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : rawTags) {
            if (tag == null) {
                continue;
            }

            final String next = tag.trim().replaceFirst("^#+", "");
            if (next.isEmpty()) {
                continue;
            }

            normalized.add(next);
            if (normalized.size() > SocialLimits.MAX_TAGS) {
                throw new BadRequestException("Too many tags");
            }
        }

        return new ArrayList<>(normalized);
    }

    private String writeTagsJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize tags", ex);
        }
    }

    private List<String> readTagsJson(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }

        try {
            final List<String> parsed = objectMapper.readValue(tagsJson, TAGS_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
