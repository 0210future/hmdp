-- Minimal non-breaking index changes for microservice migration

ALTER TABLE tb_voucher_order ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
ALTER TABLE tb_follow ADD UNIQUE KEY uk_user_follow (user_id, follow_user_id);

ALTER TABLE tb_follow ADD KEY idx_follow_user (follow_user_id);
ALTER TABLE tb_blog ADD KEY idx_blog_user_ct (user_id, create_time);
ALTER TABLE tb_blog ADD KEY idx_blog_liked (liked);
ALTER TABLE tb_blog_comments ADD KEY idx_comment_blog_ct (blog_id, create_time);
ALTER TABLE tb_shop ADD KEY idx_shop_type (type_id);