package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.domain.OutboundRecord;
import com.salt.field.domain.SaltPlatform;
import com.salt.field.dto.OutboundRequest;
import com.salt.field.dto.PlatformStockStat;
import com.salt.field.dto.PlatformVO;
import com.salt.field.repository.OutboundRecordRepository;
import com.salt.field.repository.SaltPlatformRepository;

@Service
public class PlatformService {

    private final SaltPlatformRepository platformRepository;
    private final OutboundRecordRepository outboundRepository;
    private final FieldLogService fieldLogService;

    public PlatformService(SaltPlatformRepository platformRepository,
                           OutboundRecordRepository outboundRepository,
                           FieldLogService fieldLogService) {
        this.platformRepository = platformRepository;
        this.outboundRepository = outboundRepository;
        this.fieldLogService = fieldLogService;
    }

    public List<PlatformVO> list() {
        Map<Long, BigDecimal> outMap = new LinkedHashMap<>();
        for (PlatformStockStat s : platformRepository.statStockAndOutbound()) {
            outMap.put(s.platformId(), s.outTons() == null ? BigDecimal.ZERO : s.outTons());
        }
        List<PlatformVO> list = new ArrayList<>();
        platformRepository.findAll().forEach(p -> list.add(toVO(p, outMap.get(p.getId()))));
        list.sort((a, b) -> a.getPlatformNo().compareTo(b.getPlatformNo()));
        return list;
    }

    public List<PlatformStockStat> stockStats() {
        return platformRepository.statStockAndOutbound();
    }

    public List<OutboundRecord> outbounds(Long platformId) {
        return outboundRepository.findByPlatformIdOrderByOutDateDesc(platformId);
    }

    /**
     * 坨台出仓。规则：出仓量不得超过库存。
     */
    @Transactional
    public PlatformVO outbound(Long platformId, OutboundRequest request) {
        SaltPlatform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new BizException("坨台不存在：id=" + platformId));
        BigDecimal stock = platform.getStockTons() == null ? BigDecimal.ZERO : platform.getStockTons();
        BigDecimal tons = request.getTons();
        if (tons == null || tons.signum() <= 0) {
            throw new BizException("出仓吨数必须大于 0");
        }
        if (tons.compareTo(stock) > 0) {
            throw new BizException("出仓量不得超过库存：坨台 " + platform.getPlatformNo() + " 现有库存 "
                    + stock.stripTrailingZeros().toPlainString() + " 吨，本次出仓 "
                    + tons.stripTrailingZeros().toPlainString() + " 吨");
        }
        platform.setStockTons(stock.subtract(tons));
        platformRepository.save(platform);

        OutboundRecord record = new OutboundRecord();
        record.setPlatformId(platform.getId());
        record.setCustomer(request.getCustomer());
        record.setTons(tons);
        record.setPlateNo(request.getPlateNo());
        record.setOutDate(LocalDate.now());
        record.setOperator(request.getOperator() == null ? "司磅员" : request.getOperator());
        outboundRepository.save(record);

        // @Any 多态关联指向「坨台」
        fieldLogService.write("OUTBOUND", platform,
                "出仓 " + tons.stripTrailingZeros().toPlainString() + " 吨 → " + request.getCustomer()
                        + "（" + request.getPlateNo() + "）",
                record.getOperator());
        return toVO(platform, sumOutbound(platformId));
    }

    private BigDecimal sumOutbound(Long platformId) {
        return outboundRepository.findByPlatformIdOrderByOutDateDesc(platformId).stream()
                .map(OutboundRecord::getTons)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PlatformVO toVO(SaltPlatform p, BigDecimal outTons) {
        return PlatformVO.of(p.getId(), p.getPlatformNo(), p.getName(), p.getSaltType(),
                p.getStockTons(), p.getCapacityTons(), p.getMoisture(), p.getPileShape(), p.getLocation(), outTons);
    }
}
