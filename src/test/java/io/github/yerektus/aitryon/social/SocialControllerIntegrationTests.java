package io.github.yerektus.aitryon.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yerektus.aitryon.domain.SocialCommentEntity;
import io.github.yerektus.aitryon.domain.SocialFollowEntity;
import io.github.yerektus.aitryon.domain.SocialFollowId;
import io.github.yerektus.aitryon.domain.SocialLookEntity;
import io.github.yerektus.aitryon.domain.SocialLookVisibility;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.repo.SocialCommentRepository;
import io.github.yerektus.aitryon.domain.repo.SocialFollowRepository;
import io.github.yerektus.aitryon.domain.repo.SocialLookDraftRepository;
import io.github.yerektus.aitryon.domain.repo.SocialLookLikeRepository;
import io.github.yerektus.aitryon.domain.repo.SocialLookRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import io.github.yerektus.aitryon.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SocialControllerIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialFollowRepository socialFollowRepository;

    @Autowired
    private SocialLookRepository socialLookRepository;

    @Autowired
    private SocialLookLikeRepository socialLookLikeRepository;

    @Autowired
    private SocialCommentRepository socialCommentRepository;

    @Autowired
    private SocialLookDraftRepository socialLookDraftRepository;

    @BeforeEach
    void cleanup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        socialLookLikeRepository.deleteAll();
        socialCommentRepository.deleteAll();
        socialLookRepository.deleteAll();
        socialFollowRepository.deleteAll();
        socialLookDraftRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void profileEndpointsReturnMeAndFollowingFlags() throws Exception {
        final UserEntity me = createUser("me");
        final UserEntity other = createUser("other");
        follow(me, other);

        mockMvc.perform(get("/api/v1/social/profiles/me").with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.getId().toString()))
                .andExpect(jsonPath("$.username").value(me.getUsername()))
                .andExpect(jsonPath("$.isMe").value(true))
                .andExpect(jsonPath("$.isFollowing").value(false));

        mockMvc.perform(get("/api/v1/social/profiles/{id}", other.getId()).with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.getId().toString()))
                .andExpect(jsonPath("$.isMe").value(false))
                .andExpect(jsonPath("$.isFollowing").value(true));
    }

    @Test
    void followAndUnfollowAreIdempotent() throws Exception {
        final UserEntity me = createUser("follower");
        final UserEntity other = createUser("followee");

        mockMvc.perform(post("/api/v1/social/follows/{id}", other.getId()).with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(other.getId().toString()))
                .andExpect(jsonPath("$.isFollowing").value(true))
                .andExpect(jsonPath("$.followersCount").value(1));

        mockMvc.perform(post("/api/v1/social/follows/{id}", other.getId()).with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(1));

        mockMvc.perform(delete("/api/v1/social/follows/{id}", other.getId()).with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFollowing").value(false))
                .andExpect(jsonPath("$.followersCount").value(0));

        mockMvc.perform(delete("/api/v1/social/follows/{id}", other.getId()).with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(0));
    }

    @Test
    void patchProfileUpdatesDisplayNameBioAndAvatar() throws Exception {
        final UserEntity me = createUser("patch_me");

        mockMvc.perform(patch("/api/v1/social/profiles/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"displayName\":\"New Display Name\",
                                  \"bio\":\"Updated bio\",
                                  \"avatarUrl\":\"https://cdn.example.com/avatar.png\"
                                }
                                """)
                        .with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Display Name"))
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example.com/avatar.png"));
    }

    @Test
    void publishLookAndListLooks() throws Exception {
        final UserEntity author = createUser("author");
        final MockMultipartFile image = new MockMultipartFile(
                "image",
                "look.jpg",
                "image/jpeg",
                "img".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/social/looks")
                        .file(image)
                        .param("title", "Urban fit")
                        .param("description", "desc")
                        .param("style", "Casual")
                        .param("visibility", "public")
                        .param("tags[]", "street", "night")
                        .with(auth(author)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Urban fit"))
                .andExpect(jsonPath("$.visibility").value("public"))
                .andExpect(jsonPath("$.imageUrl").isNotEmpty());

        mockMvc.perform(get("/api/v1/social/profiles/{id}/looks", author.getId())
                        .param("limit", "20")
                        .with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Urban fit"));
    }

    @Test
    void visibilityRulesAppliedForListAndFetch() throws Exception {
        final UserEntity author = createUser("author_v");
        final UserEntity follower = createUser("follower_v");
        final UserEntity stranger = createUser("stranger_v");

        follow(follower, author);

        final SocialLookEntity publicLook = createLook(author, SocialLookVisibility.PUBLIC, "Public");
        final SocialLookEntity followersLook = createLook(author, SocialLookVisibility.FOLLOWERS, "Followers");
        final SocialLookEntity privateLook = createLook(author, SocialLookVisibility.PRIVATE, "Private");

        mockMvc.perform(get("/api/v1/social/profiles/{id}/looks", author.getId())
                        .param("limit", "20")
                        .with(auth(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(publicLook.getId().toString()));

        mockMvc.perform(get("/api/v1/social/profiles/{id}/looks", author.getId())
                        .param("limit", "20")
                        .with(auth(follower)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/social/profiles/{id}/looks", author.getId())
                        .param("limit", "20")
                        .with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        mockMvc.perform(get("/api/v1/social/looks/{id}", privateLook.getId()).with(auth(stranger)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/social/looks/{id}", followersLook.getId()).with(auth(follower)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(followersLook.getId().toString()));
    }

    @Test
    void publishedLooksEndpointReturnsOnlyPublicLooksWithCursorPagination() throws Exception {
        final UserEntity authorA = createUser("author_a");
        final UserEntity authorB = createUser("author_b");
        final UserEntity viewer = createUser("viewer_feed");

        final SocialLookEntity publicA = createLook(authorA, SocialLookVisibility.PUBLIC, "Public A");
        final SocialLookEntity followersA = createLook(authorA, SocialLookVisibility.FOLLOWERS, "Followers A");
        final SocialLookEntity privateA = createLook(authorA, SocialLookVisibility.PRIVATE, "Private A");
        final SocialLookEntity publicB = createLook(authorB, SocialLookVisibility.PUBLIC, "Public B");

        final MvcResult fullPage = mockMvc.perform(get("/api/v1/social/looks")
                        .param("limit", "10")
                        .with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        final List<String> allIds = StreamSupport.stream(
                        json(fullPage).path("items").spliterator(),
                        false
                )
                .map(item -> item.path("id").asText())
                .toList();

        assertThat(allIds)
                .contains(publicA.getId().toString(), publicB.getId().toString())
                .doesNotContain(followersA.getId().toString(), privateA.getId().toString());

        final MvcResult firstPage = mockMvc.perform(get("/api/v1/social/looks")
                        .param("limit", "1")
                        .with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();

        final String nextCursor = json(firstPage).path("nextCursor").asText();

        mockMvc.perform(get("/api/v1/social/looks")
                        .param("limit", "1")
                        .param("cursor", nextCursor)
                        .with(auth(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void commentFlowSupportsRepliesDepthGuardAndDeletePermissions() throws Exception {
        final UserEntity author = createUser("author_c");
        final UserEntity viewer = createUser("viewer_c");
        final SocialLookEntity look = createLook(author, SocialLookVisibility.PUBLIC, "Comment look");

        final MvcResult topLevelResult = mockMvc.perform(post("/api/v1/social/looks/{lookId}/comments", look.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Top level\",\"parentId\":null}")
                        .with(auth(viewer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Top level"))
                .andReturn();

        final String topCommentId = json(topLevelResult).get("id").asText();

        final MvcResult replyResult = mockMvc.perform(post("/api/v1/social/looks/{lookId}/comments", look.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Reply\",\"parentId\":\"" + topCommentId + "\"}")
                        .with(auth(author)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(topCommentId))
                .andReturn();

        final String replyId = json(replyResult).get("id").asText();

        mockMvc.perform(post("/api/v1/social/looks/{lookId}/comments", look.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Too deep\",\"parentId\":\"" + replyId + "\"}")
                        .with(auth(viewer)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/social/comments/{id}", topCommentId).with(auth(author)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/social/comments/{id}", topCommentId).with(auth(viewer)))
                .andExpect(status().isNoContent());

        assertThat(socialCommentRepository.count()).isZero();
    }

    @Test
    void likesAreIdempotent() throws Exception {
        final UserEntity author = createUser("author_l");
        final UserEntity liker = createUser("liker_l");
        final SocialLookEntity look = createLook(author, SocialLookVisibility.PUBLIC, "Like look");

        mockMvc.perform(post("/api/v1/social/looks/{id}/likes", look.getId()).with(auth(liker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likesCount").value(1))
                .andExpect(jsonPath("$.isLikedByMe").value(true));

        mockMvc.perform(post("/api/v1/social/looks/{id}/likes", look.getId()).with(auth(liker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likesCount").value(1));

        mockMvc.perform(delete("/api/v1/social/looks/{id}/likes", look.getId()).with(auth(liker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likesCount").value(0))
                .andExpect(jsonPath("$.isLikedByMe").value(false));

        mockMvc.perform(delete("/api/v1/social/looks/{id}/likes", look.getId()).with(auth(liker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likesCount").value(0));
    }

    @Test
    void draftLifecycleAndOversizeValidation() throws Exception {
        final UserEntity me = createUser("draft_user");

        final MockMultipartFile image = new MockMultipartFile(
                "image",
                "draft.jpg",
                "image/jpeg",
                "small-image".getBytes(StandardCharsets.UTF_8)
        );

        final MvcResult upsertResult = mockMvc.perform(multipart("/api/v1/social/look-drafts/me")
                        .file(image)
                        .param("title", "Draft title")
                        .param("description", "Draft description")
                        .param("style", "Casual")
                        .param("visibility", "public")
                        .param("tags[]", "a", "b")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft title"))
                .andExpect(jsonPath("$.imageDataUri").isNotEmpty())
                .andReturn();

        final String draftImageDataUri = json(upsertResult).get("imageDataUri").asText();
        assertThat(draftImageDataUri).startsWith("data:image/jpeg;base64,");

        mockMvc.perform(get("/api/v1/social/look-drafts/me").with(auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft title"));

        mockMvc.perform(delete("/api/v1/social/look-drafts/me").with(auth(me)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/social/look-drafts/me").with(auth(me)))
                .andExpect(status().isNotFound());

        final byte[] big = new byte[SocialLimits.MAX_IMAGE_SIZE_BYTES + 1];
        final MockMultipartFile bigImage = new MockMultipartFile(
                "image",
                "big.jpg",
                "image/jpeg",
                big
        );

        mockMvc.perform(multipart("/api/v1/social/look-drafts/me")
                        .file(bigImage)
                        .param("title", "Big draft")
                        .param("description", "desc")
                        .param("style", "Casual")
                        .param("visibility", "public")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(auth(me)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    void followersListSupportsCursorPagination() throws Exception {
        final UserEntity target = createUser("target_p");
        final UserEntity f1 = createUser("f1_p");
        final UserEntity f2 = createUser("f2_p");
        final UserEntity f3 = createUser("f3_p");

        follow(f1, target);
        follow(f2, target);
        follow(f3, target);

        final MvcResult firstPage = mockMvc.perform(get("/api/v1/social/profiles/{id}/followers", target.getId())
                        .param("limit", "2")
                        .with(auth(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();

        final String nextCursor = json(firstPage).get("nextCursor").asText();

        mockMvc.perform(get("/api/v1/social/profiles/{id}/followers", target.getId())
                        .param("limit", "2")
                        .param("cursor", nextCursor)
                        .with(auth(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    private UserEntity createUser(String prefix) {
        final String token = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        final UserEntity user = new UserEntity();
        user.setEmail(token + "@example.com");
        user.setDisplayName(prefix);
        user.setUsername(token);
        user.setCreditsBalance(0);
        return userRepository.save(user);
    }

    private SocialLookEntity createLook(UserEntity author, SocialLookVisibility visibility, String title) {
        final SocialLookEntity look = new SocialLookEntity();
        look.setAuthor(author);
        look.setImageData(("img-" + title).getBytes(StandardCharsets.UTF_8));
        look.setImageMime("image/jpeg");
        look.setTitle(title);
        look.setDescription("desc");
        look.setTagsJson("[\"tag\"]");
        look.setStyle("Casual");
        look.setVisibility(visibility);
        return socialLookRepository.save(look);
    }

    private void follow(UserEntity follower, UserEntity followee) {
        final SocialFollowEntity relation = new SocialFollowEntity();
        relation.setId(new SocialFollowId(follower.getId(), followee.getId()));
        relation.setFollower(follower);
        relation.setFollowee(followee);
        socialFollowRepository.save(relation);
    }

    private RequestPostProcessor auth(UserEntity user) {
        final AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail());
        final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authentication);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
