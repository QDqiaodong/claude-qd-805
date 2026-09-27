package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.salt.field.domain.SaltPlatform;
import com.salt.field.dto.PlatformStockStat;

public interface SaltPlatformRepository extends JpaRepository<SaltPlatform, Long> {

    /**
     * 手写 JPQL ⑥：坨台库存与发运汇总（左连接出仓记录）
     */
    @Query("select new com.salt.field.dto.PlatformStockStat(p.id, p.platformNo, p.name, p.saltType, p.stockTons, "
            + "p.capacityTons, sum(o.tons), count(o)) "
            + "from SaltPlatform p left join OutboundRecord o on o.platformId = p.id "
            + "group by p.id, p.platformNo, p.name, p.saltType, p.stockTons, p.capacityTons "
            + "order by p.platformNo asc")
    List<PlatformStockStat> statStockAndOutbound();

    @Query("select coalesce(sum(p.stockTons), 0) from SaltPlatform p")
    java.math.BigDecimal sumStock();
}
