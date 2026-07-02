package com.example.tsubuyaki.service;

import com.example.tsubuyaki.domain.Post;

import java.time.Instant;
import java.util.List;

public record ReplyNode(Long id, String author, String body, Instant createdAt, String avatarColor,
        List<String> tagNames, long likeCount, List<ReplyNode> children) {

    /*
     * テンプレート表示中に外部からリスト内容が変わらないよう、防御的コピーを保持する。
     */
    public ReplyNode {
        tagNames = List.copyOf(tagNames);
        children = List.copyOf(children);
    }

    /*
     * JPA Entity から表示に必要な値だけを取り出し、再帰描画用のノードへ変換する。
     */
    public static ReplyNode from(Post post, long likeCount, List<ReplyNode> children) {
        List<String> tagNames = post.getTags().stream()
                .map(com.example.tsubuyaki.domain.Tag::getName)
                .toList();
        return new ReplyNode(post.getId(), post.getAuthor(), post.getBody(), post.getCreatedAt(),
                post.getAvatarColor(), tagNames, likeCount, children);
    }

    /*
     * record のアクセサでもコピーを返し、呼び出し側から内部リストを変更できない契約を保つ。
     */
    @Override
    public List<String> tagNames() {
        return List.copyOf(tagNames);
    }

    /*
     * 子ノード一覧も immutable な値として扱い、ツリー構造の表示中変更を防ぐ。
     */
    @Override
    public List<ReplyNode> children() {
        return List.copyOf(children);
    }
}
