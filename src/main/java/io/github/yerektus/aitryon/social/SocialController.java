package io.github.yerektus.aitryon.social;

import io.github.yerektus.aitryon.security.AuthenticatedUser;
import io.github.yerektus.aitryon.social.dto.CreateSocialCommentRequest;
import io.github.yerektus.aitryon.social.dto.CursorPageResponse;
import io.github.yerektus.aitryon.social.dto.FollowRelationResponse;
import io.github.yerektus.aitryon.social.dto.SocialCommentResponse;
import io.github.yerektus.aitryon.social.dto.SocialLookDraftResponse;
import io.github.yerektus.aitryon.social.dto.SocialLookResponse;
import io.github.yerektus.aitryon.social.dto.SocialProfileResponse;
import io.github.yerektus.aitryon.social.dto.UpdateMySocialProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @GetMapping("/profiles/me")
    public SocialProfileResponse fetchMyProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return socialService.fetchMyProfile(user.userId());
    }

    @GetMapping("/profiles/{userId}")
    public SocialProfileResponse fetchProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable UUID userId) {
        return socialService.fetchProfile(user.userId(), userId);
    }

    @PatchMapping("/profiles/me")
    public SocialProfileResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody UpdateMySocialProfileRequest request) {
        return socialService.updateMyProfile(user.userId(), request);
    }

    @PostMapping("/follows/{userId}")
    public FollowRelationResponse follow(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID userId) {
        return socialService.follow(user.userId(), userId);
    }

    @DeleteMapping("/follows/{userId}")
    public FollowRelationResponse unfollow(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID userId) {
        return socialService.unfollow(user.userId(), userId);
    }

    @GetMapping("/profiles/{userId}/followers")
    public CursorPageResponse<SocialProfileResponse> listFollowers(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @PathVariable UUID userId,
                                                                   @RequestParam(name = "cursor", required = false) String cursor,
                                                                   @RequestParam(name = "limit", required = false) Integer limit) {
        return socialService.listFollowers(user.userId(), userId, cursor, limit);
    }

    @GetMapping("/profiles/{userId}/following")
    public CursorPageResponse<SocialProfileResponse> listFollowing(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @PathVariable UUID userId,
                                                                   @RequestParam(name = "cursor", required = false) String cursor,
                                                                   @RequestParam(name = "limit", required = false) Integer limit) {
        return socialService.listFollowing(user.userId(), userId, cursor, limit);
    }

    @GetMapping("/profiles/{userId}/looks")
    public CursorPageResponse<SocialLookResponse> listProfileLooks(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @PathVariable UUID userId,
                                                                   @RequestParam(name = "cursor", required = false) String cursor,
                                                                   @RequestParam(name = "limit", required = false) Integer limit) {
        return socialService.listProfileLooks(user.userId(), userId, cursor, limit);
    }

    @PostMapping(value = "/looks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SocialLookResponse createLook(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam("image") MultipartFile image,
                                         @RequestParam("title") String title,
                                         @RequestParam(name = "description", required = false) String description,
                                         @RequestParam(name = "style", required = false) String style,
                                         @RequestParam("visibility") String visibility,
                                         @RequestParam(name = "tags[]", required = false) List<String> tags) throws IOException {
        return socialService.createLook(
                user.userId(),
                image.getBytes(),
                image.getContentType(),
                title,
                description,
                style,
                visibility,
                tags
        );
    }

    @GetMapping("/looks")
    public CursorPageResponse<SocialLookResponse> listPublishedLooks(@AuthenticationPrincipal AuthenticatedUser user,
                                                                     @RequestParam(name = "cursor", required = false) String cursor,
                                                                     @RequestParam(name = "limit", required = false) Integer limit) {
        return socialService.listPublishedLooks(user.userId(), cursor, limit);
    }

    @GetMapping("/looks/{lookId}")
    public SocialLookResponse fetchLook(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID lookId) {
        return socialService.fetchLook(user.userId(), lookId);
    }

    @PostMapping("/looks/{lookId}/likes")
    public SocialLookResponse likeLook(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID lookId) {
        return socialService.likeLook(user.userId(), lookId);
    }

    @DeleteMapping("/looks/{lookId}/likes")
    public SocialLookResponse unlikeLook(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID lookId) {
        return socialService.unlikeLook(user.userId(), lookId);
    }

    @GetMapping("/looks/{lookId}/comments")
    public CursorPageResponse<SocialCommentResponse> listComments(@AuthenticationPrincipal AuthenticatedUser user,
                                                                  @PathVariable UUID lookId,
                                                                  @RequestParam(name = "parentId", required = false) UUID parentId,
                                                                  @RequestParam(name = "cursor", required = false) String cursor,
                                                                  @RequestParam(name = "limit", required = false) Integer limit) {
        return socialService.listComments(user.userId(), lookId, parentId, cursor, limit);
    }

    @PostMapping("/looks/{lookId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialCommentResponse createComment(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID lookId,
                                               @Valid @RequestBody CreateSocialCommentRequest request) {
        return socialService.createComment(user.userId(), lookId, request.body(), request.parentId());
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal AuthenticatedUser user,
                              @PathVariable UUID commentId) {
        socialService.deleteComment(user.userId(), commentId);
    }

    @GetMapping("/look-drafts/me")
    public SocialLookDraftResponse fetchMyLookDraft(@AuthenticationPrincipal AuthenticatedUser user) {
        return socialService.fetchMyLookDraft(user.userId());
    }

    @PutMapping(value = "/look-drafts/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SocialLookDraftResponse upsertMyLookDraft(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(name = "title", required = false) String title,
                                                     @RequestParam(name = "description", required = false) String description,
                                                     @RequestParam(name = "style", required = false) String style,
                                                     @RequestParam(name = "visibility", required = false, defaultValue = "public") String visibility,
                                                     @RequestParam(name = "tags[]", required = false) List<String> tags,
                                                     @RequestParam(name = "clearImage", defaultValue = "false") boolean clearImage,
                                                     @RequestParam(name = "image", required = false) MultipartFile image) throws IOException {
        return socialService.upsertMyLookDraft(
                user.userId(),
                image != null ? image.getBytes() : null,
                image != null ? image.getContentType() : null,
                title,
                description,
                style,
                visibility,
                tags,
                clearImage
        );
    }

    @DeleteMapping("/look-drafts/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyLookDraft(@AuthenticationPrincipal AuthenticatedUser user) {
        socialService.deleteMyLookDraft(user.userId());
    }
}
