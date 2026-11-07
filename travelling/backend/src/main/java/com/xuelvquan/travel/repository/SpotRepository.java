package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    List<Spot> findByCity(String city);

    List<Spot> findByCategory(String category);

    @Query("""
            select s from Spot s
            where (:category is null or s.category = :category)
              and (:city is null or s.city = :city)
              and (:kw is null or lower(s.name) like lower(concat('%', :kw, '%'))
                   or lower(s.description) like lower(concat('%', :kw, '%')))
            order by s.heat desc
            """)
    List<Spot> search(@Param("category") String category,
                      @Param("city") String city,
                      @Param("kw") String keyword);

    @Query("select distinct s.city from Spot s order by s.city")
    List<String> findCities();
}
