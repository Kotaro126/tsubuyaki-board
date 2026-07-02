package com.example.tsubuyaki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @SequenceGenerator(name = "posts_seq_gen", sequenceName = "posts_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "posts_seq_gen")
    private Long id;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "body", length = 280, nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // parent が null の投稿を通常投稿、値を持つ投稿をリプライとして扱う。
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Post parent;

    @OneToMany(mappedBy = "parent")
    private List<Post> replies = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Post() {
        // JPA
    }

    public Post(String author, String body, Instant createdAt) {
        this(new User(author, User.DEFAULT_AVATAR_COLOR), body, createdAt);
    }

    public Post(String author, String body, Instant createdAt, String avatarColor) {
        this(new User(author, avatarColor), body, createdAt);
    }

    public Post(User user, String body, Instant createdAt) {
        this(null, user, body, createdAt);
    }

    public Post(Post parent, String author, String body, Instant createdAt) {
        this(parent, new User(author, User.DEFAULT_AVATAR_COLOR), body, createdAt);
    }

    public Post(Post parent, String author, String body, Instant createdAt, String avatarColor) {
        this(parent, new User(author, avatarColor), body, createdAt);
    }

    public Post(Post parent, User user, String body, Instant createdAt) {
        this.parent = parent;
        this.user = user;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getAuthor() {
        return user.getName();
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getAvatarColor() {
        return user.getAvatarColor();
    }

    public Set<Tag> getTags() {
        return Set.copyOf(tags);
    }

    public Post getParent() {
        return parent;
    }

    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    public boolean isReply() {
        return parent != null;
    }

    public List<Post> getReplies() {
        return List.copyOf(replies);
    }

    public void addTag(Tag tag) {
        tags.add(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Post other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public User getUser() {
        return user;
    }
}
