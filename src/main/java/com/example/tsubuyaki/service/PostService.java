package com.example.tsubuyaki.service;

import com.example.tsubuyaki.domain.Post;
import com.example.tsubuyaki.domain.PostLike;
import com.example.tsubuyaki.domain.Tag;
import com.example.tsubuyaki.domain.TagParser;
import com.example.tsubuyaki.domain.User;
import com.example.tsubuyaki.repository.PostLikeRepository;
import com.example.tsubuyaki.repository.PostRepository;
import com.example.tsubuyaki.repository.TagRepository;
import com.example.tsubuyaki.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository repository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    public PostService(PostRepository repository, PostLikeRepository postLikeRepository,
            UserRepository userRepository, TagRepository tagRepository) {
        this.repository = repository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
    }

    /*
     * 一覧画面では会話の入口になる通常投稿だけを表示し、リプライは詳細画面のツリーに閉じ込める。
     */
    public List<Post> findLatest50() {
        return initializeTags(repository.findTop50ByParentIsNullOrderByCreatedAtDesc());
    }

    /*
     * 空検索は一覧表示と同じ意味にし、キーワード指定時も通常投稿だけを対象にして検索結果の粒度を揃える。
     */
    public List<Post> searchByBody(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findLatest50();
        }
        return initializeTags(repository.findTop50ByParentIsNullAndBodyContainingOrderByCreatedAtDesc(keyword));
    }

    /*
     * 詳細画面でタグと投稿者を即時表示できるよう、Repository 側の EntityGraph 付き取得を入口にする。
     */
    public Optional<Post> findById(Long id) {
        return repository.findWithTagsById(id);
    }

    /*
     * タグから辿る画面でも通常投稿だけを返し、リプライは元投稿の詳細ツリーで読む導線に統一する。
     */
    public List<Post> findByTagName(String name) {
        return initializeTags(repository.findDistinctTop50ByParentIsNullAndTagsNameOrderByCreatedAtDesc(name));
    }

    /*
     * 投稿とリプライのいいね数を同じ集計経路にし、表示側が対象種別を意識しなくて済むようにする。
     */
    public long countLikes(Long postId) {
        return postLikeRepository.countByPostId(postId);
    }

    /*
     * 取得した全子孫を親IDごとにまとめ、テンプレートがそのまま再帰描画できる表示用ツリーへ再構成する。
     */
    public List<ReplyNode> findReplyTree(Long postId) {
        List<Post> replies = findDescendantReplies(postId);
        Map<Long, List<Post>> repliesByParentId = replies.stream()
                .collect(Collectors.groupingBy(Post::getParentId));
        return buildReplyNodes(postId, repliesByParentId);
    }

    /*
     * リプライへの投稿・いいね操作後も会話全体を見失わないよう、常に最上位投稿の詳細へ戻す。
     */
    public Long findRootId(Long postId) {
        Long currentId = postId;
        Optional<Long> parentId = repository.findParentIdById(currentId);
        while (parentId.isPresent()) {
            currentId = parentId.orElseThrow();
            parentId = repository.findParentIdById(currentId);
        }
        return currentId;
    }

    @Transactional
    /*
     * 既定色に任せる投稿作成経路を残し、既存のテスト・呼び出し元が色指定を意識しないで済むようにする。
     */
    public Post create(String author, String body) {
        return create(author, body, "");
    }

    @Transactional
    /*
     * 通常投稿は parent を持たない Post として保存し、リプライと同じ作成処理を共有する。
     */
    public Post create(String author, String body, String avatarColor) {
        return createPost(null, author, body, avatarColor);
    }

    @Transactional
    /*
     * リプライも投稿と同じ本文・投稿者・色・タグ抽出を使い、親投稿参照だけを追加して保存する。
     */
    public Post createReply(Long parentId, String author, String body, String avatarColor) {
        return createPost(repository.getReferenceById(parentId), author, body, avatarColor);
    }

    /*
     * 投稿者とタグの再利用を一箇所に集約し、通常投稿とリプライで作成時の振る舞いを揃える。
     */
    private Post createPost(Post parent, String author, String body, String avatarColor) {
        User user = userRepository.findByName(author)
                .map(existing -> {
                    existing.updateAvatarColor(avatarColor);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(new User(author, avatarColor)));
        Post post = new Post(parent, user, body, Instant.now());
        TagParser.extractNames(body).stream()
                .map(this::findOrCreateTag)
                .forEach(post::addTag);
        return repository.save(post);
    }

    @Transactional
    /*
     * 同じ利用者識別子の再クリックを取り消しとして扱い、投稿とリプライで同じトグル仕様を使う。
     */
    public void toggleLike(Long postId, String clientHash) {
        if (postLikeRepository.existsByPostIdAndClientHash(postId, clientHash)) {
            postLikeRepository.deleteByPostIdAndClientHash(postId, clientHash);
            return;
        }

        postLikeRepository.save(new PostLike(repository.getReferenceById(postId), clientHash, Instant.now()));
    }

    /*
     * 同名タグを再利用し、投稿ごとにタグ行を増やさず検索・一覧で同じタグ概念を共有する。
     */
    private Tag findOrCreateTag(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> tagRepository.save(new Tag(name)));
    }

    /*
     * 階層の深さに制限を設けず、現在階層の子をまとめて取得して次階層へ進む。
     */
    private List<Post> findDescendantReplies(Long postId) {
        List<Post> replies = new ArrayList<>();
        List<Long> parentIds = List.of(postId);
        while (!parentIds.isEmpty()) {
            List<Post> children = repository.findByParentIdInOrderByCreatedAtAsc(parentIds);
            replies.addAll(children);
            parentIds = children.stream()
                    .map(Post::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return replies;
    }

    /*
     * JPA Entity をテンプレートへ直接再帰参照させず、表示に必要な値だけを ReplyNode に詰め替える。
     */
    private List<ReplyNode> buildReplyNodes(Long parentId, Map<Long, List<Post>> repliesByParentId) {
        return repliesByParentId.getOrDefault(parentId, List.of()).stream()
                .map(reply -> ReplyNode.from(reply, countLikes(reply.getId()),
                        buildReplyNodes(reply.getId(), repliesByParentId)))
                .toList();
    }

    /*
     * 一覧系クエリの結果に対してタグを先に初期化し、View 描画時の遅延ロード依存を避ける。
     */
    private List<Post> initializeTags(List<Post> posts) {
        List<Long> ids = posts.stream()
                .map(Post::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!ids.isEmpty()) {
            repository.findAllWithTagsByIdIn(ids);
        }
        return posts;
    }
}
