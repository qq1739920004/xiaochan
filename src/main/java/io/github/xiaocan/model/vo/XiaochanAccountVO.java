package io.github.xiaocan.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class XiaochanAccountVO {
    private Integer id;
    private String accountName;
    private Long silkId;
    @JsonProperty("xVayne")
    private Long xVayne;
    @JsonProperty("xSivirMasked")
    private String xSivirMasked;
    private Boolean enabled;
    private Long upstreamUserId;
    private String nickname;
    private String phoneMasked;
    private String vipLevel;
    private Integer cardTotal;
    private Integer cardActive;
    private Integer cardExpired;
    private Integer redpackTotal;
    private Integer meituanRedpackTotal;
    private Integer elemeRedpackTotal;
    private Integer platformRedpackTotal;
    private String refreshStatus;
    private String lastRefreshError;
    private LocalDateTime lastRefreshTime;
}
