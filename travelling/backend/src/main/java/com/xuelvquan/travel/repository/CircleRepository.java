package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Circle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CircleRepository extends JpaRepository<Circle, Long> {

    @Query("select c from Circle c order by c.memberCount desc, c.id desc")
    List<Circle> findAllOrdered();
}
