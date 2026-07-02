package com.example.tsubuyaki.controller;

import com.example.tsubuyaki.domain.Post;
import com.example.tsubuyaki.service.PostService;
import com.example.tsubuyaki.web.dto.PostForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Controller
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping({ "/", "/posts" })
    public String list(@RequestParam(required = false) String q, Model model) {
        String keyword = q == null ? "" : q;
        model.addAttribute("posts", keyword.isBlank()
                ? postService.findLatest50()
                : postService.searchByBody(keyword));
        model.addAttribute("q", keyword);
        return "posts/list";
    }

    @GetMapping("/posts/new")
    public String newForm(Model model) {
        model.addAttribute("postForm", new PostForm());
        return "posts/form";
    }

    @GetMapping("/posts/{id}")
    public String detail(@PathVariable Long id, Model model) {
        populateDetailModel(id, findPostOrThrow(id), model, new PostForm());
        return "posts/detail";
    }

    @PostMapping("/posts/{id}/likes")
    public String toggleLike(@PathVariable Long id, HttpServletRequest request) {
        findPostOrThrow(id);
        postService.toggleLike(id, clientHash(request));
        // リプライにいいねした場合も、ユーザーの閲覧文脈を保つためルート投稿詳細へ戻す。
        return "redirect:/posts/" + postService.findRootId(id);
    }

    @PostMapping("/posts/{id}/replies")
    public String createReply(@PathVariable Long id,
            @Valid @ModelAttribute("replyForm") PostForm replyForm,
            BindingResult bindingResult,
            Model model) {
        Post parent = findPostOrThrow(id);
        Long rootId = postService.findRootId(id);
        if (bindingResult.hasErrors()) {
            // リプライへの返信で入力エラーが出ても、ツリー全体の文脈を保つためルート投稿を再表示する。
            Post root = id.equals(rootId) ? parent : findPostOrThrow(rootId);
            populateDetailModel(rootId, root, model, replyForm, id);
            return "posts/detail";
        }

        postService.createReply(id, replyForm.getAuthor(), replyForm.getBody(), replyForm.getAvatarColor());
        return "redirect:/posts/" + rootId;
    }

    @PostMapping("/posts")
    public String create(@Valid PostForm postForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "posts/form";
        }

        postService.create(postForm.getAuthor(), postForm.getBody(), postForm.getAvatarColor());
        return "redirect:/posts";
    }

    private Post findPostOrThrow(Long id) {
        return postService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void populateDetailModel(Long id, Post post, Model model, PostForm replyForm) {
        populateDetailModel(id, post, model, replyForm, null);
    }

    private void populateDetailModel(Long id, Post post, Model model, PostForm replyForm, Long replyTargetId) {
        model.addAttribute("post", post);
        model.addAttribute("likeCount", postService.countLikes(id));
        model.addAttribute("replyForm", replyForm);
        // 複数の返信フォームのうち、バリデーションエラーを表示する対象フォームを特定する。
        model.addAttribute("replyTargetId", replyTargetId);
        model.addAttribute("replyTree", postService.findReplyTree(id));
    }

    private String clientHash(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        // ログイン機能がないため、IP と User-Agent の組み合わせを疑似的な利用者識別に使う。
        String source = request.getRemoteAddr() + (userAgent == null ? "" : userAgent);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
