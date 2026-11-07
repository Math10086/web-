package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Post> findByStatusOrderByCreatedAtDesc(String status);

    List<Post> findByCircleId(Long circleId);

    long countByAuthorIdAndStatus(Long authorId, String status);

    long countByCreatedAtAfter(LocalDateTime time);

    @Query("""
            select p from Post p
            where (:status is null or p.status = :status)
              and (:type is null or p.type = :type)
              and (:circleId is null or p.circleId = :circleId)
              and (:city is null or p.city = :city)
              and (:authorId is null or p.authorId = :authorId)
              and (:kw is null or lower(p.title) like lower(concat('%', :kw, '%'))
                   or lower(p.content) like lower(concat('%', :kw, '%'))
                   or lower(p.destination) like lower(concat('%', :kw, '%')))
            order by p.createdAt desc
            """)
    List<Post> search(@Param("status") String status,
                      @Param("type") String type,
                      @Param("circleId") Long circleId,
                      @Param("city") String city,
                      @Param("authorId") Long authorId,
                      @Param("kw") String keyword);

    @Query("""
            select p from Post p
            where (:status is null or p.status = :status)
              and (:type is null or p.type = :type)
              and (:circleId is null or p.circleId = :circleId)
              and (:city is null or p.city = :city)
              and (:authorId is null or p.authorId = :authorId)
              and (:kw is null or lower(p.title) like lower(concat('%', :kw, '%'))
                   or lower(p.content) like lower(concat('%', :kw, '%'))
                   or lower(p.destination) like lower(concat('%', :kw, '%')))
            order by (p.viewCount + p.likeCount * 2 + p.favoriteCount * 3 + p.citationCount * 5) desc,
                     p.createdAt desc
            """)
    List<Post> searchHot(@Param("status") String status,
                         @Param("type") String type,
                         @Param("circleId") Long circleId,
                         @Param("city") String city,
                         @Param("authorId") Long authorId,
                         @Param("kw") String keyword);
}
