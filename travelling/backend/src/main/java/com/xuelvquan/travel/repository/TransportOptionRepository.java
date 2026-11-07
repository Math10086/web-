package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.TransportOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransportOptionRepository extends JpaRepository<TransportOption, Long> {

    List<TransportOption> findByDestination(String destination);

    List<TransportOption> findByOrigin(String origin);

    List<TransportOption> findByOriginAndDestination(String origin, String destination);

    @Query("select distinct t.origin from TransportOption t where t.origin is not null and t.origin <> ''")
    List<String> findDistinctOrigins();

    @Query("select distinct t.destination from TransportOption t where t.destination is not null and t.destination <> ''")
    List<String> findDistinctDestinations();
}
