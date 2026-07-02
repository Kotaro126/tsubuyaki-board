package com.example.tsubuyaki.repository;

import com.example.tsubuyaki.domain.Post;
import com.example.tsubuyaki.domain.Tag;
import com.example.tsubuyaki.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("h2")
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("本文検索_キーワードを含む投稿だけ_新着順で返す")
    void 本文検索_キーワードを含む投稿だけ_新着順で返す() {
        Post oldMatch = postRepository.save(new Post("alice", "xxx を含む古い投稿",
                Instant.parse("2026-05-23T09:00:00Z")));
        Post parent = postRepository.save(new Post("dave", "親投稿",
                Instant.parse("2026-05-23T08:00:00Z")));
        postRepository.save(new Post(parent, "erin", "xxx を含むリプライ",
                Instant.parse("2026-05-23T12:00:00Z")));
        postRepository.save(new Post("bob", "一致しない投稿",
                Instant.parse("2026-05-23T10:00:00Z")));
        Post newMatch = postRepository.save(new Post("carol", "新しい xxx 投稿",
                Instant.parse("2026-05-23T11:00:00Z")));

        List<Post> posts = postRepository.findTop50ByParentIsNullAndBodyContainingOrderByCreatedAtDesc("xxx");

        assertThat(posts).containsExactly(newMatch, oldMatch);
    }

    @Test
    @DisplayName("投稿一覧_リプライがあるとき_親投稿だけを新着順で返す")
    void 投稿一覧_リプライがあるとき_親投稿だけを新着順で返す() {
        Post oldParent = postRepository.save(new Post("alice", "古い親投稿",
                Instant.parse("2026-05-23T09:00:00Z")));
        Post newParent = postRepository.save(new Post("bob", "新しい親投稿",
                Instant.parse("2026-05-23T11:00:00Z")));
        postRepository.save(new Post(oldParent, "carol", "親より新しいリプライ",
                Instant.parse("2026-05-23T12:00:00Z")));

        List<Post> posts = postRepository.findTop50ByParentIsNullOrderByCreatedAtDesc();

        assertThat(posts).containsExactly(newParent, oldParent);
    }

    @Test
    @DisplayName("リプライ取得_親投稿に複数リプライがあるとき_作成日時昇順で返す")
    void リプライ取得_親投稿に複数リプライがあるとき_作成日時昇順で返す() {
        Post parent = postRepository.save(new Post("alice", "親投稿",
                Instant.parse("2026-05-23T09:00:00Z")));
        Post newerReply = postRepository.save(new Post(parent, "bob", "新しいリプライ",
                Instant.parse("2026-05-23T11:00:00Z")));
        Post olderReply = postRepository.save(new Post(parent, "carol", "古いリプライ",
                Instant.parse("2026-05-23T10:00:00Z")));
        postRepository.save(new Post("dave", "別の親投稿",
                Instant.parse("2026-05-23T12:00:00Z")));

        List<Post> replies = postRepository.findByParentIdInOrderByCreatedAtAsc(List.of(parent.getId()));

        assertThat(replies).containsExactly(olderReply, newerReply);
        assertThat(replies).allSatisfy(reply -> assertThat(reply.getParentId()).isEqualTo(parent.getId()));
    }

    @Test
    @DisplayName("投稿保存_Userを指定したとき_userId経由でアバター色を参照できる")
    void 投稿保存_Userを指定したとき_userId経由でアバター色を参照できる() {
        User user = userRepository.save(new User("alice", "#ef4444"));
        Post saved = postRepository.save(new Post(user, "色付き投稿",
                Instant.parse("2026-05-23T10:00:00Z")));
        postRepository.flush();

        Post found = postRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getAvatarColor()).isEqualTo("#ef4444");
    }

    @Test
    @DisplayName("タグ検索_指定タグに紐づく投稿だけ_新着順で返す")
    void タグ検索_指定タグに紐づく投稿だけ_新着順で返す() {
        Tag spring = tagRepository.save(new Tag("spring"));
        Tag java = tagRepository.save(new Tag("java"));
        Post oldMatch = new Post("alice", "#spring 古い投稿",
                Instant.parse("2026-05-23T09:00:00Z"));
        oldMatch.addTag(spring);
        Post newMatch = new Post("bob", "#spring 新しい投稿",
                Instant.parse("2026-05-23T11:00:00Z"));
        newMatch.addTag(spring);
        Post replyMatch = new Post(oldMatch, "dave", "#spring リプライ",
                Instant.parse("2026-05-23T12:00:00Z"));
        replyMatch.addTag(spring);
        Post other = new Post("carol", "#java の投稿",
                Instant.parse("2026-05-23T10:00:00Z"));
        other.addTag(java);
        postRepository.saveAll(List.of(oldMatch, newMatch, replyMatch, other));

        List<Post> posts = postRepository.findDistinctTop50ByParentIsNullAndTagsNameOrderByCreatedAtDesc("spring");

        assertThat(posts).containsExactly(newMatch, oldMatch);
    }

    @Test
    @DisplayName("タグ検索_存在しないタグのとき_空配列を返す")
    void タグ検索_存在しないタグのとき_空配列を返す() {
        List<Post> posts = postRepository.findDistinctTop50ByParentIsNullAndTagsNameOrderByCreatedAtDesc("missing");

        assertThat(posts).isEmpty();
    }
}
