ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(64),
    ADD COLUMN IF NOT EXISTS bio TEXT,
    ADD COLUMN IF NOT EXISTS avatar_url TEXT;

UPDATE users
SET username = LOWER('user_' || REPLACE(id::TEXT, '-', ''))
WHERE username IS NULL OR TRIM(username) = '';

ALTER TABLE users
    ALTER COLUMN username SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username ON users (username);

CREATE TABLE social_follows (
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT chk_social_follows_not_self CHECK (follower_id <> followee_id)
);

CREATE INDEX idx_social_follows_followee_created ON social_follows (followee_id, created_at DESC);
CREATE INDEX idx_social_follows_follower_created ON social_follows (follower_id, created_at DESC);

CREATE TABLE social_looks (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_data BYTEA NOT NULL,
    image_mime VARCHAR(64) NOT NULL,
    title VARCHAR(70) NOT NULL,
    description VARCHAR(280) NOT NULL DEFAULT '',
    tags_json TEXT NOT NULL DEFAULT '[]',
    style VARCHAR(64) NOT NULL DEFAULT 'Casual',
    visibility VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_social_looks_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE'))
);

CREATE INDEX idx_social_looks_author_created ON social_looks (author_id, created_at DESC);
CREATE INDEX idx_social_looks_created ON social_looks (created_at DESC);

CREATE TABLE social_look_likes (
    look_id UUID NOT NULL REFERENCES social_looks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (look_id, user_id)
);

CREATE INDEX idx_social_look_likes_user_created ON social_look_likes (user_id, created_at DESC);

CREATE TABLE social_comments (
    id UUID PRIMARY KEY,
    look_id UUID NOT NULL REFERENCES social_looks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES social_comments(id) ON DELETE CASCADE,
    body VARCHAR(400) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_social_comments_look_parent_created ON social_comments (look_id, parent_id, created_at DESC);
CREATE INDEX idx_social_comments_parent_created ON social_comments (parent_id, created_at DESC);

CREATE TABLE social_look_drafts (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    image_data BYTEA,
    image_mime VARCHAR(64),
    title VARCHAR(70) NOT NULL DEFAULT '',
    description VARCHAR(280) NOT NULL DEFAULT '',
    tags_json TEXT NOT NULL DEFAULT '[]',
    style VARCHAR(64) NOT NULL DEFAULT 'Casual',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_social_look_drafts_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE'))
);
