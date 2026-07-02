package com.example.tsubuyaki.repository;

import com.example.tsubuyaki.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /*
     * 同名タグを再利用し、投稿ごとにタグ行が増え続けないようにする。
     */
    Optional<Tag> findByName(String name);
}
