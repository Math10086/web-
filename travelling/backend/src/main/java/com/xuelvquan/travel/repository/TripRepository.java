package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByCaptainIdOrderByCreatedAtDesc(Long captainId);

    List<Trip> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 结伴列表检索：目的地/状态/按学校筛选（学校取队长所属学校，null表示三校全部）
     */
    @Query("""
            select t from Trip t, User u
            where t.captainId = u.id
              and (:destination is null or t.destination like concat('%', :destination, '%'))
              and (:status is null or t.status = :status)
              and (:school is null or u.school = :school)
            order by t.createdAt desc
            """)
    List<Trip> search(@Param("destination") String destination,
                      @Param("status") String status,
                      @Param("school") String school);
}
