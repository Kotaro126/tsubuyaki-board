package com.example.tsubuyaki.service;

import com.example.tsubuyaki.domain.Post;
import com.example.tsubuyaki.domain.PostLike;
import com.example.tsubuyaki.domain.Tag;
import com.example.tsubuyaki.domain.User;
import com.example.tsubuyaki.repository.PostLikeRepository;
import com.example.tsubuyaki.repository.PostRepository;
import com.example.tsubuyaki.repository.TagRepository;
import com.example.tsubuyaki.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("投稿取得_存在するidのとき_Repositoryの結果を返す")
    void 投稿取得_存在するidのとき_Repositoryの結果を返す() {
        Post post = new Post("alice", "hello", Instant.parse("2026-05-23T10:00:00Z"));
        given(postRepository.findWithTagsById(1L)).willReturn(Optional.of(post));

        Optional<Post> result = postService.findById(1L);

        assertThat(result).contains(post);
    }

    @Test
    @DisplayName("投稿検索_キーワードあり_本文検索Repositoryを呼ぶ")
    void 投稿検索_キーワードあり_本文検索Repositoryを呼ぶ() {
        List<Post> posts = List.of(new Post("alice", "xxx を含む投稿",
                Instant.parse("2026-05-23T10:00:00Z")));
        given(postRepository.findTop50ByParentIsNullAndBodyContainingOrderByCreatedAtDesc("xxx")).willReturn(posts);

        List<Post> result = postService.searchByBody("xxx");

        assertThat(result).isSameAs(posts);
        verify(postRepository).findTop50ByParentIsNullAndBodyContainingOrderByCreatedAtDesc("xxx");
        verify(postRepository, never()).findTop50ByParentIsNullOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("投稿検索_キーワードが空白のみ_最新50件を返す")
    void 投稿検索_キーワードが空白のみ_最新50件を返す() {
        List<Post> posts = List.of(new Post("alice", "最新投稿",
                Instant.parse("2026-05-23T10:00:00Z")));
        given(postRepository.findTop50ByParentIsNullOrderByCreatedAtDesc()).willReturn(posts);

        List<Post> result = postService.searchByBody("   ");

        assertThat(result).isSameAs(posts);
        verify(postRepository).findTop50ByParentIsNullOrderByCreatedAtDesc();
        verify(postRepository, never())
                .findTop50ByParentIsNullAndBodyContainingOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("投稿作成_投稿者と本文とアバター色を指定したとき_投稿を保存する")
    void 投稿作成_投稿者と本文とアバター色を指定したとき_投稿を保存する() {
        given(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.findByName("alice")).willReturn(Optional.empty());
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Post created = postService.create("alice", "こんにちは", "#ef4444");

        assertThat(created.getAuthor()).isEqualTo("alice");
        assertThat(created.getBody()).isEqualTo("こんにちは");
        assertThat(created.getAvatarColor()).isEqualTo("#ef4444");
        assertThat(created.getUser().getName()).isEqualTo("alice");
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("投稿作成_新規ユーザーでアバター色が空白のとき_デフォルト色で保存する")
    void 投稿作成_新規ユーザーでアバター色が空白のとき_デフォルト色で保存する() {
        given(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.findByName("alice")).willReturn(Optional.empty());
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Post created = postService.create("alice", "こんにちは", "   ");

        assertThat(created.getAvatarColor()).isEqualTo(User.DEFAULT_AVATAR_COLOR);
    }

    @Test
    @DisplayName("投稿作成_既存ユーザーでアバター色が空白のとき_既存色を維持する")
    void 投稿作成_既存ユーザーでアバター色が空白のとき_既存色を維持する() {
        User user = new User("alice", "#22c55e");
        given(userRepository.findByName("alice")).willReturn(Optional.of(user));
        given(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Post created = postService.create("alice", "こんにちは", "");

        assertThat(created.getUser()).isSameAs(user);
        assertThat(created.getAvatarColor()).isEqualTo("#22c55e");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    @DisplayName("投稿作成_本文にタグが存在しないとき_タグを保存しない")
    void 投稿作成_本文にタグが存在しないとき_タグを保存しない() {
        given(userRepository.findByName("alice")).willReturn(Optional.empty());
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Post created = postService.create("alice", "タグなしの投稿です", "#ef4444");

        assertThat(created.getTags()).isEmpty();
        verify(tagRepository, never()).findByName(org.mockito.ArgumentMatchers.anyString());
        verify(tagRepository, never()).save(org.mockito.ArgumentMatchers.any(Tag.class));
    }

    @Test
    @DisplayName("投稿作成_本文にタグがあるとき_既存タグを再利用し新規タグを保存する")
    void 投稿作成_本文にタグがあるとき_既存タグを再利用し新規タグを保存する() {
        Tag spring = new Tag("spring");
        given(userRepository.findByName("alice")).willReturn(Optional.empty());
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tagRepository.findByName("spring")).willReturn(Optional.of(spring));
        given(tagRepository.findByName("java")).willReturn(Optional.empty());
        given(tagRepository.save(org.mockito.ArgumentMatchers.any(Tag.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Post created = postService.create("alice", "#spring　#java #spring", "#ef4444");

        Set<String> tagNames = created.getTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());
        assertThat(tagNames).containsExactlyInAnyOrder("spring", "java");
        verify(tagRepository).save(org.mockito.ArgumentMatchers.argThat(tag -> "java".equals(tag.getName())));
        verify(tagRepository, never()).save(org.mockito.ArgumentMatchers.argThat(tag -> "spring".equals(tag.getName())));
    }

    @Test
    @DisplayName("タグ別投稿取得_タグ名を指定したとき_Repositoryの結果を返す")
    void タグ別投稿取得_タグ名を指定したとき_Repositoryの結果を返す() {
        List<Post> posts = List.of(new Post("alice", "#spring 投稿",
                Instant.parse("2026-05-23T10:00:00Z")));
        given(postRepository.findDistinctTop50ByParentIsNullAndTagsNameOrderByCreatedAtDesc("spring"))
                .willReturn(posts);

        List<Post> result = postService.findByTagName("spring");

        assertThat(result).isSameAs(posts);
    }

    @Test
    @DisplayName("リプライ作成_親投稿と本文を指定したとき_親に紐づく投稿として保存する")
    void リプライ作成_親投稿と本文を指定したとき_親に紐づく投稿として保存する() {
        Post parent = new Post("alice", "親投稿", Instant.parse("2026-05-23T10:00:00Z"));
        Tag spring = new Tag("spring");
        given(postRepository.getReferenceById(1L)).willReturn(parent);
        given(userRepository.findByName("bob")).willReturn(Optional.empty());
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tagRepository.findByName("spring")).willReturn(Optional.of(spring));
        given(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Post reply = postService.createReply(1L, "bob", "返信です #spring", "#22c55e");

        assertThat(reply.getParent()).isSameAs(parent);
        assertThat(reply.getAuthor()).isEqualTo("bob");
        assertThat(reply.getBody()).isEqualTo("返信です #spring");
        assertThat(reply.getAvatarColor()).isEqualTo("#22c55e");
        assertThat(reply.getTags()).containsExactly(spring);
    }

    @Test
    @DisplayName("リプライツリー取得_孫リプライがあるとき_親子構造といいね数を返す")
    void リプライツリー取得_孫リプライがあるとき_親子構造といいね数を返す() {
        Post parent = new Post("alice", "親投稿", Instant.parse("2026-05-23T10:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(parent, "id", 1L);
        Post firstReply = new Post(parent, "bob", "最初の返信", Instant.parse("2026-05-23T10:01:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(firstReply, "id", 2L);
        Post secondReply = new Post(parent, "carol", "次の返信", Instant.parse("2026-05-23T10:02:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(secondReply, "id", 3L);
        Post grandChild = new Post(firstReply, "dave", "返信への返信", Instant.parse("2026-05-23T10:03:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(grandChild, "id", 4L);
        given(postRepository.findByParentIdInOrderByCreatedAtAsc(List.of(1L)))
                .willReturn(List.of(firstReply, secondReply));
        given(postRepository.findByParentIdInOrderByCreatedAtAsc(List.of(2L, 3L)))
                .willReturn(List.of(grandChild));
        given(postRepository.findByParentIdInOrderByCreatedAtAsc(List.of(4L))).willReturn(List.of());
        given(postLikeRepository.countByPostId(2L)).willReturn(2L);
        given(postLikeRepository.countByPostId(3L)).willReturn(0L);
        given(postLikeRepository.countByPostId(4L)).willReturn(1L);

        List<ReplyNode> tree = postService.findReplyTree(1L);

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).id()).isEqualTo(2L);
        assertThat(tree.get(0).body()).isEqualTo("最初の返信");
        assertThat(tree.get(0).likeCount()).isEqualTo(2L);
        assertThat(tree.get(0).children()).singleElement()
                .satisfies(node -> {
                    assertThat(node.id()).isEqualTo(4L);
                    assertThat(node.body()).isEqualTo("返信への返信");
                    assertThat(node.likeCount()).isEqualTo(1L);
                });
        assertThat(tree.get(1).id()).isEqualTo(3L);
        assertThat(tree.get(1).body()).isEqualTo("次の返信");
        assertThat(tree.get(1).children()).isEmpty();
    }

    @Test
    @DisplayName("ルート投稿id取得_リプライを指定したとき_最上位の投稿idを返す")
    void ルート投稿id取得_リプライを指定したとき_最上位の投稿idを返す() {
        given(postRepository.findParentIdById(4L)).willReturn(Optional.of(2L));
        given(postRepository.findParentIdById(2L)).willReturn(Optional.of(1L));
        given(postRepository.findParentIdById(1L)).willReturn(Optional.empty());

        Long rootId = postService.findRootId(4L);

        assertThat(rootId).isEqualTo(1L);
    }

    @Test
    @DisplayName("いいね切替_未いいねのとき_いいねを保存する")
    void いいね切替_未いいねのとき_いいねを保存する() {
        Post post = new Post("alice", "hello", Instant.parse("2026-05-23T10:00:00Z"));
        given(postLikeRepository.existsByPostIdAndClientHash(1L, "abcd1234")).willReturn(false);
        given(postRepository.getReferenceById(1L)).willReturn(post);

        postService.toggleLike(1L, "abcd1234");

        ArgumentCaptor<PostLike> captor = ArgumentCaptor.forClass(PostLike.class);
        verify(postLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getPost()).isSameAs(post);
        assertThat(captor.getValue().getClientHash()).isEqualTo("abcd1234");
        verify(postLikeRepository, never()).deleteByPostIdAndClientHash(1L, "abcd1234");
    }

    @Test
    @DisplayName("いいね切替_いいね済みのとき_いいねを削除する")
    void いいね切替_いいね済みのとき_いいねを削除する() {
        given(postLikeRepository.existsByPostIdAndClientHash(1L, "abcd1234")).willReturn(true);

        postService.toggleLike(1L, "abcd1234");

        verify(postLikeRepository).deleteByPostIdAndClientHash(1L, "abcd1234");
        verify(postRepository, never()).getReferenceById(1L);
        verify(postLikeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("いいね数取得_投稿idを指定したとき_Repositoryの件数を返す")
    void いいね数取得_投稿idを指定したとき_Repositoryの件数を返す() {
        given(postLikeRepository.countByPostId(1L)).willReturn(3L);

        long count = postService.countLikes(1L);

        assertThat(count).isEqualTo(3L);
    }
}
