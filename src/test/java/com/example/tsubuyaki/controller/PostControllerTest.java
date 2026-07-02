package com.example.tsubuyaki.controller;

import com.example.tsubuyaki.config.SecurityConfig;
import com.example.tsubuyaki.domain.Post;
import com.example.tsubuyaki.domain.Tag;
import com.example.tsubuyaki.domain.User;
import com.example.tsubuyaki.service.PostService;
import com.example.tsubuyaki.service.ReplyNode;
import com.example.tsubuyaki.web.dto.PostForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("投稿一覧_投稿があるとき_最新50件をビューに渡す")
    void 投稿一覧_投稿があるとき_最新50件をビューに渡す() throws Exception {
        Post latest = new Post("alice", "新しい投稿 #spring",
                Instant.parse("2026-05-23T10:00:00Z"), "#ef4444");
        latest.addTag(new Tag("spring"));
        List<Post> posts = List.of(latest,
                new Post("bob", "古い投稿", Instant.parse("2026-05-23T09:00:00Z"), "#6b7280"));
        given(postService.findLatest50()).willReturn(posts);

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/list"))
                .andExpect(model().attribute("posts", posts))
                .andExpect(model().attribute("q", ""))
                .andExpect(content().string(containsString("post__avatar")))
                .andExpect(content().string(containsString("--avatar-color: #ef4444")))
                .andExpect(content().string(matchesPattern("(?s).*<span class=\"post__avatar\""
                        + "\\s+style=\"--avatar-color: #ef4444\""
                        + "\\s+aria-hidden=\"true\"></span>\\s+"
                        + "<span class=\"post__author\">alice</span>.*")))
                .andExpect(content().string(containsString("href=\"/tags/spring\"")))
                .andExpect(content().string(containsString("#spring")));

        verify(postService).findLatest50();
    }

    @Test
    @DisplayName("投稿一覧_検索キーワードあり_検索結果とキーワードをビューに渡す")
    void 投稿一覧_検索キーワードあり_検索結果とキーワードをビューに渡す() throws Exception {
        List<Post> posts = List.of(
                new Post("alice", "xxx を含む投稿", Instant.parse("2026-05-23T10:00:00Z")));
        given(postService.searchByBody("xxx")).willReturn(posts);

        mockMvc.perform(get("/posts").param("q", "xxx"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/list"))
                .andExpect(model().attribute("posts", posts))
                .andExpect(model().attribute("q", "xxx"))
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString("value=\"xxx\"")));

        verify(postService).searchByBody("xxx");
        verify(postService, never()).findLatest50();
    }

    @Test
    @DisplayName("投稿作成画面_GET_posts_new_フォーム用モデルを渡す")
    void 投稿作成画面_GET_posts_new_フォーム用モデルを渡す() throws Exception {
        mockMvc.perform(get("/posts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().attribute("postForm", instanceOf(PostForm.class)))
                .andExpect(model().attribute("postForm",
                        hasProperty("avatarColor", is(User.DEFAULT_AVATAR_COLOR))))
                .andExpect(content().string(containsString("type=\"color\"")))
                .andExpect(content().string(containsString("name=\"avatarColor\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        verifyNoInteractions(postService);
    }

    @Test
    @DisplayName("投稿詳細_存在するidのとき_投稿をビューに渡す")
    void 投稿詳細_存在するidのとき_投稿をビューに渡す() throws Exception {
        Post post = new Post("alice", "詳細表示する投稿",
                Instant.parse("2026-05-23T10:00:00Z"), "#22c55e");
        org.springframework.test.util.ReflectionTestUtils.setField(post, "id", 1L);
        Post reply = new Post(post, "bob", "返信です", Instant.parse("2026-05-23T10:01:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(reply, "id", 2L);
        Post childReply = new Post(reply, "carol", "返信への返信です", Instant.parse("2026-05-23T10:02:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(childReply, "id", 3L);
        List<ReplyNode> replyTree = List.of(ReplyNode.from(reply, 1L,
                List.of(ReplyNode.from(childReply, 0L, List.of()))));
        given(postService.findById(1L)).willReturn(Optional.of(post));
        given(postService.countLikes(1L)).willReturn(2L);
        given(postService.findReplyTree(1L)).willReturn(replyTree);

        mockMvc.perform(get("/posts/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/detail"))
                .andExpect(model().attribute("post", post))
                .andExpect(model().attribute("likeCount", 2L))
                .andExpect(model().attribute("replyForm", instanceOf(PostForm.class)))
                .andExpect(model().attribute("replyTree", replyTree))
                .andExpect(content().string(containsString("post__avatar")))
                .andExpect(content().string(containsString("--avatar-color: #22c55e")))
                .andExpect(content().string(containsString("返信です")))
                .andExpect(content().string(containsString("返信への返信です")))
                .andExpect(content().string(containsString("reply-tree--nested")))
                .andExpect(content().string(containsString("action=\"/posts/1/replies\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(matchesPattern("(?s).*<span class=\"post__avatar\""
                        + "\\s+style=\"--avatar-color: #22c55e\""
                        + "\\s+aria-hidden=\"true\"></span>\\s+"
                        + "<span class=\"post__author\">alice</span>.*")));

        verify(postService).findById(1L);
        verify(postService).countLikes(1L);
        verify(postService).findReplyTree(1L);
    }

    @Test
    @DisplayName("投稿詳細_存在しないidのとき_404を返す")
    void 投稿詳細_存在しないidのとき_404を返す() throws Exception {
        given(postService.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/posts/{id}", 999L))
                .andExpect(status().isNotFound());

        verify(postService).findById(999L);
        verify(postService, never()).countLikes(999L);
    }

    @Test
    @DisplayName("リプライ登録_入力が正しいとき_親投稿に保存してルート詳細へリダイレクトする")
    void リプライ登録_入力が正しいとき_親投稿に保存してルート詳細へリダイレクトする() throws Exception {
        Post parent = new Post("alice", "親投稿", Instant.parse("2026-05-23T10:00:00Z"));
        given(postService.findById(1L)).willReturn(Optional.of(parent));
        given(postService.findRootId(1L)).willReturn(1L);

        mockMvc.perform(post("/posts/{id}/replies", 1L)
                        .with(csrf())
                        .param("author", "bob")
                        .param("body", "返信です")
                        .param("avatarColor", "#22c55e"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts/1"));

        verify(postService).findById(1L);
        verify(postService).createReply(1L, "bob", "返信です", "#22c55e");
        verify(postService).findRootId(1L);
    }

    @Test
    @DisplayName("リプライ登録_本文が空白のみのとき_詳細を再表示して保存しない")
    void リプライ登録_本文が空白のみのとき_詳細を再表示して保存しない() throws Exception {
        Post parent = new Post("alice", "親投稿", Instant.parse("2026-05-23T10:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(parent, "id", 1L);
        given(postService.findById(1L)).willReturn(Optional.of(parent));
        given(postService.countLikes(1L)).willReturn(0L);
        given(postService.findReplyTree(1L)).willReturn(List.of());
        given(postService.findRootId(1L)).willReturn(1L);

        mockMvc.perform(post("/posts/{id}/replies", 1L)
                        .with(csrf())
                        .param("author", "bob")
                        .param("body", "   ")
                        .param("avatarColor", "#22c55e"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/detail"))
                .andExpect(model().attribute("post", parent))
                .andExpect(model().attribute("replyTargetId", 1L))
                .andExpect(model().attributeHasFieldErrors("replyForm", "body"));

        verify(postService).findById(1L);
        verify(postService).findRootId(1L);
        verify(postService).countLikes(1L);
        verify(postService).findReplyTree(1L);
        verify(postService, never()).createReply(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("リプライ登録_リプライへの返信が不正なとき_ルート詳細で対象フォームのエラーを表示する")
    void リプライ登録_リプライへの返信が不正なとき_ルート詳細で対象フォームのエラーを表示する() throws Exception {
        Post root = new Post("alice", "親投稿", Instant.parse("2026-05-23T10:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(root, "id", 1L);
        Post reply = new Post(root, "bob", "リプライ", Instant.parse("2026-05-23T10:01:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(reply, "id", 2L);
        List<ReplyNode> replyTree = List.of(ReplyNode.from(reply, 0L, List.of()));
        given(postService.findById(2L)).willReturn(Optional.of(reply));
        given(postService.findRootId(2L)).willReturn(1L);
        given(postService.findById(1L)).willReturn(Optional.of(root));
        given(postService.countLikes(1L)).willReturn(0L);
        given(postService.findReplyTree(1L)).willReturn(replyTree);

        mockMvc.perform(post("/posts/{id}/replies", 2L)
                        .with(csrf())
                        .param("author", "carol")
                        .param("body", "   ")
                        .param("avatarColor", "#22c55e"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/detail"))
                .andExpect(model().attribute("post", root))
                .andExpect(model().attribute("replyTree", replyTree))
                .andExpect(model().attribute("replyTargetId", 2L))
                .andExpect(model().attributeHasFieldErrors("replyForm", "body"))
                .andExpect(content().string(containsString("id=\"reply-body-2\"")))
                .andExpect(content().string(containsString("本文を入力してください")));

        verify(postService).findById(2L);
        verify(postService).findRootId(2L);
        verify(postService).findById(1L);
        verify(postService).countLikes(1L);
        verify(postService).findReplyTree(1L);
        verify(postService, never()).createReply(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("いいね切替_存在する投稿のとき_clientHashを渡して詳細へリダイレクトする")
    void いいね切替_存在する投稿のとき_clientHashを渡して詳細へリダイレクトする() throws Exception {
        Post post = new Post("alice", "いいね対象", Instant.parse("2026-05-23T10:00:00Z"));
        given(postService.findById(1L)).willReturn(Optional.of(post));
        given(postService.findRootId(1L)).willReturn(1L);

        mockMvc.perform(post("/posts/{id}/likes", 1L)
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        })
                        .header("User-Agent", "JUnit-Agent"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts/1"));

        verify(postService).findById(1L);
        verify(postService).toggleLike(1L, "c68f6c0d");
        verify(postService).findRootId(1L);
    }

    @Test
    @DisplayName("いいね切替_リプライのとき_リプライidで切替してルート詳細へリダイレクトする")
    void いいね切替_リプライのとき_リプライidで切替してルート詳細へリダイレクトする() throws Exception {
        Post parent = new Post("alice", "親投稿", Instant.parse("2026-05-23T10:00:00Z"));
        Post reply = new Post(parent, "bob", "リプライ", Instant.parse("2026-05-23T10:01:00Z"));
        given(postService.findById(2L)).willReturn(Optional.of(reply));
        given(postService.findRootId(2L)).willReturn(1L);

        mockMvc.perform(post("/posts/{id}/likes", 2L)
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        })
                        .header("User-Agent", "JUnit-Agent"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts/1"));

        verify(postService).findById(2L);
        verify(postService).toggleLike(2L, "c68f6c0d");
        verify(postService).findRootId(2L);
    }

    @Test
    @DisplayName("いいね切替_存在しない投稿のとき_404を返し更新しない")
    void いいね切替_存在しない投稿のとき_404を返し更新しない() throws Exception {
        given(postService.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(post("/posts/{id}/likes", 999L)
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        })
                        .header("User-Agent", "JUnit-Agent"))
                .andExpect(status().isNotFound());

        verify(postService).findById(999L);
        verify(postService, never()).toggleLike(999L, "c68f6c0d");
    }

    @Test
    @DisplayName("投稿登録_入力が正しいとき_投稿を保存して一覧へリダイレクトする")
    void 投稿登録_入力が正しいとき_投稿を保存して一覧へリダイレクトする() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(csrf())
                        .param("author", "alice")
                        .param("body", "こんにちは")
                        .param("avatarColor", "#ef4444"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));

        verify(postService).create("alice", "こんにちは", "#ef4444");
    }

    @Test
    @DisplayName("投稿登録_CSRFトークンなし_403を返し保存しない")
    void 投稿登録_CSRFトークンなし_403を返し保存しない() throws Exception {
        mockMvc.perform(post("/posts")
                        .param("author", "alice")
                        .param("body", "こんにちは")
                        .param("avatarColor", "#ef4444"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postService);
    }

    @Test
    @DisplayName("投稿登録_アバター色が空のとき_投稿を保存して一覧へリダイレクトする")
    void 投稿登録_アバター色が空のとき_投稿を保存して一覧へリダイレクトする() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(csrf())
                        .param("author", "alice")
                        .param("body", "こんにちは")
                        .param("avatarColor", ""))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/posts"));

        verify(postService).create("alice", "こんにちは", "");
    }

    @Test
    @DisplayName("投稿登録_本文が空白のみのとき_フォームを再表示して保存しない")
    void 投稿登録_本文が空白のみのとき_フォームを再表示して保存しない() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(csrf())
                        .param("author", "alice")
                        .param("body", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().attributeHasFieldErrors("postForm", "body"));

        verifyNoInteractions(postService);
    }

    @Test
    @DisplayName("投稿登録_投稿者名が空白のみのとき_フォームを再表示して保存しない")
    void 投稿登録_投稿者名が空白のみのとき_フォームを再表示して保存しない() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(csrf())
                        .param("author", "   ")
                        .param("body", "こんにちは")
                        .param("avatarColor", "#ef4444"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().attributeHasFieldErrors("postForm", "author"));

        verifyNoInteractions(postService);
    }

    @Test
    @DisplayName("投稿登録_アバター色が不正なとき_フォームを再表示して保存しない")
    void 投稿登録_アバター色が不正なとき_フォームを再表示して保存しない() throws Exception {
        mockMvc.perform(post("/posts")
                        .with(csrf())
                        .param("author", "alice")
                        .param("body", "こんにちは")
                        .param("avatarColor", "red"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().attributeHasFieldErrors("postForm", "avatarColor"));

        verifyNoInteractions(postService);
    }
}
