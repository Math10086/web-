package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    @Query("""
            select r from Route r
            where (:city is null or r.city = :city)
              and (:mode is null or r.mode = :mode)
              and (:authorId is null or r.authorId = :authorId)
            order by r.createdAt desc
            """)
    List<Route> search(@Param("city") String city,
                       @Param("mode") String mode,
                       @Param("authorId") Long authorId);
}
