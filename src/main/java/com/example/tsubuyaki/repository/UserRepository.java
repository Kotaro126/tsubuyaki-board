package com.example.tsubuyaki.repository;

import com.example.tsubuyaki.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * 投稿者名を同一ユーザーのキーとして扱い、アバター色を投稿間で共有する。
     */
    Optional<User> findByName(String name);
}
