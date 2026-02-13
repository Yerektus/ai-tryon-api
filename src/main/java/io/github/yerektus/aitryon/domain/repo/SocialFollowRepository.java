package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.SocialFollowEntity;
import io.github.yerektus.aitryon.domain.SocialFollowId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SocialFollowRepository extends JpaRepository<SocialFollowEntity, SocialFollowId> {
    boolean existsByFollower_IdAndFollowee_Id(UUID followerId, UUID followeeId);

    long countByFollowee_Id(UUID followeeId);

    long countByFollower_Id(UUID followerId);

    void deleteByFollower_IdAndFollowee_Id(UUID followerId, UUID followeeId);

    @Query("""
            select sf
            from SocialFollowEntity sf
            where sf.followee.id = :followeeId
            order by sf.createdAt desc, sf.follower.id desc
            """)
    List<SocialFollowEntity> findFollowersPage(@Param("followeeId") UUID followeeId, Pageable pageable);

    @Query("""
            select sf
            from SocialFollowEntity sf
            where sf.follower.id = :followerId
            order by sf.createdAt desc, sf.followee.id desc
            """)
    List<SocialFollowEntity> findFollowingPage(@Param("followerId") UUID followerId, Pageable pageable);
}
