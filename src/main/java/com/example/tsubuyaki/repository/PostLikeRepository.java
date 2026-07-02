package com.example.tsubuyaki.repository;

import com.example.tsubuyaki.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    /*
     * 同じ利用者識別子からの再クリックを取り消し判定するため、投稿 ID と識別子で存在確認する。
     */
    boolean existsByPostIdAndClientHash(Long postId, String clientHash);

    /*
     * 投稿とリプライを同じ PostLike 集計対象にし、表示側が種別を意識せず件数を出せるようにする。
     */
    long countByPostId(Long postId);

    /*
     * いいね取り消し時に、存在確認と同じキーで対象行だけを削除する。
     */
    void deleteByPostIdAndClientHash(Long postId, String clientHash);
}
