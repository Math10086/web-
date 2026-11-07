package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where (:kw is null or lower(p.name) like lower(concat('%', :kw, '%'))
                   or lower(p.description) like lower(concat('%', :kw, '%')))
              and (:category is null or p.category = :category)
              and (:merchantId is null or p.merchantId = :merchantId)
            order by p.sales desc
            """)
    List<Product> search(@Param("kw") String keyword,
                         @Param("category") String category,
                         @Param("merchantId") Long merchantId);
}
