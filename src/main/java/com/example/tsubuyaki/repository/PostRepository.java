package com.example.tsubuyaki.repository;

import com.example.tsubuyaki.domain.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /*
     * 一覧ではリプライを混ぜず、会話の入口になる通常投稿だけを新着順カードとして表示する。
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findTop50ByParentIsNullOrderByCreatedAtDesc();

    /*
     * 既存の全投稿取得経路を残し、移行中のテストや内部用途で親子を区別せず取得できるようにする。
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findTop50ByOrderByCreatedAtDesc();

    /*
     * 本文検索でも通常投稿だけを返し、リプライは元投稿詳細のツリー内で読ませる。
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findTop50ByParentIsNullAndBodyContainingOrderByCreatedAtDesc(String keyword);

    /*
     * タグ検索結果にリプライ単体を出さず、タグ付き会話の入口として親投稿だけを返す。
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findDistinctTop50ByParentIsNullAndTagsNameOrderByCreatedAtDesc(String name);

    /*
     * 詳細表示で投稿者・タグ・親投稿を参照するため、必要な関連をまとめて取得する。
     */
    @EntityGraph(attributePaths = { "parent", "user", "tags" })
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findWithTagsById(@Param("id") Long id);

    /*
     * 一覧取得後にタグをまとめて初期化し、View 描画時の遅延ロード依存を避ける。
     */
    @EntityGraph(attributePaths = "tags")
    @Query("SELECT DISTINCT p FROM Post p WHERE p.id IN :ids")
    List<Post> findAllWithTagsByIdIn(@Param("ids") Collection<Long> ids);

    /*
     * ツリー構築で現在階層の子を一括取得し、同階層内では作成日時の古い順に表示する。
     */
    @EntityGraph(attributePaths = { "parent", "user", "tags" })
    @Query("SELECT p FROM Post p WHERE p.parent.id IN :parentIds ORDER BY p.createdAt ASC")
    List<Post> findByParentIdInOrderByCreatedAtAsc(@Param("parentIds") Collection<Long> parentIds);

    /*
     * リプライ操作後の戻り先を決めるため、Post 全体を読み込まず親 ID だけを取得する。
     */
    @Query("SELECT p.parent.id FROM Post p WHERE p.id = :id")
    Optional<Long> findParentIdById(@Param("id") Long id);
}
